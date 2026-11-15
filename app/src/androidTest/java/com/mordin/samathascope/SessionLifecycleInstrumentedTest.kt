package com.mordin.samathascope

import android.app.Application
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Drives a whole session on the shipped debug raw loop: stream confirmation, start, calibration skip,
 * pause/resume and stop. No headset needed.
 */
class SessionLifecycleInstrumentedTest {

  private lateinit var vm: MainViewModel

  @Before
  fun setUp() {
    vm = MainViewModel(ApplicationProvider.getApplicationContext<Application>())
    vm.setAudioEnabled(false) // Keeps the test silent and independent of audio focus.
  }

  @After
  fun tearDown() {
    vm.stopSession()
    vm.setDebugRawLoopEnabled(false)
  }

  @Test
  fun debugReplaySession_startsCalibratesPausesAndStops() {
    // The stored loop is loaded off the main thread at startup; the shipped asset guarantees one.
    waitUntil(timeoutMs = 3_000) { vm.ui.value.debugRawLoopAvailable }

    vm.setDebugRawLoopEnabled(true)
    // The stream needs one full 2 s rate window at ~512 samples/s before a session may start.
    waitUntil(timeoutMs = 8_000) { vm.ui.value.eegStreamReady }
    assertTrue(vm.ui.value.eegStreamStatus == EegStreamStatus.DEBUG_REPLAY)

    vm.startSession()
    assertTrue(vm.ui.value.sessionRunning)
    assertTrue(vm.ui.value.calibrating)

    // Skipping right away leaves too few windows in the matching half: provisional baseline.
    vm.skipCalibration()
    assertFalse(vm.ui.value.calibrating)
    assertTrue(vm.ui.value.baselineProvisional)

    vm.togglePause()
    assertTrue(vm.ui.value.sessionPaused)
    vm.togglePause()
    assertFalse(vm.ui.value.sessionPaused)

    vm.stopSession()
    assertFalse(vm.ui.value.sessionRunning)
    assertFalse(vm.ui.value.recordingActive)
  }

  private fun waitUntil(timeoutMs: Long, condition: () -> Boolean) {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    while (!condition()) {
      check(SystemClock.elapsedRealtime() < deadline) { "condition not met within $timeoutMs ms" }
      Thread.sleep(50)
    }
  }
}
