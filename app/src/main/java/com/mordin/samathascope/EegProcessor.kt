package com.mordin.samathascope

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin

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
  val lineNoiseRatio: Float,
  val maxGapMs: Long,
)

class EegProcessor(
  private val sampleRateHz: Int,
  private val windowSize: Int = sampleRateHz * 8,
  private val hopSize: Int = sampleRateHz,
  rawPreviewSeconds: Int = 20,
  rawHistorySeconds: Int = 600,
  private val rawHistoryRateHz: Int = 32,
) {
  private val ring = IntArray(windowSize)
  private val timeRing = LongArray(windowSize)
  private var ringPos = 0
  private var totalSamples = 0
  private var hopCounter = 0
  private var notch50Enabled = false

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
    notch50Enabled = enabled
  }

  fun reset() {
    ring.fill(0)
    timeRing.fill(0L)
    ringPos = 0
    totalSamples = 0
    hopCounter = 0
    preview.fill(0)
    previewPos = 0
    rawHistory.fill(0)
    rawHistoryPos = 0
    rawHistoryCounter = 0
    rawHistoryCount = 0
  }

  fun rawPreview(maxSamples: Int): List<Int> {
    val count = maxSamples.coerceIn(1, previewSize)
    val out = IntArray(count)
    val start = (previewPos - count + previewSize) % previewSize
    for (i in 0 until count) {
      out[i] = preview[(start + i) % previewSize]
    }
    return out.toList()
  }

  fun rawHistory(maxSamples: Int, offsetSamples: Int = 0): List<Int> {
    val count = maxSamples.coerceAtLeast(1).coerceAtMost(rawHistorySize)
    val safeOffset = offsetSamples.coerceAtLeast(0)
    val available = (rawHistoryCount - safeOffset).coerceAtLeast(0)
    if (available <= 0) return emptyList()
    val end = (rawHistoryPos - safeOffset).mod(rawHistorySize)
    val actualCount = count.coerceAtMost(available)
    val out = IntArray(actualCount)
    val start = (end - actualCount + rawHistorySize) % rawHistorySize
    for (i in 0 until actualCount) {
      out[i] = rawHistory[(start + i) % rawHistorySize]
    }
    return out.toList()
  }

  fun rawHistoryRateHz(): Int = rawHistoryRateHz

  fun maxRawHistoryOffsetSeconds(windowSeconds: Int): Int {
    val windowSamples = (windowSeconds * rawHistoryRateHz).coerceAtLeast(1)
    return ((rawHistoryCount - windowSamples).coerceAtLeast(0) / rawHistoryRateHz).coerceAtLeast(0)
  }

  fun pushRaw(sample: Int, timestampMs: Long): EegFeatures? {
    ring[ringPos] = sample
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

    val rawWindow = FloatArray(windowSize)
    val timeWindow = LongArray(windowSize)
    val start = (ringPos - windowSize + ring.size) % ring.size
    for (i in 0 until windowSize) {
      val index = (start + i) % ring.size
      rawWindow[i] = ring[index].toFloat()
      timeWindow[i] = timeRing[index]
    }
    return computeFeatures(rawWindow, timeWindow)
  }

  private fun computeFeatures(rawWindow: FloatArray, timeWindow: LongArray): EegFeatures {
    val eps = 1e-6f
    val detrended = detrend(rawWindow)
    val rawSpectrum = welchPsd(detrended)
    val notchedSpectrum = applyOptionalNotch(rawSpectrum)
    val analysisSpectrum = applyAnalysisBandLimit(notchedSpectrum)

    val pTheta = bandPower(analysisSpectrum, 4f, 7f)
    val pAlpha = bandPower(analysisSpectrum, 8f, 12f)
    val pBeta = bandPower(analysisSpectrum, 13f, 30f)
    val pHf = bandPower(notchedSpectrum, 20f, 40f)
    val p4To13 = bandPower(analysisSpectrum, 4f, 13f)
    val p4To30 = bandPower(analysisSpectrum, 4f, 30f)
    val thetaPeakHz = peakFrequency(analysisSpectrum, 4f, 7f)
    val alphaPeakHz = peakFrequency(analysisSpectrum, 8f, 12f)
    val spectralEntropy = spectralEntropy(analysisSpectrum, 4f, 30f)
    val blinkRateHz = blinkRateHz(detrended)
    val clipFraction = clipFraction(rawWindow)
    val lineNoiseRatio = bandPower(notchedSpectrum, 49f, 51f) / (bandPower(rawSpectrum, 1f, 60f) + eps)
    val hfRatio = pHf / (p4To13 + pHf + eps)

    return EegFeatures(
      windowStartMs = timeWindow.firstOrNull() ?: 0L,
      windowEndMs = timeWindow.lastOrNull() ?: 0L,
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
      maxGapMs = maxGapMs(timeWindow),
    )
  }

  private fun detrend(values: FloatArray): FloatArray {
    var sum = 0f
    for (value in values) sum += value
    val mean = sum / values.size.toFloat()
    return FloatArray(values.size) { index -> values[index] - mean }
  }

  private fun welchPsd(signal: FloatArray): FloatArray {
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

  private fun applyOptionalNotch(psd: FloatArray): FloatArray {
    if (!notch50Enabled) return psd.copyOf()
    val df = sampleRateHz.toFloat() / ((psd.size - 1) * 2).toFloat()
    return FloatArray(psd.size) { index ->
      val freq = index * df
      if (freq in 49f..51f) 0f else psd[index]
    }
  }

  private fun applyAnalysisBandLimit(psd: FloatArray): FloatArray {
    val df = sampleRateHz.toFloat() / ((psd.size - 1) * 2).toFloat()
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
    val df = sampleRateHz.toFloat() / ((psd.size - 1) * 2).toFloat()
    val startBin = (startHz / df).toInt().coerceIn(0, psd.lastIndex)
    val endBin = (endHz / df).toInt().coerceIn(startBin, psd.lastIndex)
    var sum = 0f
    for (bin in startBin..endBin) {
      sum += psd[bin] * df
    }
    return sum
  }

  private fun peakFrequency(psd: FloatArray, startHz: Float, endHz: Float): Float {
    val df = sampleRateHz.toFloat() / ((psd.size - 1) * 2).toFloat()
    val startBin = (startHz / df).toInt().coerceIn(0, psd.lastIndex)
    val endBin = (endHz / df).toInt().coerceIn(startBin, psd.lastIndex)
    var bestBin = startBin
    var bestPower = -1f
    for (bin in startBin..endBin) {
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
    val df = sampleRateHz.toFloat() / ((psd.size - 1) * 2).toFloat()
    val startBin = (startHz / df).toInt().coerceIn(0, psd.lastIndex)
    val endBin = (endHz / df).toInt().coerceIn(startBin, psd.lastIndex)
    var total = 0f
    for (bin in startBin..endBin) {
      total += psd[bin]
    }
    if (total <= 0f) return 0f

    var entropy = 0f
    val count = max(1, endBin - startBin + 1)
    for (bin in startBin..endBin) {
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

  private fun maxGapMs(timeWindow: LongArray): Long {
    var maxGap = 0L
    for (i in 1 until timeWindow.size) {
      val previous = timeWindow[i - 1]
      val current = timeWindow[i]
      if (previous == 0L || current == 0L) continue
      maxGap = max(maxGap, current - previous)
    }
    return maxGap.coerceAtLeast(0L)
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
}
