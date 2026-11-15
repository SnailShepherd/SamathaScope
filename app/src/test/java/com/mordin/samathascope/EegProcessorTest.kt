package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Test

class EegProcessorTest {

  @Test
  fun pushRaw_usesFiveSecondWindowWithOneSecondHop() {
    val processor = EegProcessor(sampleRateHz = 512)
    var featuresCount = 0

    repeat(2_560) { index ->
      if (processor.pushRaw(0, index * 2L) != null) {
        featuresCount++
      }
    }
    repeat(511) { index ->
      if (processor.pushRaw(0, (2_560 + index) * 2L) != null) {
        featuresCount++
      }
    }
    val next = processor.pushRaw(0, 3_071 * 2L)
    if (next != null) {
      featuresCount++
    }

    assertThat(featuresCount).isEqualTo(2)
    assertThat(next).isNotNull()
  }

  @Test
  fun alphaSignal_hasAlphaPeakAndMoreAlphaPowerThanBeta() {
    val processor = EegProcessor(sampleRateHz = 512)
    val feature = pushWindow(processor) { sampleIndex ->
      sineSample(sampleIndex, 10.0, amplitude = 500.0)
    }

    assertThat(feature).isNotNull()
    assertThat(feature!!.alphaPeakHz).isWithin(0.6f).of(10f)
    assertThat(feature.pAlpha).isGreaterThan(feature.pBeta)
  }

  @Test
  fun spectralEntropy_isLowerForNarrowbandSignalThanMixedSignal() {
    val narrow = pushWindow(EegProcessor(sampleRateHz = 512)) { sampleIndex ->
      sineSample(sampleIndex, 10.0, amplitude = 500.0)
    }
    val mixed = pushWindow(EegProcessor(sampleRateHz = 512)) { sampleIndex ->
      sineSample(sampleIndex, 5.0, amplitude = 200.0) +
        sineSample(sampleIndex, 10.0, amplitude = 180.0) +
        sineSample(sampleIndex, 18.0, amplitude = 160.0) +
        sineSample(sampleIndex, 25.0, amplitude = 140.0)
    }

    assertThat(narrow).isNotNull()
    assertThat(mixed).isNotNull()
    assertThat(narrow!!.spectralEntropy).isLessThan(mixed!!.spectralEntropy)
  }

  @Test
  fun blinkRate_detectsOutlierPeaks() {
    val processor = EegProcessor(sampleRateHz = 512)
    val feature = pushWindow(processor) { sampleIndex ->
      when (sampleIndex) {
        2_200, 2_700, 3_200, 3_700 -> 1_800
        else -> 0
      }
    }

    assertThat(feature).isNotNull()
    assertThat(feature!!.blinkRateHz).isGreaterThan(0.3f)
  }

  @Test
  fun notch_toggle_doesNotChangeMeasuredLineNoise() {
    val withoutNotch = EegProcessor(sampleRateHz = 512)
    val withNotch = EegProcessor(sampleRateHz = 512).apply { setNotchEnabled(true) }

    val noNotchFeature = pushWindow(withoutNotch) { sampleIndex ->
      sineSample(sampleIndex, 50.0, amplitude = 500.0)
    }
    val notchFeature = pushWindow(withNotch) { sampleIndex ->
      sineSample(sampleIndex, 50.0, amplitude = 500.0)
    }

    assertThat(noNotchFeature).isNotNull()
    assertThat(notchFeature).isNotNull()
    assertThat(notchFeature!!.lineNoiseRatio).isWithin(0.02f).of(noNotchFeature!!.lineNoiseRatio)
  }

  @Test
  fun notch_recoversBlinksMaskedByMainsHum() {
    val humWithBlinks: (Int) -> Int = { sampleIndex ->
      val blink = when (sampleIndex) {
        1_024, 2_048, 3_072, 4_096 -> 1_500
        else -> 0
      }
      sineSample(sampleIndex, 50.0, amplitude = 800.0) + blink
    }
    // Analyse a later window (samples 2048..4607: blinks at 2048, 3072 and 4096, 3 in 5 s), past the
    // notch's start-up transient.
    fun secondWindow(processor: EegProcessor): EegFeatures? {
      var feature: EegFeatures? = null
      repeat(4_096 + 512) { sampleIndex ->
        feature = processor.pushRaw(humWithBlinks(sampleIndex), sampleIndex * 2L) ?: feature
      }
      return feature
    }

    val withoutNotch = secondWindow(EegProcessor(sampleRateHz = 512))
    val withNotch = secondWindow(EegProcessor(sampleRateHz = 512).apply { setNotchEnabled(true) })

    assertThat(withoutNotch!!.blinkRateHz).isEqualTo(0f)
    assertThat(withNotch!!.blinkRateHz).isWithin(0.01f).of(0.6f)
  }

