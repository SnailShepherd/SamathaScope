package com.mordin.samathascope

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EegStreamSnapshotTest {

  @Test
  fun disconnected_streamIsNotReady() {
    val snapshot = buildEegStreamSnapshot(
      connected = false,
      debugReplayEnabled = false,
      nowMs = 5_000L,
      lastRawSampleAtMs = 0L,
      firstRawBurstAtMs = 0L,
      lastMeasuredSamplesPerSecond = 0f,
      rawCountThisSecond = 0,
      lastRateTickMs = 0L,
    )

    assertEquals(EegStreamStatus.DISCONNECTED, snapshot.status)
    assertFalse(snapshot.ready)
    assertEquals(0f, snapshot.samplesPerSecond)
    assertEquals(0L, snapshot.stallMs)
  }

  @Test
  fun waitingWithoutRawSamples_staysBlocked() {
    val snapshot = buildEegStreamSnapshot(
      connected = true,
      debugReplayEnabled = false,
      nowMs = 5_000L,
      lastRawSampleAtMs = 0L,
      firstRawBurstAtMs = 0L,
      lastMeasuredSamplesPerSecond = 0f,
      rawCountThisSecond = 0,
      lastRateTickMs = 0L,
    )

    assertEquals(EegStreamStatus.WAITING_FOR_RAW, snapshot.status)
    assertFalse(snapshot.ready)
  }

  @Test
  fun recentRawStream_needsShortConfirmationBeforeReady() {
    val snapshot = buildEegStreamSnapshot(
      connected = true,
      debugReplayEnabled = false,
      nowMs = 5_300L,
      lastRawSampleAtMs = 5_260L,
      firstRawBurstAtMs = 5_000L,
      lastMeasuredSamplesPerSecond = 180f,
      rawCountThisSecond = 96,
      lastRateTickMs = 5_000L,
    )

    assertEquals(EegStreamStatus.CONFIRMING, snapshot.status)
    assertFalse(snapshot.ready)
  }

  @Test
  fun liveRawStream_unblocksSessionStart() {
    val snapshot = buildEegStreamSnapshot(
      connected = true,
      debugReplayEnabled = false,
      nowMs = 7_000L,
      lastRawSampleAtMs = 6_950L,
      firstRawBurstAtMs = 6_000L,
      lastMeasuredSamplesPerSecond = 220f,
      rawCountThisSecond = 150,
      lastRateTickMs = 6_500L,
    )

    assertEquals(EegStreamStatus.LIVE, snapshot.status)
    assertTrue(snapshot.ready)
    assertTrue(snapshot.samplesPerSecond >= 220f)
    assertEquals(50L, snapshot.stallMs)
  }

  @Test
  fun stalledRawStream_relocksSessionStart() {
    val snapshot = buildEegStreamSnapshot(
      connected = true,
      debugReplayEnabled = false,
      nowMs = 8_000L,
      lastRawSampleAtMs = 6_900L,
      firstRawBurstAtMs = 6_000L,
      lastMeasuredSamplesPerSecond = 220f,
      rawCountThisSecond = 0,
      lastRateTickMs = 7_500L,
    )

    assertEquals(EegStreamStatus.STALLED, snapshot.status)
    assertFalse(snapshot.ready)
    assertEquals(1_100L, snapshot.stallMs)
  }

  @Test
  fun debugReplayStream_isReadyWithoutBluetoothConnection() {
    val snapshot = buildEegStreamSnapshot(
      connected = false,
      debugReplayEnabled = true,
      nowMs = 9_000L,
      lastRawSampleAtMs = 8_970L,
      firstRawBurstAtMs = 8_000L,
      lastMeasuredSamplesPerSecond = 510f,
      rawCountThisSecond = 120,
      lastRateTickMs = 8_750L,
    )

    assertEquals(EegStreamStatus.DEBUG_REPLAY, snapshot.status)
    assertTrue(snapshot.ready)
    assertTrue(snapshot.samplesPerSecond >= 510f)
    assertEquals(30L, snapshot.stallMs)
  }
}
