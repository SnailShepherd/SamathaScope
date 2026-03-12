package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Test

class EegProcessorTest {

  @Test
  fun pushRaw_usesEightSecondWindowWithOneSecondHop() {
    val processor = EegProcessor(sampleRateHz = 512)
    var featuresCount = 0

    repeat(4_096) { index ->
      if (processor.pushRaw(0, index * 2L) != null) {
        featuresCount++
      }
    }
    repeat(511) { index ->
      if (processor.pushRaw(0, (4_096 + index) * 2L) != null) {
        featuresCount++
      }
    }
    val next = processor.pushRaw(0, 9_214L)
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
        512, 1_024, 1_536, 2_048 -> 1_800
        else -> 0
      }
    }

    assertThat(feature).isNotNull()
    assertThat(feature!!.blinkRateHz).isGreaterThan(0.3f)
  }

  @Test
  fun notch_toggle_reducesMeasuredLineNoise() {
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
    assertThat(notchFeature!!.lineNoiseRatio).isLessThan(noNotchFeature!!.lineNoiseRatio)
  }

  private fun pushWindow(processor: EegProcessor, generator: (Int) -> Int): EegFeatures? {
    var feature: EegFeatures? = null
    repeat(4_096) { sampleIndex ->
      feature = processor.pushRaw(generator(sampleIndex), sampleIndex * 2L)
    }
    return feature
  }

  private fun sineSample(sampleIndex: Int, frequencyHz: Double, amplitude: Double): Int {
    return (amplitude * sin((2.0 * PI * frequencyHz * sampleIndex) / 512.0)).roundToInt()
  }
}