  @Test
  fun notchBiquad_removes50HzAndPassesAlpha() {
    fun rmsAfterSettling(frequencyHz: Double): Double {
      val filter = NotchBiquad(sampleRateHz = 512, notchHz = 50.0, q = 10.0)
      var sumSquares = 0.0
      repeat(4_096) { i ->
        val y = filter.process(sin(2.0 * PI * frequencyHz * i / 512.0))
        if (i >= 1_024) sumSquares += y * y
      }
      return kotlin.math.sqrt(sumSquares / 3_072)
    }

    assertThat(rmsAfterSettling(50.0)).isLessThan(0.01)
    assertThat(rmsAfterSettling(10.0)).isWithin(0.02).of(1.0 / kotlin.math.sqrt(2.0))
  }

  @Test
  fun mainsAt60Hz_isDetectedAndNotched() {
    val humWithBlinks: (Int) -> Int = { sampleIndex ->
      val blink = if (sampleIndex == 3_072 || sampleIndex == 4_096) 1_500 else 0
      sineSample(sampleIndex, 60.0, amplitude = 800.0) + blink
    }
    val processor = EegProcessor(sampleRateHz = 512).apply { setNotchEnabled(true) }
    var feature: EegFeatures? = null
    repeat(4_096 + 512) { i -> feature = processor.pushRaw(humWithBlinks(i), i * 2L) ?: feature }

    assertThat(feature!!.mainsHz).isEqualTo(60)
    assertThat(feature!!.lineNoiseRatio).isGreaterThan(0.5f)
    assertThat(feature!!.blinkRateHz).isWithin(0.01f).of(0.4f)
  }

  @Test
  fun bands_areContiguousSoThetaAlphaBetaSumToFourToThirty() {
    // Tones exactly on the old gaps (7.5 and 12.5 Hz) must land in a band.
    val feature = pushWindow(EegProcessor(sampleRateHz = 512)) { i ->
      sineSample(i, 7.5, amplitude = 300.0) + sineSample(i, 12.5, amplitude = 300.0) + sineSample(i, 20.0, amplitude = 100.0)
    }!!

    assertThat(feature.pTheta + feature.pAlpha + feature.pBeta).isWithin(feature.p4To30 * 1e-3f).of(feature.p4To30)
    assertThat(feature.thetaPeakHz).isWithin(0.01f).of(7.5f)
    assertThat(feature.alphaPeakHz).isWithin(0.01f).of(12.5f)
  }

  @Test
  fun lostSamples_areReportedAsSignalGap() {
    val processor = EegProcessor(sampleRateHz = 512)
    var feature: EegFeatures? = null
    // Timestamps at exactly 512 Hz, so arrival timing explains nothing extra.
    fun ts(i: Int) = i * 1000L / 512
    repeat(2_560 + 400) { i -> feature = processor.pushRaw(0, ts(i)) ?: feature }
    repeat(102) { i -> feature = processor.pushLost(ts(2_960 + i)) ?: feature } // ~200 ms lost
    repeat(10) { i -> feature = processor.pushRaw(0, ts(3_062 + i)) ?: feature }

    assertThat(feature!!.signalGapMs).isEqualTo(199L)
  }

  @Test
  fun burstyButCompleteDelivery_isNotAGap() {
    val processor = EegProcessor(sampleRateHz = 512)
    var feature: EegFeatures? = null
    // Samples arrive in 128-sample bursts every 250 ms: late, but none missing.
    repeat(2_560 + 512) { i -> feature = processor.pushRaw(0, (i / 128) * 250L) ?: feature }

    assertThat(feature!!.signalGapMs).isEqualTo(0L)
  }

  @Test
  fun deliveryPauseWithoutReportedLoss_isAGap() {
    val processor = EegProcessor(sampleRateHz = 512)
    var feature: EegFeatures? = null
    fun ts(i: Int) = i * 1000L / 512 + if (i >= 2_800) 1_000L else 0L // 1 s unexplained pause
    repeat(2_560 + 512) { i -> feature = processor.pushRaw(0, ts(i)) ?: feature }

    assertThat(feature!!.signalGapMs).isAtLeast(650L)
  }

  private fun pushWindow(processor: EegProcessor, generator: (Int) -> Int): EegFeatures? {
    var feature: EegFeatures? = null
    repeat(4_096) { sampleIndex ->
      feature = processor.pushRaw(generator(sampleIndex), sampleIndex * 2L) ?: feature
    }
    return feature
  }

  private fun sineSample(sampleIndex: Int, frequencyHz: Double, amplitude: Double): Int {
    return (amplitude * sin((2.0 * PI * frequencyHz * sampleIndex) / 512.0)).roundToInt()
  }
}
