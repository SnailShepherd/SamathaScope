package com.mordin.samathascope

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.LinkedHashSet
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Threading model: raw samples arrive on the Bluetooth reader thread (or the debug-replay coroutine),
 * the session/monitor loops run on [Dispatchers.Default], and user actions arrive on Main. All mutable
 * signal-processing state (processor, calibration, scorer, smoothers, recorder, audio engine, counters)
 * is only touched while holding [lock], so those three threads never interleave inside it.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

  // The application context (never an Activity), read through AndroidViewModel instead of a stored field.
  private val ctx: Context get() = getApplication()
  private val lock = Any()
  private val rawSampleRateHz = DEBUG_RAW_LOOP_SAMPLE_RATE_HZ
  private val plotSettingsStore = createPlotSettingsStore(ctx)
  private val noiseColorStore = createNoiseColorStore(ctx)
  private val deviceSelectionStore = createDeviceSelectionStore(ctx)
  private val sessionModeStore = createSessionModeStore(ctx)
  private val userSettingsStore = createUserSettingsStore(ctx)
  private val initialSettings = userSettingsStore.load()
  private val debugRawLoopStore = createDebugRawLoopStore(ctx)
  /** Loaded off the main thread in init; null until then or if there is none. */
  private var debugRawLoopRecord: DebugRawLoopRecord? = null
  private val debugRawLoopCapture = DebugRawLoopCapture(rawSampleRateHz * DEBUG_RAW_LOOP_DURATION_SECONDS)

  private val _ui = MutableStateFlow(
    UiState(
      plotSettings = plotSettingsStore.load(),
      noiseColor = noiseColorStore.load(),
      selectedDeviceMac = deviceSelectionStore.load(),
      sessionMode = sessionModeStore.load(),
      audioEnabled = initialSettings.audioEnabled,
      invertReward = initialSettings.invertReward,
      gamma = initialSettings.gamma,
      gMinDb = initialSettings.gMinDb,
      gMaxDb = initialSettings.gMaxDb,
      notch50Enabled = initialSettings.notch50Enabled,
      recordingEnabled = initialSettings.recordingEnabled,
      feedbackMetric = initialSettings.feedbackMetric,
      visibleMetrics = initialSettings.visibleMetrics,
      selectedMetricInfo = initialSettings.feedbackMetric,
      themeMode = initialSettings.themeMode,
      developerOptionsEnabled = initialSettings.developerOptionsEnabled,
    )
  )
  val ui: StateFlow<UiState> = _ui

  /** The theme setting alone, so the activity root recomposes only when it changes. */
  val themeMode: StateFlow<ThemeMode> = _ui
    .map { it.themeMode }
    .stateIn(viewModelScope, SharingStarted.Eagerly, _ui.value.themeMode)

  // The raw preview changes ~16 times a second, so it has its own flow: only the raw plot recomposes.
  private val _rawPreview = MutableStateFlow(IntArray(0))
  val rawPreview: StateFlow<IntArray> = _rawPreview

  private val feedbackAudio = FeedbackAudio(ctx) { onAudioFocusLost() }

  private fun persistUserSettings() {
    val state = _ui.value
    userSettingsStore.save(
      UserSettings(
        audioEnabled = state.audioEnabled,
        invertReward = state.invertReward,
        gamma = state.gamma,
        gMinDb = state.gMinDb,
        gMaxDb = state.gMaxDb,
        notch50Enabled = state.notch50Enabled,
        recordingEnabled = state.recordingEnabled,
        feedbackMetric = state.feedbackMetric,
        visibleMetrics = state.visibleMetrics,
        themeMode = state.themeMode,
        developerOptionsEnabled = state.developerOptionsEnabled,
      )
    )
  }

  private val btAdapter: BluetoothAdapter? by lazy {
    val mgr = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    mgr.adapter
  }

  private var client: BluetoothMindWaveClient? = null

  private val eegProcessor = EegProcessor(sampleRateHz = rawSampleRateHz)
  // All internal timing (sample arrival, session clock, calibration) uses elapsedRealtime: monotonic,
  // unaffected by wall-clock changes. Wall-clock time is used only for recorded timestamps.
  private val calibration = CalibrationManager(calibrationSeconds = CALIBRATION_SECONDS, nowMs = SystemClock::elapsedRealtime)
  /** The calibration baseline for this session's mode, or null if calibration produced none. */
  private var sessionBaseline: Calibration? = null
  private val scorer = ScoreModel()
  private val metricHistory = MetricHistory(maxSeconds = 600, pointsPerSecond = 1)


  private val outputSmoother = OutputSmoother()

  private val artefactPromptOrder = listOf(
    ArtefactPrompt.LOOK_LEFT_RIGHT,
    ArtefactPrompt.LOOK_UP_DOWN,
    ArtefactPrompt.JAW_CLENCH,
    ArtefactPrompt.FROWN,
    ArtefactPrompt.RELAX,
  )

  private val sessionStats = SessionStats()

  private var sessionStartMs: Long = 0L
  private var pausedAtMs: Long = 0L
  private var pausedAccumMs: Long = 0L
  private var sessionJob: Job? = null
  private var streamMonitorJob: Job? = null
  private var rawPreviewJob: Job? = null

  private var recorder: SessionRecorder? = null
  private var debugRawReplayJob: Job? = null

  private var lastRawValue = 0
  private var rawCountThisSecond = 0
  private var lastRateTickMs: Long = 0L
  private var lastMeasuredSamplesPerSecond = 0f
  private var lastRawSampleAtMs: Long = 0L
  private var rawPreviewDirty = false

  private var lastAdaptiveCalibrationUpdateMs = 0L
  private var calibrationRawCount = 0
  private var calibrationRawMean = 0.0
  private var calibrationRawM2 = 0.0
  private var eyesClosedCuePlayed = false
  private var artefactPromptIndex = 0
  private var artefactPromptStartMs = 0L
  private var artefactCalibrationProfile = ArtefactCalibrationProfile()

  init {
    eegProcessor.setNotchEnabled(_ui.value.notch50Enabled)
    scorer.setArtefactCalibrationProfile(artefactCalibrationProfile)
    if (hasBluetoothConnectPermission()) {
      _ui.update { it.copy(btPermissionGranted = true) }
      refreshBondedDevices()
    }
    refreshRawPreview()
    refreshMetricPlotSeries()
    startStreamMonitor()
    startRawPreviewTicker()
    loadStoredDataInBackground()
  }

  /** Disk reads that would otherwise block the main thread while the app starts. */
  private fun loadStoredDataInBackground() {
    viewModelScope.launch(Dispatchers.IO) {
      val hasRecordedSession = latestSessionDir(ctx) != null
      val loop = debugRawLoopStore.load(rawSampleRateHz * DEBUG_RAW_LOOP_DURATION_SECONDS)
      synchronized(lock) {
        // A loop captured in the meantime is newer than the stored one.
        if (debugRawLoopRecord == null) debugRawLoopRecord = loop
        _ui.update {
          it.copy(
            recordedSessionAvailable = it.recordedSessionAvailable || hasRecordedSession,
            debugRawLoopAvailable = debugRawLoopRecord != null,
          )
        }
      }
    }
  }

  private fun hasBluetoothConnectPermission(): Boolean {
    if (Build.VERSION.SDK_INT < 31) return true
    return ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
  }

  fun onPermissionsResult(result: Map<String, Boolean>) {
    val ok = Build.VERSION.SDK_INT < 31 || result[Manifest.permission.BLUETOOTH_CONNECT] == true
    _ui.update { it.copy(btPermissionGranted = ok) }
    if (ok) refreshBondedDevices()
  }

  fun refreshBondedDevices() {
    val adapter = btAdapter
    if (adapter == null) {
      _ui.update { it.copy(connectionError = ctx.getString(R.string.bt_error_unavailable)) }
      return
    }
    if (!_ui.value.btPermissionGranted && Build.VERSION.SDK_INT >= 31) return
    // A refresh is the user's way to retry, so it replaces any earlier message.
    val offMessage = if (adapter.isEnabled) null else ctx.getString(R.string.bt_error_off)
    _ui.update { it.copy(connectionError = offMessage) }

    val bonded = try {
      adapter.bondedDevices?.map {
        BondedDevice(mac = it.address, display = "${it.name ?: "Unknown"} (${it.address})")
      } ?: emptyList()
    } catch (e: SecurityException) {
      Log.w(TAG, "Bluetooth permission missing while listing bonded devices", e)
      emptyList()
    }

    val remembered = deviceSelectionStore.load()
    _ui.update { state ->
      state.copy(
        bondedDevices = bonded,
        selectedDeviceMac = resolveSelectedDeviceMac(
          current = state.selectedDeviceMac,
          remembered = remembered,
          bonded = bonded.map { it.mac },
        ),
      )
    }
  }

  fun selectDevice(mac: String) {
    _ui.update { it.copy(selectedDeviceMac = mac) }
    deviceSelectionStore.save(mac)
  }

  private fun nowMs(): Long = SystemClock.elapsedRealtime()

  /** Changes take effect from the next session; the running session keeps its baseline. */
  fun setSessionMode(mode: SessionMode) {
    if (_ui.value.sessionRunning) return
    _ui.update { it.copy(sessionMode = mode) }
    sessionModeStore.save(mode)
  }

  fun selectTab(tab: AppTab) {
    _ui.update { it.copy(selectedTab = tab) }
  }

  fun connect() {
    if (_ui.value.connected || _ui.value.headsetConnecting || _ui.value.debugRawLoopEnabled) return
    val mac = _ui.value.selectedDeviceMac ?: return
    val adapter = btAdapter
    if (adapter == null) {
      _ui.update { it.copy(connectionError = ctx.getString(R.string.bt_error_unavailable)) }
      return
    }
    if (!adapter.isEnabled) {
      _ui.update { it.copy(connectionError = ctx.getString(R.string.bt_error_off)) }
      return
    }
    val device = try {
      adapter.getRemoteDevice(mac)
    } catch (e: IllegalArgumentException) {
      Log.w(TAG, "Invalid Bluetooth address $mac", e)
      _ui.update { it.copy(connectionError = ctx.getString(R.string.bt_error_invalid_address)) }
      return
    }
    deviceSelectionStore.save(mac)

    val newClient = BluetoothMindWaveClient(device)
    synchronized(lock) {
      client?.close()
      client = newClient
      resetStreamTracking()
    }
    _ui.update {
      it.copy(
        headsetConnecting = true,
        connectionError = null,
        connected = false,
        eegStreamStatus = EegStreamStatus.DISCONNECTED,
        eegStreamReady = false,
        streamStallMs = 0,
        samplesPerSecond = 0f,
        batteryPercent = null,
      )
    }

    // Callbacks from a client we have since replaced or closed are ignored, so a late disconnect from
    // an old socket can never reset the state of a newer connection attempt.
    newClient.connect(
      onConnected = {
        synchronized(lock) {
          if (client !== newClient) return@synchronized
          _ui.update {
            it.copy(
              headsetConnecting = false,
              connected = true,
              eegStreamStatus = EegStreamStatus.WAITING_FOR_RAW,
              eegStreamReady = false,
            )
          }
        }
      },
      onDisconnected = { error ->
        synchronized(lock) {
          if (client !== newClient) return@synchronized
          if (error != null) Log.w(TAG, "Headset connection ended", error)
          val message = ctx.getString(
            if (_ui.value.connected) R.string.bt_error_connection_lost else R.string.bt_error_connect_failed
          )
          client = null
          cancelDebugRawLoopCaptureLocked()
          resetStreamTracking()
          stopSessionLocked()
          _ui.update {
            it.copy(
              headsetConnecting = false,
              connectionError = message,
              connected = false,
              eegStreamStatus = EegStreamStatus.DISCONNECTED,
              eegStreamReady = false,
              streamStallMs = 0,
              samplesPerSecond = 0f,
              batteryPercent = null,
            )
          }
        }
      },
      onData = { data ->
        synchronized(lock) {
          if (client === newClient) handleThinkGearData(data)
        }
      },
    )
  }

  fun disconnect() = synchronized(lock) {
    cancelDebugRawLoopCaptureLocked()
    stopSessionLocked()
    client?.close()
    client = null
    resetStreamTracking()
    _ui.update {
      it.copy(
        headsetConnecting = false,
        connected = false,
        eegStreamStatus = EegStreamStatus.DISCONNECTED,
        eegStreamReady = false,
        streamStallMs = 0,
        samplesPerSecond = 0f,
        batteryPercent = null,
      )
    }
  }

  fun startSession() {
    synchronized(lock) {
      if (_ui.value.sessionRunning || !_ui.value.eegStreamReady) return

      eegProcessor.reset()
      eegProcessor.setNotchEnabled(_ui.value.notch50Enabled)
      calibration.reset()
      scorer.reset()
      artefactCalibrationProfile = ArtefactCalibrationProfile()
      scorer.setArtefactCalibrationProfile(artefactCalibrationProfile)
      metricHistory.reset()
      resetRawCalibrationStats()
      outputSmoother.reset()
      calibration.resetArtefactCapture()
      sessionBaseline = null

      sessionStats.reset()
      lastAdaptiveCalibrationUpdateMs = 0L
      eyesClosedCuePlayed = false
      artefactPromptIndex = 0
      artefactPromptStartMs = 0L

      sessionStartMs = nowMs()
      pausedAtMs = 0L
      pausedAccumMs = 0L

      if (!_ui.value.plotSettings.getValue(PlotType.RAW).isUserLocked) {
        val (yMin, yMax) = PlotMath.defaultRawRange()
        updatePlotSettings(
          type = PlotType.RAW,
          newSettings = _ui.value.plotSettings.getValue(PlotType.RAW).copy(
            yMin = yMin,
            yMax = yMax,
            isUserLocked = false,
          ),
          persist = true,
        )
      }

      recorder = if (_ui.value.recordingEnabled) startRecorderOrNull() else null
      _ui.update {
        it.copy(
          lastRecordingPath = recorder?.sessionDir?.absolutePath,
          recordingActive = recorder != null,
          pausedByAudioFocus = false,
        )
      }

      _ui.update {
        it.copy(
          sessionRunning = true,
          sessionPaused = false,
          calibrating = true,
          calibrationPhase = CalibrationPhase.EYES_OPEN,
          calibrationInstruction = calibrationInstructionFor(CalibrationPhase.EYES_OPEN, calibration.calibrationSeconds),
          calibrationRemainingSec = calibration.calibrationSeconds,
          artefactCalibrationState = ArtefactCalibrationUiState(),
          baselineProvisional = false,
          sessionElapsedSec = 0,
          meditationProxy = 0f,
          settledness = 0f,
          control = 0f,
          alertness = 0f,
          drowsyScore = 0f,
          artefactScore = 0f,
          qualityConfidence = 0f,
          effortfulFocusScore = 0f,
          mindWanderingScore = 0f,
          displayedStateLabel = StateLabel.UNCERTAIN,
          drowsyTarContribution = 0f,
          drowsyTbrContribution = 0f,
          drowsyEntropyContribution = 0f,
          drowsyAbrContribution = 0f,
          avgMeditationProxy = 0f,
          timeMeditationProxyOver80Seconds = 0,
          audioRunning = feedbackAudio.isActive && it.audioEnabled,
          audioMuted = true,
          metricPlotSeries = emptyMap(),
          rawPlotOffsetSeconds = 0,
          metricPlotOffsetSeconds = 0,
        )
      }

      refreshRawPreview()
      refreshMetricPlotSeries()
      ensureSessionAudioEngine(fadeIn = false)
      syncFeedbackAudioState(fadeIn = false)
    }

    // Reached only when the session actually started (the guard above returns from startSession).
    SessionKeepAliveService.start(ctx)

    sessionJob?.cancel()
    sessionJob = viewModelScope.launch(Dispatchers.Default) {
      while (_ui.value.sessionRunning) {
        synchronized(lock) { tickSession(nowMs()) }
        delay(200)
      }
    }
  }

  private fun tickSession(now: Long) {
    if (!_ui.value.sessionRunning) return
    val elapsedMs = now - sessionStartMs - pausedAccumMs - if (_ui.value.sessionPaused) (now - pausedAtMs) else 0L
    val elapsedSec = (elapsedMs / 1000L).toInt().coerceAtLeast(0)

    if (_ui.value.calibrating) {
      val rem = calibration.remainingSeconds()
      val phase = when (calibration.currentPhase()) {
        SessionMode.EYES_OPEN -> CalibrationPhase.EYES_OPEN
        SessionMode.EYES_CLOSED -> CalibrationPhase.EYES_CLOSED
      }
      if (phase == CalibrationPhase.EYES_CLOSED && !eyesClosedCuePlayed) {
        eyesClosedCuePlayed = true
        if (_ui.value.audioEnabled) {
          BellSoundPlayer.playCalibrationComplete()
        }
      }
      _ui.update {
        it.copy(
          calibrationRemainingSec = rem,
          calibrationPhase = phase,
          calibrationInstruction = calibrationInstructionFor(phase, rem),
          sessionElapsedSec = elapsedSec,
        )
      }
      // Finish when the timer runs out even if a stalled stream left too few windows: the half
      // matching the session then yields no baseline, and scoring continues on a provisional one
      // instead of the countdown sitting at 0 s.
      if (rem <= 0) {
        finishCleanCalibration(now)
      }
    } else if (_ui.value.artefactCalibrationState.running) {
      updateArtefactCaptureUi(now, elapsedSec)
    } else {
      _ui.update { it.copy(sessionElapsedSec = elapsedSec) }
    }
  }

  fun stopSession() = synchronized(lock) { stopSessionLocked() }

  private fun stopSessionLocked() {
    sessionJob?.cancel()
    sessionJob = null
    if (_ui.value.sessionRunning) SessionKeepAliveService.stop(ctx)

    shutdownAudioEngine()

    if (recorder != null) {
      writeRecorderSafely {
        it.appendMeta(sessionEndMetadata(endedAtWallMs = System.currentTimeMillis(), durationMs = nowMs() - sessionStartMs))
      }
      recorder?.stop()
      _ui.update { it.copy(recordedSessionAvailable = true) }
    }
    recorder = null
    calibration.resetArtefactCapture()
    eyesClosedCuePlayed = false
    artefactPromptIndex = 0
    artefactPromptStartMs = 0L

    _ui.update {
      it.copy(
        sessionRunning = false,
        sessionPaused = false,
        pausedByAudioFocus = false,
        recordingActive = false,
        calibrating = false,
        calibrationPhase = null,
        calibrationInstruction = "",
        calibrationRemainingSec = 0,
        artefactCalibrationState = ArtefactCalibrationUiState(),
        baselineProvisional = false,
        sessionElapsedSec = 0,
        audioRunning = false,
        audioMuted = true,
      )
    }
  }

  fun togglePause() = synchronized(lock) {
    if (!_ui.value.sessionRunning) return@synchronized
    if (_ui.value.sessionPaused) resumeLocked() else pauseLocked()
  }

  /** Another app took the audio output: pause, as if the user pressed Pause. They resume manually. */
  private fun onAudioFocusLost() = synchronized(lock) {
    if (!_ui.value.sessionRunning || _ui.value.sessionPaused) return@synchronized
    pauseLocked()
    _ui.update { it.copy(pausedByAudioFocus = true) }
  }

  private fun pauseLocked() {
    pausedAtMs = nowMs()
    // Calibration and the artefact prompts are timed tasks; their clocks stop while paused.
    calibration.pause()
    _ui.update { it.copy(sessionPaused = true, audioMuted = true) }
    syncFeedbackAudioState()
  }

  private fun resumeLocked() {
    val now = nowMs()
    // Focus may have gone to another app while paused; take it back for the feedback noise.
    feedbackAudio.reacquireFocus()
    val pausedForMs = (now - pausedAtMs).coerceAtLeast(0L)
    pausedAccumMs += pausedForMs
    pausedAtMs = 0L
    calibration.resume()
    if (_ui.value.artefactCalibrationState.running) artefactPromptStartMs += pausedForMs
    // The paused stretch must not count towards "time over 80%".
    sessionStats.markGap()
    _ui.update {
      it.copy(sessionPaused = false, pausedByAudioFocus = false, rawPlotOffsetSeconds = 0, metricPlotOffsetSeconds = 0)
    }
    refreshRawPreview()
    refreshMetricPlotSeries()
    syncFeedbackAudioState(fadeIn = true)
  }

  fun toggleVisibleMetric(type: PlotType) = synchronized(lock) {
    if (type == PlotType.RAW) return@synchronized
    val next = LinkedHashSet(_ui.value.visibleMetrics)
    if (next.contains(type)) {
      if (next.size > 1) next.remove(type)
    } else {
      next.add(type)
    }
    _ui.update { it.copy(visibleMetrics = next, selectedMetricInfo = type) }
    persistUserSettings()
    refreshMetricPlotSeries()
  }

  fun focusMetricInfo(type: PlotType) {
    _ui.update { it.copy(selectedMetricInfo = type) }
  }

  fun setFeedbackMetric(value: PlotType) = synchronized(lock) {
    if (!rewardSelectableMetrics().contains(value)) return@synchronized
    _ui.update {
      it.copy(
        feedbackMetric = value,
        selectedMetricInfo = value,
      )
    }
    persistUserSettings()
    refreshMetricPlotSeries()
  }

  fun setAudioEnabled(value: Boolean) = synchronized(lock) {
    _ui.update { it.copy(audioEnabled = value) }
    persistUserSettings()
    if (!value) {
      shutdownAudioEngine()
      _ui.update { it.copy(audioRunning = false, audioMuted = true) }
    } else {
      ensureSessionAudioEngine(fadeIn = true)
      syncFeedbackAudioState(fadeIn = true)
    }
  }

  fun setInvertReward(value: Boolean) {
    _ui.update { it.copy(invertReward = value) }
    persistUserSettings()
  }

  fun setNoiseColor(value: NoiseColor) {
    _ui.update { it.copy(noiseColor = value) }
    noiseColorStore.save(value)
  }

  fun setGamma(value: Float) {
    _ui.update { it.copy(gamma = value) }
    persistUserSettings()
  }

  fun setGMinDb(value: Int) {
    _ui.update { it.copy(gMinDb = min(value, it.gMaxDb - 1)) }
    persistUserSettings()
  }

  fun setGMaxDb(value: Int) {
    _ui.update { it.copy(gMaxDb = max(value, it.gMinDb + 1)) }
    persistUserSettings()
  }

  /** Takes effect from the next session. */
  fun setRecordingEnabled(value: Boolean) {
    _ui.update { it.copy(recordingEnabled = value) }
    persistUserSettings()
  }

  /** Turning developer options off also leaves debug replay and any capture in progress. */
  fun setDeveloperOptionsEnabled(value: Boolean) = synchronized(lock) {
    if (!value) {
      cancelDebugRawLoopCaptureLocked()
      if (_ui.value.debugRawLoopEnabled && !_ui.value.sessionRunning) setDebugRawLoopEnabled(false)
    }
    _ui.update { it.copy(developerOptionsEnabled = value) }
    persistUserSettings()
  }

  fun setThemeMode(value: ThemeMode) {
    _ui.update { it.copy(themeMode = value) }
    persistUserSettings()
  }

  /**
   * Zips the most recent recorded session for sharing, off the main thread. Returns null if there is
   * none or zipping failed. The UI disables sharing while a session is being recorded, so a
   * half-written session is never offered.
   */
  suspend fun exportLatestSession(): File? = withContext(Dispatchers.IO) {
    val dir = latestSessionDir(ctx) ?: return@withContext null
    try {
      zipSessionForSharing(ctx, dir)
    } catch (e: Exception) {
      Log.e(TAG, "Could not export session ${dir.name}", e)
      null
    }
  }

  fun startDebugRawLoopCapture() = synchronized(lock) {
    if (_ui.value.debugRawLoopEnabled || _ui.value.debugRawLoopCapturing) return@synchronized
    if (!_ui.value.connected || !_ui.value.eegStreamReady) return@synchronized
    debugRawLoopCapture.reset()
    _ui.update {
      it.copy(
        debugRawLoopCapturing = true,
        debugRawLoopCapturedSeconds = 0,
      )
    }
  }

  fun cancelDebugRawLoopCapture() = synchronized(lock) { cancelDebugRawLoopCaptureLocked() }

  private fun cancelDebugRawLoopCaptureLocked() {
    if (!_ui.value.debugRawLoopCapturing) return
    debugRawLoopCapture.reset()
    _ui.update {
      it.copy(
        debugRawLoopCapturing = false,
        debugRawLoopCapturedSeconds = 0,
      )
    }
  }

  fun setDebugRawLoopEnabled(value: Boolean) = synchronized(lock) {
    if (_ui.value.sessionRunning || value == _ui.value.debugRawLoopEnabled) return@synchronized

    if (value) {
      val loopRecord = debugRawLoopRecord ?: return@synchronized
      cancelDebugRawLoopCaptureLocked()
      if (_ui.value.connected || _ui.value.headsetConnecting) {
        disconnect()
      }
      eegProcessor.reset()
      metricHistory.reset()
      refreshRawPreview()
      refreshMetricPlotSeries()
      resetStreamTracking()
      _ui.update {
        it.copy(
          debugRawLoopEnabled = true,
          poorSignal = loopRecord.metadata.poorSignal,
          attention = loopRecord.metadata.attention,
          meditation = loopRecord.metadata.meditation,
          eegStreamStatus = EegStreamStatus.WAITING_FOR_RAW,
          eegStreamReady = false,
          samplesPerSecond = 0f,
          streamStallMs = 0L,
          rawPlotOffsetSeconds = 0,
          metricPlotOffsetSeconds = 0,
        )
      }
      startDebugRawReplay(loopRecord.samples)
      return@synchronized
    }

    stopDebugRawReplay()
    resetStreamTracking()
    _ui.update {
      it.copy(
        debugRawLoopEnabled = false,
        poorSignal = if (it.connected) it.poorSignal else 255,
        attention = if (it.connected) it.attention else 0,
        meditation = if (it.connected) it.meditation else 0,
        eegStreamStatus = EegStreamStatus.DISCONNECTED,
        eegStreamReady = false,
        samplesPerSecond = 0f,
        streamStallMs = 0L,
        rawPlotOffsetSeconds = 0,
        metricPlotOffsetSeconds = 0,
      )
    }
    refreshRawPreview()
    refreshMetricPlotSeries()
  }

  fun panRawPlotBy(deltaSeconds: Int) = synchronized(lock) {
    if (!canPanPlots()) return@synchronized
    val current = _ui.value.rawPlotOffsetSeconds
    val maxOffset = eegProcessor.maxRawHistoryOffsetSeconds(_ui.value.plotSettings.getValue(PlotType.RAW).windowSeconds)
    _ui.update { it.copy(rawPlotOffsetSeconds = (current + deltaSeconds).coerceIn(0, maxOffset)) }
    refreshRawPreview()
  }

  fun panMetricPlotBy(deltaSeconds: Int) = synchronized(lock) {
    if (!canPanPlots()) return@synchronized
    val windowSeconds = _ui.value.plotSettings.getValue(PlotType.MEDITATION_PROXY).windowSeconds
    val maxOffset = _ui.value.visibleMetrics.maxOfOrNull { metricHistory.maxOffsetSeconds(it, windowSeconds) } ?: 0
    _ui.update { it.copy(metricPlotOffsetSeconds = (it.metricPlotOffsetSeconds + deltaSeconds).coerceIn(0, maxOffset)) }
    refreshMetricPlotSeries()
  }

  fun resetRawPlotToLatest() = synchronized(lock) {
    _ui.update { it.copy(rawPlotOffsetSeconds = 0) }
    refreshRawPreview()
  }

  fun resetMetricPlotToLatest() = synchronized(lock) {
    _ui.update { it.copy(metricPlotOffsetSeconds = 0) }
    refreshMetricPlotSeries()
  }

  fun setNotch50Enabled(value: Boolean) = synchronized(lock) {
    eegProcessor.setNotchEnabled(value)
    _ui.update { it.copy(notch50Enabled = value) }
    persistUserSettings()
  }

  fun setMetricWindowSeconds(seconds: Int) = synchronized(lock) {
    val normalized = METRIC_WINDOW_OPTIONS.minBy { option -> kotlin.math.abs(option - seconds) }
    val updated = _ui.value.plotSettings.mapValues { (type, settings) ->
      if (type == PlotType.RAW) settings else settings.copy(windowSeconds = normalized)
    }
    _ui.update { it.copy(plotSettings = updated) }
    for ((type, settings) in updated) {
      plotSettingsStore.save(type, settings)
    }
    refreshMetricPlotSeries()
  }

  fun startArtefactCalibration() = synchronized(lock) {
    if (!_ui.value.sessionRunning || _ui.value.calibrating) return@synchronized
    calibration.resetArtefactCapture()
    artefactPromptIndex = 0
    artefactPromptStartMs = nowMs()
    _ui.update {
      it.copy(
        artefactCalibrationState = ArtefactCalibrationUiState(
          available = true,
          running = true,
          completed = false,
          skipped = false,
          prompt = artefactPromptOrder.first(),
          promptLabel = artefactPromptLabel(artefactPromptOrder.first()),
          remainingSec = ARTEFACT_PROMPT_SECONDS,
          completedPrompts = 0,
        ),
        selectedMetricInfo = PlotType.ARTEFACT_SCORE,
      )
    }
    syncFeedbackAudioState()
  }

  fun dismissArtefactCalibrationOffer() {
    _ui.update {
      it.copy(
        artefactCalibrationState = it.artefactCalibrationState.copy(
          available = true,
          dismissed = true,
        )
      )
    }
  }

  fun skipArtefactCalibration() = synchronized(lock) {
    _ui.update {
      it.copy(
        artefactCalibrationState = it.artefactCalibrationState.copy(
          available = false,
          dismissed = false,
          skipped = true,
          running = false,
          prompt = null,
          promptLabel = "",
          remainingSec = 0,
        )
      )
    }
    syncFeedbackAudioState(fadeIn = true)
  }

  fun skipCalibration() = synchronized(lock) {
    if (!_ui.value.sessionRunning || !_ui.value.calibrating) return@synchronized
    finishCleanCalibration(now = nowMs(), skipped = true)
  }

  fun testBeep() {
    viewModelScope.launch(Dispatchers.Default) {
      try {
        val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        try {
          tg.startTone(ToneGenerator.TONE_PROP_BEEP, 220)
          delay(250)
        } finally {
          tg.release()
        }
      } catch (_: Throwable) {
      }
    }
  }

  private fun startRecorderOrNull(): SessionRecorder? {
    return try {
      SessionRecorder(ctx).apply {
        start(
          sampleRateHz = rawSampleRateHz,
          metadata = sessionStartMetadata(
            appVersion = appVersionName(ctx),
            startedAtWallMs = System.currentTimeMillis(),
            state = _ui.value,
          ),
        )
      }
    } catch (e: Exception) {
      Log.e(TAG, "Could not start session recording", e)
      null
    }
  }

  /** Called with [lock] held. */
  private fun appendDebugRawLoopCapture(raw: Int) {
    if (!_ui.value.debugRawLoopCapturing || debugRawLoopCapture.isFull) return
    debugRawLoopCapture.addSample(raw)

    val capturedSeconds = (debugRawLoopCapture.samplesCaptured / rawSampleRateHz)
      .coerceIn(0, DEBUG_RAW_LOOP_DURATION_SECONDS)
    if (capturedSeconds != _ui.value.debugRawLoopCapturedSeconds) {
      _ui.update { it.copy(debugRawLoopCapturedSeconds = capturedSeconds) }
    }
    if (!debugRawLoopCapture.isFull) return

    val savedRecord = debugRawLoopCapture.buildRecord(
      fallback = DebugRawLoopMetadata(
        poorSignal = _ui.value.poorSignal,
        attention = _ui.value.attention,
        meditation = _ui.value.meditation,
      )
    )
    try {
      debugRawLoopStore.save(savedRecord)
    } catch (e: Exception) {
      // Keep the loop for this run even if it can't be written; don't leave the capture stuck.
      Log.e(TAG, "Could not save the debug raw loop", e)
    }
    debugRawLoopRecord = savedRecord
    _ui.update {
      it.copy(
        debugRawLoopAvailable = true,
        debugRawLoopCapturing = false,
        debugRawLoopCapturedSeconds = DEBUG_RAW_LOOP_DURATION_SECONDS,
      )
    }
  }

  private fun startDebugRawReplay(samples: IntArray) {
    stopDebugRawReplay()
    debugRawReplayJob = viewModelScope.launch(Dispatchers.Default) {
      // Paced by the clock, not by a fixed chunk per delay: timer overshoot would otherwise drop
      // the replay below 512 samples/s and fail the stream's sample-rate check.
      var baseMs = nowMs()
      var emitted = 0L
      var sampleIndex = 0
      while (_ui.value.debugRawLoopEnabled) {
        synchronized(lock) {
          var due = (nowMs() - baseMs) * rawSampleRateHz / 1000L - emitted
          if (due > rawSampleRateHz) {
            // Fell far behind (e.g. the process was frozen): skip ahead instead of bursting.
            baseMs = nowMs()
            emitted = 0L
            due = 0L
          }
          repeat(due.toInt()) {
            if (!_ui.value.debugRawLoopEnabled) return@synchronized
            handleRawSample(
              raw = samples[sampleIndex],
              source = RawSignalSource.DEBUG_REPLAY,
              receivedAtMs = nowMs(),
              sampleTimestampMs = baseMs + emitted * 1000L / rawSampleRateHz,
            )
            sampleIndex = (sampleIndex + 1) % samples.size
            emitted++
          }
        }
        delay(DEBUG_RAW_LOOP_TICK_MS)
      }
    }
  }

  private fun stopDebugRawReplay() {
    debugRawReplayJob?.cancel()
    debugRawReplayJob = null
  }

  /** Called with [lock] held. */
  private fun handleThinkGearData(data: ThinkGearData) {
    when (data) {
      is ThinkGearData.Battery -> _ui.update { it.copy(batteryPercent = data.value.coerceIn(0, 100)) }
      is ThinkGearData.PoorSignal -> {
        if (_ui.value.debugRawLoopCapturing) debugRawLoopCapture.addPoorSignal(data.value)
        _ui.update { it.copy(poorSignal = data.value) }
      }
      is ThinkGearData.Attention -> {
        if (_ui.value.debugRawLoopCapturing) debugRawLoopCapture.addAttention(data.value)
        _ui.update { it.copy(attention = data.value) }
      }
      is ThinkGearData.Meditation -> {
        if (_ui.value.debugRawLoopCapturing) debugRawLoopCapture.addMeditation(data.value)
        _ui.update { it.copy(meditation = data.value) }
      }
      is ThinkGearData.RawSample -> handleRawSample(data.value, source = RawSignalSource.LIVE)
      is ThinkGearData.PacketLoss -> {
        // Placeholders keep the analysis window in real time and mark the loss as a signal gap.
        // More than a window's worth would only overwrite itself.
        repeat(data.lostSamples.coerceAtMost(rawSampleRateHz * ANALYSIS_WINDOW_SECONDS)) {
          handleRawSample(lastRawValue, source = RawSignalSource.LIVE, lost = true)
        }
      }
      else -> Unit
    }
  }

  /**
   * Called with [lock] held. [lost] marks a placeholder for a sample the parser reported lost: it
   * holds the last value, is written to the recording so the raw file keeps real time, but doesn't
   * count as received for the sample-rate check.
   */
  private fun handleRawSample(
    raw: Int,
    source: RawSignalSource,
    receivedAtMs: Long = nowMs(),
    sampleTimestampMs: Long = receivedAtMs,
    lost: Boolean = false,
  ) {
    val now = receivedAtMs

    if (!lost) {
      // After a stall, measure the rate afresh rather than averaging the stall into it.
      if (lastRawSampleAtMs == 0L || now - lastRawSampleAtMs > EEG_STREAM_STALE_MS) {
        rawCountThisSecond = 0
        lastRateTickMs = now
        lastMeasuredSamplesPerSecond = 0f
      }
      lastRawSampleAtMs = now
      lastRawValue = raw

      rawCountThisSecond++
      if (now - lastRateTickMs >= EEG_STREAM_RATE_WINDOW_MS) {
        lastMeasuredSamplesPerSecond = rawCountThisSecond * 1000f / max(1L, now - lastRateTickMs).toFloat()
        rawCountThisSecond = 0
        lastRateTickMs = now
      }
    }

    if (source == RawSignalSource.LIVE) {
      appendDebugRawLoopCapture(raw)
    }

    writeRecorderSafely { it.appendRaw(raw.toShort()) }

    if (!lost && _ui.value.sessionRunning && _ui.value.calibrating) addCalibrationRaw(raw)

    val features = if (lost) {
      eegProcessor.pushLost(sampleTimestampMs)
    } else {
      eegProcessor.pushRaw(raw, sampleTimestampMs)
    }
    // The preview list is rebuilt by the preview ticker, off the Bluetooth reader thread.
    rawPreviewDirty = true

    if (features == null) return
    if (!_ui.value.sessionRunning || _ui.value.sessionPaused) return

    val poorSignal = _ui.value.poorSignal
    val quality = scorer.quality(poorSignal = poorSignal, features = features)

    if (_ui.value.calibrating) {
      calibration.addSample(features = features, quality = quality)
      return
    }

    if (_ui.value.artefactCalibrationState.running) {
      _ui.value.artefactCalibrationState.prompt?.let { prompt ->
        calibration.addArtefactSample(prompt = prompt, features = features)
      }
      return
    }

    val output = scorer.classify(poorSignal = poorSignal, features = features)
    calibration.addAdaptiveSample(features = features, quality = output.quality)
    maybeUpdateAdaptiveCalibration(now)

    val smoothed = outputSmoother.update(output)
    sessionStats.add(smoothed.meditationProxy, atMs = now)
    metricHistory.add(smoothed.metricHistorySample(_ui.value.meditation, _ui.value.attention))

    val feedback = smoothed.feedbackValue(_ui.value.feedbackMetric)
    val audible = shouldNoiseAudioBeAudible()
    if (audible) {
      ensureSessionAudioEngine()
      feedbackAudio.update(feedbackValue = feedback, settings = _ui.value.feedbackAudioSettings())
    }
    feedbackAudio.setMuted(!audible)

    writeRecorderSafely {
      it.appendFeatures(
        recordedFeatureRow(
          features = features,
          output = output,
          smoothed = smoothed,
          atMs = now,
          wallClockOffsetMs = System.currentTimeMillis() - SystemClock.elapsedRealtime(),
          poorSignal = poorSignal,
          state = _ui.value,
          artefactProfile = artefactCalibrationProfile,
          feedbackValue = feedback,
        )
      )
    }

    _ui.update {
      it.withWindowScore(
        output = output,
        smoothed = smoothed,
        stats = sessionStats,
        artefactProfile = artefactCalibrationProfile,
        audioAudible = audible,
        audioBaseDb = feedbackAudio.baseDb,
      )
    }

    refreshMetricPlotSeries()
  }

  /**
   * A storage failure (disk full, card removed) stops recording for this session instead of
   * propagating into the Bluetooth reader and looking like a headset disconnect.
   */
  private inline fun writeRecorderSafely(block: (SessionRecorder) -> Unit) {
    val active = recorder ?: return
    try {
      block(active)
    } catch (e: Exception) {
      Log.e(TAG, "Session recording failed; recording stopped", e)
      active.stop()
      recorder = null
      _ui.update { it.copy(recordingActive = false, recordedSessionAvailable = true) }
    }
  }

  /** Called with [lock] held. */
  private fun finishCleanCalibration(now: Long, skipped: Boolean = false) {
    val baseline = calibration.finish(_ui.value.sessionMode)
    sessionBaseline = baseline
    writeRecorderSafely { it.appendMeta(baselineMetadata(baseline, skipped = skipped)) }
    if (baseline != null) {
      scorer.setCalibration(baseline)
    }
    // With no baseline (early skip, or too few windows in the half matching the session mode) the
    // scorer stays uncalibrated, which yields neutral z-scores instead of the saturated ones a tiny
    // baseline would produce. The adaptive window supplies a baseline once it holds enough clean
    // session windows.
    lastAdaptiveCalibrationUpdateMs = now
    applyCalibratedRawRangeIfNeeded()

    _ui.update {
      it.copy(
        calibrating = false,
        calibrationPhase = CalibrationPhase.BASELINE_COMPLETE,
        calibrationInstruction = calibrationInstructionFor(CalibrationPhase.BASELINE_COMPLETE),
        calibrationRemainingSec = 0,
        artefactCalibrationState = ArtefactCalibrationUiState(available = true),
        baselineProvisional = baseline == null,
      )
    }

    if (!skipped && _ui.value.audioEnabled) {
      BellSoundPlayer.playCalibrationComplete()
    }
    syncFeedbackAudioState(fadeIn = true)
  }

  private fun updateArtefactCaptureUi(now: Long, elapsedSec: Int) {
    val elapsedPromptMs = now - artefactPromptStartMs
    if (elapsedPromptMs >= ARTEFACT_PROMPT_SECONDS * 1000L) {
      artefactPromptIndex++
      if (artefactPromptIndex >= artefactPromptOrder.size) {
        artefactCalibrationProfile = calibration.buildArtefactCalibrationProfile()
        scorer.setArtefactCalibrationProfile(artefactCalibrationProfile)
        _ui.update {
          it.copy(
            sessionElapsedSec = elapsedSec,
            artefactCalibrationState = it.artefactCalibrationState.copy(
              available = false,
              dismissed = false,
              running = false,
              completed = true,
              prompt = null,
              promptLabel = "",
              remainingSec = 0,
              completedPrompts = artefactPromptOrder.size,
            ),
            artefactBlinkNormalizationHz = artefactCalibrationProfile.blinkNormalizationHz,
            artefactEmgNormalizationHfRatio = artefactCalibrationProfile.emgNormalizationHfRatio,
          )
        }
        syncFeedbackAudioState(fadeIn = true)
        return
      }
      artefactPromptStartMs = now
    }

    val prompt = artefactPromptOrder[artefactPromptIndex]
    val remainingSec = (ARTEFACT_PROMPT_SECONDS - (elapsedPromptMs / 1000L).toInt()).coerceIn(1, ARTEFACT_PROMPT_SECONDS)
    _ui.update {
      it.copy(
        sessionElapsedSec = elapsedSec,
        artefactCalibrationState = it.artefactCalibrationState.copy(
          available = true,
          dismissed = false,
          running = true,
          completed = false,
          prompt = prompt,
          promptLabel = artefactPromptLabel(prompt),
          remainingSec = remainingSec,
          completedPrompts = artefactPromptIndex,
        )
      )
    }
  }

  private fun maybeUpdateAdaptiveCalibration(now: Long) {
    if (now - lastAdaptiveCalibrationUpdateMs < 1000L) return
    lastAdaptiveCalibrationUpdateMs = now
    val adaptive = calibration.buildAdaptiveCalibration(prior = sessionBaseline) ?: return
    scorer.setCalibration(adaptive)
    if (_ui.value.baselineProvisional) {
      _ui.update { it.copy(baselineProvisional = false) }
    }
  }


  private fun rewardSelectableMetrics(): Set<PlotType> = MetricGlossary.feedbackSourceMetrics().toSet()

  private fun startStreamMonitor() {
    streamMonitorJob?.cancel()
    streamMonitorJob = viewModelScope.launch(Dispatchers.Default) {
      while (true) {
        synchronized(lock) {
          val snapshot = buildEegStreamSnapshot(
            connected = _ui.value.connected,
            debugReplayEnabled = _ui.value.debugRawLoopEnabled,
            nowMs = nowMs(),
            lastRawSampleAtMs = lastRawSampleAtMs,
            lastMeasuredSamplesPerSecond = lastMeasuredSamplesPerSecond,
            rawCountThisSecond = rawCountThisSecond,
            lastRateTickMs = lastRateTickMs,
          )
          _ui.update {
            it.copy(
              eegStreamStatus = snapshot.status,
              eegStreamReady = snapshot.ready,
              samplesPerSecond = snapshot.samplesPerSecond,
              streamStallMs = snapshot.stallMs,
            )
          }
        }
        delay(250L)
      }
    }
  }

  /** Rebuilds the raw-EEG preview at ~16 Hz when new samples arrived, off the Bluetooth thread. */
  private fun startRawPreviewTicker() {
    rawPreviewJob?.cancel()
    rawPreviewJob = viewModelScope.launch(Dispatchers.Default) {
      while (true) {
        synchronized(lock) {
          // Skip the work while nothing on screen collects the preview (app in background, other
          // tab). The dirty flag stays set, so it refreshes as soon as the plot is visible again.
          val visible = _rawPreview.subscriptionCount.value > 0
          if (visible && rawPreviewDirty && !_ui.value.sessionPaused && _ui.value.rawPlotOffsetSeconds == 0) {
            rawPreviewDirty = false
            refreshRawPreview()
          }
        }
        delay(RAW_PREVIEW_REFRESH_MS)
      }
    }
  }

  private fun resetStreamTracking() {
    rawCountThisSecond = 0
    lastRateTickMs = 0L
    lastMeasuredSamplesPerSecond = 0f
    lastRawSampleAtMs = 0L
  }

  private fun shouldNoiseAudioBeAudible(): Boolean {
    return _ui.value.audioEnabled &&
      _ui.value.sessionRunning &&
      !_ui.value.sessionPaused &&
      !_ui.value.calibrating &&
      !_ui.value.artefactCalibrationState.running
  }

  /** Called with [lock] held, so the engine can never be created twice or after the session stopped. */
  private fun ensureSessionAudioEngine(fadeIn: Boolean = false) {
    if (!_ui.value.audioEnabled || !_ui.value.sessionRunning) return
    feedbackAudio.ensureStarted(_ui.value.feedbackAudioSettings(), fadeIn)
  }

  private fun shutdownAudioEngine() = feedbackAudio.shutdown()

  private fun syncFeedbackAudioState(fadeIn: Boolean = false) {
    if (!_ui.value.audioEnabled || !_ui.value.sessionRunning) {
      shutdownAudioEngine()
      _ui.update { it.copy(audioRunning = false, audioMuted = true) }
      return
    }

    val shouldNoiseRun = shouldNoiseAudioBeAudible()
    ensureSessionAudioEngine(fadeIn = fadeIn)
    feedbackAudio.setMuted(!shouldNoiseRun)
    _ui.update { it.copy(audioRunning = shouldNoiseRun, audioMuted = !shouldNoiseRun) }
  }

  private fun updatePlotSettings(
    type: PlotType,
    newSettings: PlotSettings,
    persist: Boolean,
  ) {
    _ui.update { state ->
      state.copy(
        plotSettings = state.plotSettings + (type to newSettings)
      )
    }
    if (persist) {
      plotSettingsStore.save(type, newSettings)
    }
  }

  private fun refreshRawPreview() {
    val setting = _ui.value.plotSettings.getValue(PlotType.RAW)
    val maxOffset = eegProcessor.maxRawHistoryOffsetSeconds(setting.windowSeconds)
    val clampedOffset = _ui.value.rawPlotOffsetSeconds.coerceIn(0, maxOffset)
    val values = if (clampedOffset == 0) {
      val sampleCount = (setting.windowSeconds * rawSampleRateHz).coerceIn(64, rawSampleRateHz * 20)
      eegProcessor.rawPreview(sampleCount)
    } else {
      val historyRate = eegProcessor.rawHistoryRateHz()
      val sampleCount = (setting.windowSeconds * historyRate).coerceAtLeast(24)
      eegProcessor.rawHistory(sampleCount, clampedOffset * historyRate)
    }
    _rawPreview.value = PlotMath.minMaxDecimate(values, RAW_PREVIEW_MAX_POINTS)
    if (_ui.value.rawPlotOffsetSeconds != clampedOffset) {
      _ui.update { it.copy(rawPlotOffsetSeconds = clampedOffset) }
    }
  }

  private fun refreshMetricPlotSeries() {
    val windowSeconds = _ui.value.plotSettings.getValue(PlotType.MEDITATION_PROXY).windowSeconds
    val maxOffset = _ui.value.visibleMetrics.maxOfOrNull { metricHistory.maxOffsetSeconds(it, windowSeconds) } ?: 0
    val clampedOffset = _ui.value.metricPlotOffsetSeconds.coerceIn(0, maxOffset)
    val series = _ui.value.visibleMetrics.associateWith { type ->
      convertSeriesForDisplay(type, metricHistory.series(type, windowSeconds, clampedOffset))
    }
    _ui.update {
      it.copy(
        metricPlotSeries = series,
        metricPlotOffsetSeconds = clampedOffset,
      )
    }
  }

  private fun convertSeriesForDisplay(type: PlotType, values: List<Float>): List<Float> {
    return when (type) {
      PlotType.ESENSE_MEDITATION,
      PlotType.ESENSE_ATTENTION -> values.map { it.coerceIn(0f, 100f) }
      else -> values.map { (it * 100f).coerceIn(0f, 100f) }
    }
  }

  private fun resetRawCalibrationStats() {
    calibrationRawCount = 0
    calibrationRawMean = 0.0
    calibrationRawM2 = 0.0
  }

  private fun addCalibrationRaw(sample: Int) {
    calibrationRawCount++
    val delta = sample - calibrationRawMean
    calibrationRawMean += delta / calibrationRawCount.toDouble()
    val delta2 = sample - calibrationRawMean
    calibrationRawM2 += delta * delta2
  }

  private fun applyCalibratedRawRangeIfNeeded() {
    val current = _ui.value.plotSettings.getValue(PlotType.RAW)
    if (current.isUserLocked) return
    val variance = if (calibrationRawCount > 1) calibrationRawM2 / (calibrationRawCount - 1) else 0.0
    val sd = sqrt(variance.coerceAtLeast(1.0))
    val (yMin, yMax) = PlotMath.calibratedRawRangeFromSd(sd)
    updatePlotSettings(
      type = PlotType.RAW,
      newSettings = current.copy(yMin = yMin, yMax = yMax, isUserLocked = false),
      persist = true,
    )
    refreshRawPreview()
  }

  private fun calibrationInstructionFor(phase: CalibrationPhase, remainingSeconds: Int = 0): String {
    return when (phase) {
      CalibrationPhase.EYES_OPEN -> {
        val countdown = calibrationEyesClosedCountdownValue(remainingSeconds)
        if (countdown != null) {
          ctx.getString(R.string.calibration_phase_eyes_open_countdown, countdown)
        } else {
          ctx.getString(R.string.calibration_phase_eyes_open)
        }
      }

      CalibrationPhase.EYES_CLOSED -> {
        if (remainingSeconds == CALIBRATION_SECONDS / 2) {
          ctx.getString(R.string.calibration_phase_eyes_closed_now)
        } else {
          ctx.getString(R.string.calibration_phase_eyes_closed)
        }
      }
      CalibrationPhase.BASELINE_COMPLETE -> ctx.getString(R.string.calibration_phase_complete)
    }
  }

  private fun artefactPromptLabel(prompt: ArtefactPrompt): String {
    return when (prompt) {
      ArtefactPrompt.LOOK_LEFT_RIGHT -> ctx.getString(R.string.artifact_prompt_look_left_right)
      ArtefactPrompt.LOOK_UP_DOWN -> ctx.getString(R.string.artifact_prompt_look_up_down)
      ArtefactPrompt.JAW_CLENCH -> ctx.getString(R.string.artifact_prompt_jaw_clench)
      ArtefactPrompt.FROWN -> ctx.getString(R.string.artifact_prompt_frown)
      ArtefactPrompt.RELAX -> ctx.getString(R.string.artifact_prompt_relax)
    }
  }

  private fun canPanPlots(): Boolean {
    return !_ui.value.sessionRunning || _ui.value.sessionPaused
  }

  override fun onCleared() {
    sessionJob?.cancel()
    streamMonitorJob?.cancel()
    rawPreviewJob?.cancel()
    debugRawReplayJob?.cancel()
    synchronized(lock) {
      shutdownAudioEngine()
      recorder?.stop()
      recorder = null
      client?.close()
      client = null
    }
    SessionKeepAliveService.stop(ctx)
  }

  private companion object {
    const val TAG = "SamathaScope"
  }
}

internal fun calibrationEyesClosedCountdownValue(remainingSeconds: Int): Int? {
  // Counts down the last 10 seconds of the eyes-open half.
  val half = CALIBRATION_SECONDS / 2
  return if (remainingSeconds in (half + 1)..(half + 10)) remainingSeconds - half else null
}

internal val METRIC_WINDOW_OPTIONS = listOf(60, 180, 300, 600)

private const val RAW_PREVIEW_REFRESH_MS = 62L
// Enough for a phone-width plot; min/max pairs keep spikes visible after decimation.
private const val RAW_PREVIEW_MAX_POINTS = 800
private const val DEBUG_RAW_LOOP_TICK_MS = 20L

private enum class RawSignalSource {
  LIVE,
  DEBUG_REPLAY,
}
