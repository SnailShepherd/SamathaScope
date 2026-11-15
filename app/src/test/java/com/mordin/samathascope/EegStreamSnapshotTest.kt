package com.mordin.samathascope

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EegStreamSnapshotTest {

  private fun snapshot(
    connected: Boolean = true,
    debugReplayEnabled: Boolean = false,
    nowMs: Long,
    lastRawSampleAtMs: Long,
    lastMeasuredSamplesPerSecond: Float,
    rawCountThisSecond: Int = 0,
    lastRateTickMs: Long = 0L,
  ) = buildEegStreamSnapshot(
    connected = connected,
    debugReplayEnabled = debugReplayEnabled,
    nowMs = nowMs,
    lastRawSampleAtMs = lastRawSampleAtMs,
    lastMeasuredSamplesPerSecond = lastMeasuredSamplesPerSecond,
    rawCountThisSecond = rawCountThisSecond,
    lastRateTickMs = lastRateTickMs,
    expectedSamplesPerSecond = 512,
  )

  @Test
  fun disconnected_streamIsNotReady() {
    val s = snapshot(connected = false, nowMs = 5_000L, lastRawSampleAtMs = 0L, lastMeasuredSamplesPerSecond = 0f)

    assertEquals(EegStreamStatus.DISCONNECTED, s.status)
    assertFalse(s.ready)
    assertEquals(0f, s.samplesPerSecond)
    assertEquals(0L, s.stallMs)
  }

  @Test
  fun waitingWithoutRawSamples_staysBlocked() {
    val s = snapshot(nowMs = 5_000L, lastRawSampleAtMs = 0L, lastMeasuredSamplesPerSecond = 0f)

    assertEquals(EegStreamStatus.WAITING_FOR_RAW, s.status)
    assertFalse(s.ready)
  }

  @Test
  fun beforeAFullRateWindow_isConfirmingWithProvisionalRate() {
    val s = snapshot(
      nowMs = 5_500L,
      lastRawSampleAtMs = 5_480L,
      lastMeasuredSamplesPerSecond = 0f,
      rawCountThisSecond = 256,
      lastRateTickMs = 5_000L,
    )

    assertEquals(EegStreamStatus.CONFIRMING, s.status)
    assertFalse(s.ready)
    assertEquals(512f, s.samplesPerSecond, 1f)
  }

  @Test
  fun rateNear512_unblocksSessionStart() {
    val s = snapshot(nowMs = 7_000L, lastRawSampleAtMs = 6_950L, lastMeasuredSamplesPerSecond = 505f, lastRateTickMs = 6_000L)

    assertEquals(EegStreamStatus.LIVE, s.status)
    assertTrue(s.ready)
    assertEquals(505f, s.samplesPerSecond)
    assertEquals(50L, s.stallMs)
  }

  @Test
  fun rateFarFrom512_isWrongRateAndBlocked() {
    // The old check accepted anything above 96 samples/s.
    val s = snapshot(nowMs = 7_000L, lastRawSampleAtMs = 6_950L, lastMeasuredSamplesPerSecond = 220f, lastRateTickMs = 6_000L)

    assertEquals(EegStreamStatus.WRONG_RATE, s.status)
    assertFalse(s.ready)
  }

  @Test
  fun stalledRawStream_relocksSessionStart() {
    val s = snapshot(nowMs = 8_000L, lastRawSampleAtMs = 6_900L, lastMeasuredSamplesPerSecond = 512f, lastRateTickMs = 6_000L)

    assertEquals(EegStreamStatus.STALLED, s.status)
    assertFalse(s.ready)
    assertEquals(1_100L, s.stallMs)
  }

  @Test
  fun debugReplayStream_isReadyWithoutBluetoothConnection() {
    val s = snapshot(
      connected = false,
      debugReplayEnabled = true,
      nowMs = 9_000L,
      lastRawSampleAtMs = 8_970L,
      lastMeasuredSamplesPerSecond = 516f,
      lastRateTickMs = 8_000L,
    )

    assertEquals(EegStreamStatus.DEBUG_REPLAY, s.status)
    assertTrue(s.ready)
    assertEquals(30L, s.stallMs)
  }
}
