package com.mordin.samathascope

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin

/** Length of each analysis window. Features are recomputed every second (hop) over the last window. */
internal const val ANALYSIS_WINDOW_SECONDS = 5

/**
 * Arrival-time slack before an unexplained stretch in a window counts as a signal gap. Bluetooth
 * delivers samples in bursts, so arrival spans jitter by up to a few hundred ms without any loss.
 */
internal const val ARRIVAL_JITTER_TOLERANCE_MS = 300L

data class EegFeatures(
  val windowStartMs: Long,
  val windowEndMs: Long,
  val pTheta: Float,
  val pAlpha: Float,
  val pBeta: Float,
  val pHf: Float,
  val p4To13: Float,
  val p4To30: Float,
  val logTheta: Float,
  val logAlpha: Float,
  val logBeta: Float,
  val logHf: Float,
  val relativeTheta: Float,
  val relativeAlpha: Float,
  val relativeBeta: Float,
  val relativeHf: Float,
  val tbr: Float,
  val tar: Float,
  val abr: Float,
  val thetaPeakHz: Float,
  val alphaPeakHz: Float,
  val spectralEntropy: Float,
  val emg: Float,
  val hfRatio: Float,
  val blinkRateHz: Float,
  val clipFraction: Float,
  /** Share of 1–70 Hz raw power at the stronger of the 50 Hz and 60 Hz mains lines. */
  val lineNoiseRatio: Float,
  /**
   * Milliseconds of missing signal in the window: samples the parser saw lost (corrupt or dropped
   * packets, replaced by placeholders) or, if larger, an arrival-time stretch the samples can't explain.
   * Bursty but complete delivery is not a gap.
   */
  val signalGapMs: Long,
  /** Mains frequency (50 or 60 Hz) currently detected from the raw spectrum. */
  val mainsHz: Int = 50,
)

/**
 * Turns the raw 512 Hz stream into one [EegFeatures] per second, each over the last
 * [ANALYSIS_WINDOW_SECONDS] seconds.
 *
 * Bands are half-open ([start, end)) so adjacent bands share no bin and miss none:
 * theta 4–8, alpha 8–13, beta 13–30, HF 20–40 Hz; theta + alpha + beta = 4–30 Hz.
 */
