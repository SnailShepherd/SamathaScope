package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DebugRawLoopStoreTest {

  @Test
  fun encodeAndDecode_roundTripsSignedSamples() {
    val samples = intArrayOf(-2048, -512, -1, 0, 1, 512, 2047)

    val decoded = decodeDebugRawLoop(
      bytes = encodeDebugRawLoop(samples),
      sampleCount = samples.size,
    )

    assertThat(decoded?.toList()).isEqualTo(samples.toList())
  }

  @Test
  fun decodeDebugRawLoop_rejectsWrongLength() {
    val decoded = decodeDebugRawLoop(
      bytes = ByteArray(5),
      sampleCount = 4,
    )

    assertThat(decoded).isNull()
  }

  @Test
  fun metadataRoundTrip_preservesReplayTelemetry() {
    val metadata = DebugRawLoopMetadata(
      poorSignal = 4,
      attention = 61,
      meditation = 58,
    )

    val decoded = decodeDebugRawLoopMetadata(encodeDebugRawLoopMetadata(metadata))

    assertThat(decoded).isEqualTo(metadata)
  }
}
