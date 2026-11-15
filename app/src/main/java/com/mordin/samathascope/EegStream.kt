package com.mordin.samathascope

import kotlin.math.abs

// Raw-stream health: whether samples arrive, at what rate, and whether a session may start.

/** No sample for longer than this means the stream is stalled. */
internal const val EEG_STREAM_STALE_MS = 900L

// Rate is measured over 2 s so Bluetooth delivery bursts average out; ±10% allows for burst jitter
// and clock skew while still catching a headset that is not streaming raw data at 512 Hz.
internal const val EEG_STREAM_RATE_WINDOW_MS = 2_000L
internal const val EEG_STREAM_RATE_TOLERANCE = 0.10f

internal data class EegStreamSnapshot(
  val status: EegStreamStatus,
  val ready: Boolean,
  val samplesPerSecond: Float,
  val stallMs: Long,
)

/**
 * [lastMeasuredSamplesPerSecond] is the rate over the last full [EEG_STREAM_RATE_WINDOW_MS] window
 * (0 until one has completed since the stream started or resumed); [rawCountThisSecond] counts samples
 * in the window in progress since [lastRateTickMs]. The stream is ready only once a full window has
 * measured close to [expectedSamplesPerSecond]: all analysis assumes that rate.
 */
internal fun buildEegStreamSnapshot(
  connected: Boolean,
  debugReplayEnabled: Boolean,
  nowMs: Long,
  lastRawSampleAtMs: Long,
  lastMeasuredSamplesPerSecond: Float,
  rawCountThisSecond: Int,
  lastRateTickMs: Long,
  expectedSamplesPerSecond: Int = DEBUG_RAW_LOOP_SAMPLE_RATE_HZ,
): EegStreamSnapshot {
  if (!connected && !debugReplayEnabled) {
    return EegStreamSnapshot(
      status = EegStreamStatus.DISCONNECTED,
      ready = false,
      samplesPerSecond = 0f,
      stallMs = 0L,
    )
  }

  if (lastRawSampleAtMs == 0L) {
    return EegStreamSnapshot(
      status = EegStreamStatus.WAITING_FOR_RAW,
      ready = false,
      samplesPerSecond = 0f,
      stallMs = 0L,
    )
  }

  val sampleWindowMs = (nowMs - lastRateTickMs).coerceAtLeast(1L)
  val provisionalSamplesPerSecond = if (lastRateTickMs == 0L) {
    0f
  } else {
    rawCountThisSecond * 1000f / sampleWindowMs.toFloat()
  }
  val measured = lastMeasuredSamplesPerSecond > 0f
  val effectiveSamplesPerSecond = if (measured) lastMeasuredSamplesPerSecond else provisionalSamplesPerSecond
  val rateMatches = abs(lastMeasuredSamplesPerSecond - expectedSamplesPerSecond) <=
    expectedSamplesPerSecond * EEG_STREAM_RATE_TOLERANCE
  val stallMs = (nowMs - lastRawSampleAtMs).coerceAtLeast(0L)
  val status = when {
    stallMs > EEG_STREAM_STALE_MS -> EegStreamStatus.STALLED
    !measured -> EegStreamStatus.CONFIRMING
    !rateMatches -> EegStreamStatus.WRONG_RATE
    debugReplayEnabled -> EegStreamStatus.DEBUG_REPLAY
    else -> EegStreamStatus.LIVE
  }

  return EegStreamSnapshot(
    status = status,
    ready = status == EegStreamStatus.LIVE || status == EegStreamStatus.DEBUG_REPLAY,
    samplesPerSecond = effectiveSamplesPerSecond,
    stallMs = stallMs,
  )
}