class EegProcessor(
  private val sampleRateHz: Int,
  private val windowSize: Int = sampleRateHz * ANALYSIS_WINDOW_SECONDS,
  private val hopSize: Int = sampleRateHz,
  rawPreviewSeconds: Int = 20,
  rawHistorySeconds: Int = 600,
  private val rawHistoryRateHz: Int = 32,
) {
  private val ring = IntArray(windowSize)
  private val lostRing = BooleanArray(windowSize)
  private val notched50Ring = FloatArray(windowSize)
  private val notched60Ring = FloatArray(windowSize)
  private val timeRing = LongArray(windowSize)
  private var ringPos = 0
  private var totalSamples = 0
  private var hopCounter = 0
  private var lastSample = 0
  private var notchEnabled = false

  // Both mains notches are streaming IIR biquads that always run, so toggling the notch or a change
  // of detected mains frequency needs no warm-up. Removing hum in the time domain matters for the
  // blink/transient detector, whose robust threshold is otherwise raised by the hum.
  private val notch50 = NotchBiquad(sampleRateHz = sampleRateHz, notchHz = 50.0, q = 10.0)
  private val notch60 = NotchBiquad(sampleRateHz = sampleRateHz, notchHz = 60.0, q = 10.0)

  // The mains frequency is a property of the room, not the session, so reset() keeps it.
  private var mainsHz = 50

  private val previewSize = (sampleRateHz * rawPreviewSeconds).coerceAtLeast(sampleRateHz)
  private val preview = IntArray(previewSize)
  private var previewPos = 0
  private val rawHistoryDecimation = (sampleRateHz / rawHistoryRateHz).coerceAtLeast(1)
  private val rawHistorySize = (rawHistorySeconds * rawHistoryRateHz).coerceAtLeast(rawHistoryRateHz * 10)
  private val rawHistory = IntArray(rawHistorySize)
  private var rawHistoryPos = 0
  private var rawHistoryCounter = 0
  private var rawHistoryCount = 0

  fun setNotchEnabled(enabled: Boolean) {
    notchEnabled = enabled
  }

  fun reset() {
    ring.fill(0)
    lostRing.fill(false)
    notched50Ring.fill(0f)
    notched60Ring.fill(0f)
    notch50.reset()
    notch60.reset()
    timeRing.fill(0L)
    ringPos = 0
    totalSamples = 0
    hopCounter = 0
    lastSample = 0
    preview.fill(0)
    previewPos = 0
    rawHistory.fill(0)
    rawHistoryPos = 0
    rawHistoryCounter = 0
    rawHistoryCount = 0
  }

  fun rawPreview(maxSamples: Int): IntArray {
    val count = maxSamples.coerceIn(1, previewSize)
    val out = IntArray(count)
    val start = (previewPos - count + previewSize) % previewSize
    for (i in 0 until count) {
      out[i] = preview[(start + i) % previewSize]
    }
    return out
  }

  fun rawHistory(maxSamples: Int, offsetSamples: Int = 0): IntArray {
    val count = maxSamples.coerceAtLeast(1).coerceAtMost(rawHistorySize)
    val safeOffset = offsetSamples.coerceAtLeast(0)
    val available = (rawHistoryCount - safeOffset).coerceAtLeast(0)
    if (available <= 0) return IntArray(0)
    val end = (rawHistoryPos - safeOffset).mod(rawHistorySize)
    val actualCount = count.coerceAtMost(available)
    val out = IntArray(actualCount)
    val start = (end - actualCount + rawHistorySize) % rawHistorySize
    for (i in 0 until actualCount) {
      out[i] = rawHistory[(start + i) % rawHistorySize]
    }
    return out
  }

  fun rawHistoryRateHz(): Int = rawHistoryRateHz

  fun maxRawHistoryOffsetSeconds(windowSeconds: Int): Int {
    val windowSamples = (windowSeconds * rawHistoryRateHz).coerceAtLeast(1)
    return ((rawHistoryCount - windowSamples).coerceAtLeast(0) / rawHistoryRateHz).coerceAtLeast(0)
  }

  /** Adds one received sample. [timestampMs] is its arrival time on a monotonic clock. */
  fun pushRaw(sample: Int, timestampMs: Long): EegFeatures? = push(sample, timestampMs, lost = false)

  /**
   * Inserts a placeholder (the last sample held) for a sample known to be lost, so the window keeps
   * real time and the loss is reported in [EegFeatures.signalGapMs].
   */
  fun pushLost(timestampMs: Long): EegFeatures? = push(lastSample, timestampMs, lost = true)

  private fun push(sample: Int, timestampMs: Long, lost: Boolean): EegFeatures? {
    lastSample = sample
    ring[ringPos] = sample
    lostRing[ringPos] = lost
    notched50Ring[ringPos] = notch50.process(sample.toDouble()).toFloat()
    notched60Ring[ringPos] = notch60.process(sample.toDouble()).toFloat()
    timeRing[ringPos] = timestampMs
    ringPos = (ringPos + 1) % ring.size
    totalSamples++

    preview[previewPos] = sample
    previewPos = (previewPos + 1) % previewSize
    rawHistoryCounter++
    if (rawHistoryCounter >= rawHistoryDecimation) {
      rawHistory[rawHistoryPos] = sample
      rawHistoryPos = (rawHistoryPos + 1) % rawHistorySize
      rawHistoryCount = (rawHistoryCount + 1).coerceAtMost(rawHistorySize)
      rawHistoryCounter = 0
    }

    hopCounter++
    if (totalSamples < windowSize) return null
    if (hopCounter < hopSize) return null
    hopCounter = 0
    return computeFeatures()
  }

  private fun windowCopy(source: FloatArray, start: Int): FloatArray =
    FloatArray(windowSize) { i -> source[(start + i) % windowSize] }

  /**
   * Line noise and clipping are always measured on the raw signal so quality checks stay
   * notch-independent; everything else uses the notched signal when the notch is enabled.
   */
  private fun computeFeatures(): EegFeatures {
    val eps = 1e-6f
    val start = ringPos // Oldest sample: the ring is full and ringPos is the next write slot.
    val rawWindow = FloatArray(windowSize) { i -> ring[(start + i) % windowSize].toFloat() }
    val timeWindow = LongArray(windowSize) { i -> timeRing[(start + i) % windowSize] }
    var lostCount = 0
    for (lost in lostRing) if (lost) lostCount++

    val detrendedRaw = detrend(rawWindow)
    val rawSpectrum = welchPsd(detrendedRaw)

    val power50 = bandPower(rawSpectrum, 50f - MAINS_HALF_WIDTH_HZ, 50f + MAINS_HALF_WIDTH_HZ)
    val power60 = bandPower(rawSpectrum, 60f - MAINS_HALF_WIDTH_HZ, 60f + MAINS_HALF_WIDTH_HZ)
    // Hysteresis: only switch when the other line clearly dominates.
    if (power60 > MAINS_SWITCH_RATIO * power50) mainsHz = 60
    else if (power50 > MAINS_SWITCH_RATIO * power60) mainsHz = 50
    val lineNoiseRatio = max(power50, power60) / (bandPower(rawSpectrum, 1f, 70f) + eps)

    val detrended = if (notchEnabled) {
      detrend(windowCopy(if (mainsHz == 60) notched60Ring else notched50Ring, start))
    } else {
      detrendedRaw
    }
    val filteredSpectrum = if (notchEnabled) welchPsd(detrended) else rawSpectrum
    val analysisSpectrum = applyAnalysisBandLimit(filteredSpectrum)

    val pTheta = bandPower(analysisSpectrum, 4f, 8f)
    val pAlpha = bandPower(analysisSpectrum, 8f, 13f)
    val pBeta = bandPower(analysisSpectrum, 13f, 30f)
    val pHf = bandPower(filteredSpectrum, 20f, 40f)
    val p4To13 = bandPower(analysisSpectrum, 4f, 13f)
    val p4To30 = bandPower(analysisSpectrum, 4f, 30f)
    val thetaPeakHz = peakFrequency(analysisSpectrum, 4f, 8f)
    val alphaPeakHz = peakFrequency(analysisSpectrum, 8f, 13f)
    val spectralEntropy = spectralEntropy(analysisSpectrum, 4f, 30f)
    val blinkRateHz = blinkRateHz(detrended)
    val clipFraction = clipFraction(rawWindow)
    val hfRatio = pHf / (p4To13 + pHf + eps)

    return EegFeatures(
      windowStartMs = timeWindow.first(),
      windowEndMs = timeWindow.last(),
      pTheta = pTheta,
      pAlpha = pAlpha,
      pBeta = pBeta,
      pHf = pHf,
      p4To13 = p4To13,
      p4To30 = p4To30,
      logTheta = ln(pTheta + eps),
      logAlpha = ln(pAlpha + eps),
      logBeta = ln(pBeta + eps),
      logHf = ln(pHf + eps),
      relativeTheta = pTheta / (p4To30 + eps),
      relativeAlpha = pAlpha / (p4To30 + eps),
      relativeBeta = pBeta / (p4To30 + eps),
      relativeHf = hfRatio,
      tbr = ln((pTheta + eps) / (pBeta + eps)),
      tar = ln((pTheta + eps) / (pAlpha + eps)),
      abr = ln((pAlpha + eps) / (pBeta + eps)),
      thetaPeakHz = thetaPeakHz,
      alphaPeakHz = alphaPeakHz,
      spectralEntropy = spectralEntropy,
      emg = ln((pHf + eps) / (p4To13 + eps)),
      hfRatio = hfRatio,
      blinkRateHz = blinkRateHz,
      clipFraction = clipFraction,
      lineNoiseRatio = lineNoiseRatio,
      signalGapMs = signalGapMs(timeWindow, lostCount),
      mainsHz = mainsHz,
    )
  }

  private fun signalGapMs(timeWindow: LongArray, lostCount: Int): Long {
    val lostMs = lostCount * 1000L / sampleRateHz
    val spanMs = timeWindow.last() - timeWindow.first()
    val expectedSpanMs = (windowSize - 1) * 1000L / sampleRateHz
    val unexplainedMs = (spanMs - expectedSpanMs - ARRIVAL_JITTER_TOLERANCE_MS).coerceAtLeast(0L)
    return max(lostMs, unexplainedMs)
  }

  private fun detrend(values: FloatArray): FloatArray {
    var sum = 0f
    for (value in values) sum += value
    val mean = sum / values.size.toFloat()
    return FloatArray(values.size) { index -> values[index] - mean }
  }

  private fun welchPsd(signal: FloatArray): FloatArray {
    // 2 s segments keep 0.5 Hz resolution; a 5 s window yields 4 half-overlapping segments.
    val segmentSize = (sampleRateHz * 2).coerceAtMost(signal.size)
    val step = (segmentSize / 2).coerceAtLeast(1)
    val segments = ((signal.size - segmentSize) / step) + 1
    val psd = FloatArray(segmentSize / 2 + 1)
    val window = hannWindow(segmentSize)
    var windowPower = 0f
    for (weight in window) {
      windowPower += weight * weight
    }

    for (segmentIndex in 0 until segments) {
      val start = segmentIndex * step
      val real = FloatArray(segmentSize)
      val imag = FloatArray(segmentSize)

      var segmentMean = 0f
      for (i in 0 until segmentSize) {
        segmentMean += signal[start + i]
      }
      segmentMean /= segmentSize.toFloat()

      for (i in 0 until segmentSize) {
        real[i] = (signal[start + i] - segmentMean) * window[i]
      }

      fftRadix2(real, imag)

      for (bin in psd.indices) {
        var power = (real[bin] * real[bin]) + (imag[bin] * imag[bin])
        if (bin != 0 && bin != segmentSize / 2) {
          power *= 2f
        }
        psd[bin] += power / (windowPower * sampleRateHz.toFloat())
      }
    }

    for (i in psd.indices) {
      psd[i] /= segments.toFloat()
    }
    return psd
  }

  private fun binWidthHz(psd: FloatArray): Float = sampleRateHz.toFloat() / ((psd.size - 1) * 2).toFloat()

  /** Bins whose centre frequency lies in [startHz, endHz). */
  private fun binRange(psd: FloatArray, startHz: Float, endHz: Float): IntRange {
    val df = binWidthHz(psd)
    val startBin = ceil(startHz / df - BIN_EPSILON).toInt().coerceIn(0, psd.lastIndex)
    val endBin = (ceil(endHz / df - BIN_EPSILON).toInt() - 1).coerceIn(startBin, psd.lastIndex)
    return startBin..endBin
  }

  private fun applyAnalysisBandLimit(psd: FloatArray): FloatArray {
    val df = binWidthHz(psd)
    return FloatArray(psd.size) { index ->
      val freq = index * df
      when {
        freq < 1f -> 0f
        freq > 35f -> 0f
        else -> psd[index]
      }
    }
  }

  private fun bandPower(psd: FloatArray, startHz: Float, endHz: Float): Float {
    if (endHz <= startHz) return 0f
    val df = binWidthHz(psd)
    var sum = 0f
    for (bin in binRange(psd, startHz, endHz)) {
      sum += psd[bin] * df
    }
    return sum
  }

  private fun peakFrequency(psd: FloatArray, startHz: Float, endHz: Float): Float {
    val df = binWidthHz(psd)
    val range = binRange(psd, startHz, endHz)
    var bestBin = range.first
    var bestPower = -1f
    for (bin in range) {
      val power = psd[bin]
      if (power > bestPower) {
        bestPower = power
        bestBin = bin
      }
    }
    return if (bestPower <= 0f) {
      (startHz + endHz) / 2f
    } else {
      bestBin * df
    }
  }

  private fun spectralEntropy(psd: FloatArray, startHz: Float, endHz: Float): Float {
    val range = binRange(psd, startHz, endHz)
    var total = 0f
    for (bin in range) {
      total += psd[bin]
    }
    if (total <= 0f) return 0f

    var entropy = 0f
    val count = max(2, range.last - range.first + 1)
    for (bin in range) {
      val p = psd[bin] / total
      if (p > 0f) {
        entropy -= p * ln(p)
      }
    }
    return (entropy / ln(count.toFloat())).coerceIn(0f, 1f)
  }

  private fun blinkRateHz(signal: FloatArray): Float {
    val magnitudes = FloatArray(signal.size) { index -> abs(signal[index]) }
    val median = median(magnitudes)
    val mad = median(FloatArray(magnitudes.size) { index -> abs(magnitudes[index] - median) })
    val scale = max(1f, 1.4826f * mad)
    val threshold = median + (6f * scale)
    val refractory = (sampleRateHz * 0.25f).toInt().coerceAtLeast(1)

    var count = 0
    var lastPeak = -refractory
    for (i in 1 until magnitudes.lastIndex) {
      if (i - lastPeak < refractory) continue
      val value = magnitudes[i]
      if (value >= threshold && value >= magnitudes[i - 1] && value > magnitudes[i + 1]) {
        count++
        lastPeak = i
      }
    }
    return count.toFloat() * sampleRateHz.toFloat() / signal.size.toFloat()
  }

  private fun clipFraction(rawWindow: FloatArray): Float {
    var clips = 0
    for (value in rawWindow) {
      if (abs(value) >= 1900f) {
        clips++
      }
    }
    return clips.toFloat() / rawWindow.size.toFloat()
  }

  private fun hannWindow(size: Int): FloatArray {
    if (size <= 1) return FloatArray(size) { 1f }
    return FloatArray(size) { index ->
      (0.5 - 0.5 * cos(2.0 * Math.PI * index / (size - 1))).toFloat()
    }
  }

  private fun median(values: FloatArray): Float {
    if (values.isEmpty()) return 0f
    val sorted = values.sortedArray()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) {
      (sorted[middle - 1] + sorted[middle]) / 2f
    } else {
      sorted[middle]
    }
  }

  private fun fftRadix2(re: FloatArray, im: FloatArray) {
    val n = re.size
    var j = 0
    for (i in 1 until n) {
      var bit = n shr 1
      while (j and bit != 0) {
        j = j xor bit
        bit = bit shr 1
      }
      j = j xor bit
      if (i < j) {
        val tr = re[i]
        re[i] = re[j]
        re[j] = tr
        val ti = im[i]
        im[i] = im[j]
        im[j] = ti
      }
    }

    var len = 2
    while (len <= n) {
      val angle = -2.0 * Math.PI / len
      val wlenRe = cos(angle).toFloat()
      val wlenIm = sin(angle).toFloat()
      var i = 0
      while (i < n) {
        var wRe = 1f
        var wIm = 0f
        for (k in 0 until (len / 2)) {
          val uRe = re[i + k]
          val uIm = im[i + k]
          val vRe = (re[i + k + (len / 2)] * wRe) - (im[i + k + (len / 2)] * wIm)
          val vIm = (re[i + k + (len / 2)] * wIm) + (im[i + k + (len / 2)] * wRe)

          re[i + k] = uRe + vRe
          im[i + k] = uIm + vIm
          re[i + k + (len / 2)] = uRe - vRe
          im[i + k + (len / 2)] = uIm - vIm

          val nextWRe = (wRe * wlenRe) - (wIm * wlenIm)
          val nextWIm = (wRe * wlenIm) + (wIm * wlenRe)
          wRe = nextWRe
          wIm = nextWIm
        }
        i += len
      }
      len = len shl 1
    }
  }

  private companion object {
    const val BIN_EPSILON = 1e-4f
    // ±0.75 Hz covers the mains bin and its Hann-window neighbours at 0.5 Hz resolution.
    const val MAINS_HALF_WIDTH_HZ = 0.75f
    const val MAINS_SWITCH_RATIO = 2f
  }
}

/** RBJ-cookbook IIR notch (direct form I). Passes signal unchanged if [notchHz] is at or above Nyquist. */
internal class NotchBiquad(sampleRateHz: Int, notchHz: Double, q: Double) {
  private val enabled = notchHz < sampleRateHz / 2.0
  private val b0: Double
  private val b1: Double
  private val b2: Double
  private val a1: Double
  private val a2: Double
  private var x1 = 0.0
  private var x2 = 0.0
  private var y1 = 0.0
  private var y2 = 0.0

  init {
    val w0 = 2.0 * Math.PI * notchHz / sampleRateHz
    val cosW0 = cos(w0)
    val alpha = sin(w0) / (2.0 * q)
    val a0 = 1.0 + alpha
    b0 = 1.0 / a0
    b1 = -2.0 * cosW0 / a0
    b2 = 1.0 / a0
    a1 = -2.0 * cosW0 / a0
    a2 = (1.0 - alpha) / a0
  }

  fun process(x: Double): Double {
    if (!enabled) return x
    val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
    x2 = x1
    x1 = x
    y2 = y1
    y1 = y
    return y
  }

  fun reset() {
    x1 = 0.0
    x2 = 0.0
    y1 = 0.0
    y2 = 0.0
  }
}
