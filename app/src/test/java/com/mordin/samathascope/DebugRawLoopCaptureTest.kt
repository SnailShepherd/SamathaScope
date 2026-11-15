package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DebugRawLoopCaptureTest {

  @Test
  fun capture_fillsThenStopsAndAveragesHeadsetValues() {
    val capture = DebugRawLoopCapture(sampleCount = 3)
    capture.addPoorSignal(10)
    capture.addPoorSignal(30)
    capture.addAttention(80)

    listOf(5, 6, 7, 8).forEach(capture::addSample)
    val record = capture.buildRecord(fallback = DebugRawLoopMetadata(poorSignal = 99, attention = 1, meditation = 42))

    assertThat(capture.isFull).isTrue()
    assertThat(record.samples.toList()).containsExactly(5, 6, 7).inOrder()
    assertThat(record.metadata.poorSignal).isEqualTo(20)
    assertThat(record.metadata.attention).isEqualTo(80)
    assertThat(record.metadata.meditation).isEqualTo(42) // Never reported: fallback.
  }

  @Test
  fun reset_startsOver() {
    val capture = DebugRawLoopCapture(sampleCount = 2)
    capture.addSample(1)
    capture.addSample(2)
    capture.addMeditation(70)

    capture.reset()

    assertThat(capture.isFull).isFalse()
    assertThat(capture.samplesCaptured).isEqualTo(0)
    assertThat(capture.buildRecord(DebugRawLoopMetadata(meditation = 5)).metadata.meditation).isEqualTo(5)
  }
}
