package com.mordin.samathascope

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.LinkedHashSet
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class MainViewModel(app: Application) : AndroidViewModel(app) {

  private val ctx = app.applicationContext
  private val plotSettingsStore = createPlotSettingsStore(ctx)

  private val _ui = MutableStateFlow(
    UiState(
      plotSettings = plotSettingsStore.load(),
    )
  )
  val ui: StateFlow<UiState> = _ui

  private val btAdapter: BluetoothAdapter? by lazy {
    val mgr = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    mgr.adapter
  }

  private var client: BluetoothMindWaveClient? = null

  private val rawSampleRateHz = 512
  private val eegProcessor = EegProcessor(sampleRateHz = rawSampleRateHz)
  private val calibration = CalibrationManager(calibrationSeconds = 60)
  private val scorer = ScoreModel()
  private val metricHistory = MetricHistory(maxSeconds = 600, pointsPerSecond = 1)

  private val contaminatedProbabilitySmoother = ExponentialSmoother(alpha = 0.3f)
  private val drowsyProbabilitySmoother = ExponentialSmoother(alpha = 0.3f)
  private val displayedDrowsySmoother = ExponentialSmoother(alpha = 0.15f)
  private val settledProbabilitySmoother = ExponentialSmoother(alpha = 0.3f)
  private val effortfulFocusProbabilitySmoother = ExponentialSmoother(alpha = 0.3f)
  private val mindWanderingProbabilitySmoother = ExponentialSmoother(alpha = 0.3f)
  private val uncertainProbabilitySmoother = ExponentialSmoother(alpha = 0.3f)
  private val alertnessSmoother = ExponentialSmoother(alpha = 0.3f)
  private val controlSmoother = ExponentialSmoother(alpha = 0.3f)
  private val settlednessSmoother = ExponentialSmoother(alpha = 0.3f)
  private val artefactSmoother = ExponentialSmoother(alpha = 0.3f)
  private val qualityConfidenceSmoother = ExponentialSmoother(alpha = 0.3f)
  private val meditationProxySmoother = ExponentialSmoother(alpha = 0.3f)
  private val stateHoldSmoother = StateHoldSmoother()

  private val artefactPromptOrder = listOf(
    ArtefactPrompt.LOOK_LEFT_RIGHT,
    ArtefactPrompt.LOOK_UP_DOWN,
    ArtefactPrompt.JAW_CLENCH,
    ArtefactPrompt.FROWN,
    ArtefactPrompt.RELAX,
  )

  private var statsSumMeditationProxy = 0f
  private var statsCount = 0
  private var timeMeditationProxyOver80Ms = 0L
  private var lastFeatureTsMs = 0L

  private var sessionStartMs: Long = 0L
  private var pausedAtMs: Long = 0L
  private var pausedAccumMs: Long = 0L
  private var sessionJob: Job? = null
  private var gameLoopJob: Job? = null

  private var audio: NoiseAudioEngine? = null
  private var gameAudio: GameSoundEngine? = null
  private var recorder: SessionRecorder? = null
  private val gameSignalMapper = GameSignalMapper()
  private val pendingGameEvents = ConcurrentLinkedQueue<GameEvent>()

  private var rawCountThisSecond = 0
  private var lastRateTickMs: Long = 0L
  private var rawPreviewDecim = 0

  private var lastGameUpdateMs = 0L
  private var lastAdaptiveCalibrationUpdateMs = 0L
  private var calibrationRawCount = 0
  private var calibrationRawMean = 0.0
  private var calibrationRawM2 = 0.0
  private var artefactPromptIndex = 0
  private var artefactPromptStartMs = 0L
  private var artefactCalibrationProfile = ArtefactCalibrationProfile()
  private var drowsyDisplayWins = 0

  init {
    eegProcessor.setNotchEnabled(_ui.value.notch50Enabled)
    scorer.setArtefactCalibrationProfile(artefactCalibrationProfile)
    if (Build.VERSION.SDK_INT < 31) {
      _ui.update { it.copy(btPermissionGranted = true) }
      refreshBondedDevices()
    }
    refreshRawPreview()
    refreshMetricPlotSeries()
    refreshSelectedGameUi()
  }

  fun onPermissionsResult(result: Map<String, Boolean>) {
    val ok = if (Build.VERSION.SDK_INT >= 31) result.values.all { it } else true
    _ui.update { it.copy(btPermissionGranted = ok) }
    if (ok) refreshBondedDevices()
  }

  fun refreshBondedDevices() {
    val adapter = btAdapter ?: return
    if (!_ui.value.btPermissionGranted && Build.VERSION.SDK_INT >= 31) return

    val bonded = adapter.bondedDevices?.map {
      BondedDevice(mac = it.address, display = "${it.name ?: "Unknown"} (${it.address})")
    } ?: emptyList()

    _ui.update { state ->
      state.copy(
        bondedDevices = bonded,
        selectedDeviceMac = state.selectedDeviceMac ?: bonded.firstOrNull()?.mac,
      )
    }
  }

  fun selectDevice(mac: String) {
    _ui.update { it.copy(selectedDeviceMac = mac) }
  }

  fun selectTab(tab: AppTab) {
    _ui.update { it.copy(selectedTab = tab) }
    syncFeedbackAudioState()
  }

  fun selectGame(gameId: GameId) {
    pendingGameEvents.clear()
    val controller = GameRegistry.controllerFor(gameId)
    val signals = currentGameSignals(System.currentTimeMillis())
    val runtimeState = controller.initialState()
    _ui.update {
      it.copy(
        selectedGameId = gameId,
        gameRunning = false,
        gameRuntimeState = runtimeState,
        gameHudState = controller.hud(runtimeState, signals),
        gameAudioState = controller.audio(runtimeState, signals).copy(muted = true),
      )
    }
    refreshSelectedGameUi()
    syncFeedbackAudioState()
  }

  fun startGame() {
    if (!canStartGame()) return
    pendingGameEvents.clear()
    lastGameUpdateMs = 0L
    val controller = GameRegistry.controllerFor(_ui.value.selectedGameId)
    val signals = currentGameSignals(System.currentTimeMillis())
    val runtimeState = controller.initialState()
    _ui.update {
      it.copy(
        gameRunning = true,
        gameRuntimeState = runtimeState,
        gameAudioState = controller.audio(runtimeState, signals).copy(muted = !shouldGameAudioBeAudible()),
      )
    }
    refreshSelectedGameUi()
    gameAudio?.update(_ui.value.selectedGameId, _ui.value.gameAudioState)
    syncFeedbackAudioState()
  }

  fun onGameTap() {
    if (!shouldEnableGameInput(_ui.value.selectedGameId)) return
    pendingGameEvents.add(GameEvent.Tap)
  }

  fun connect() {
    val mac = _ui.value.selectedDeviceMac ?: return
    val adapter = btAdapter ?: return
    val device = adapter.getRemoteDevice(mac)

    disconnect()

    client = BluetoothMindWaveClient(device)
    _ui.update { it.copy(connected = false, streamStallMs = 0, samplesPerSecond = 0f) }

    client?.connect(
      onConnected = { _ui.update { it.copy(connected = true) } },
      onDisconnected = { _ ->
        _ui.update { it.copy(connected = false) }
        stopSession()
      },
      onData = { data -> handleThinkGearData(data) }
    )
  }

  fun disconnect() {
    stopSession()
    client?.close()
    client = null
    _ui.update { it.copy(connected = false) }
  }

  fun startSession() {
    if (_ui.value.sessionRunning || !_ui.value.connected) return

    eegProcessor.reset()
    eegProcessor.setNotchEnabled(_ui.value.notch50Enabled)
    calibration.reset()
    scorer.reset()
    artefactCalibrationProfile = ArtefactCalibrationProfile()
    scorer.setArtefactCalibrationProfile(artefactCalibrationProfile)
    metricHistory.reset()
    resetRawCalibrationStats()
    resetSmoothers()
    gameSignalMapper.reset()
    pendingGameEvents.clear()
    calibration.resetArtefactCapture()

    statsSumMeditationProxy = 0f
    statsCount = 0
    timeMeditationProxyOver80Ms = 0L
    lastFeatureTsMs = 0L
    lastGameUpdateMs = 0L
    lastAdaptiveCalibrationUpdateMs = 0L
    artefactPromptIndex = 0
    artefactPromptStartMs = 0L
    val gameController = GameRegistry.controllerFor(_ui.value.selectedGameId)
    val initialGameSignals = currentGameSignals(System.currentTimeMillis())
    val initialRuntimeState = gameController.initialState()

    sessionStartMs = System.currentTimeMillis()
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

    if (_ui.value.recordingEnabled) {
      recorder = SessionRecorder(ctx).apply { start(sampleRateHz = rawSampleRateHz) }
      _ui.update { it.copy(lastRecordingPath = recorder?.sessionDir?.absolutePath) }
    } else {
      recorder = null
      _ui.update { it.copy(lastRecordingPath = null) }
    }

    _ui.update {
      it.copy(
        sessionRunning = true,
        sessionPaused = false,
        calibrating = true,
        calibrationPhase = CalibrationPhase.EYES_OPEN,
        calibrationInstruction = calibrationInstructionFor(CalibrationPhase.EYES_OPEN),
        calibrationRemainingSec = calibration.calibrationSeconds,
        artefactCalibrationState = ArtefactCalibrationUiState(),
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
        audioRunning = audio != null && it.audioEnabled,
        audioMuted = true,
        metricPlotSeries = emptyMap(),
        rawPlotOffsetSeconds = 0,
        metricPlotOffsetSeconds = 0,
        gameRunning = false,
        gameSignals = initialGameSignals,
        gameRuntimeState = initialRuntimeState,
        gameAudioState = gameController.audio(initialRuntimeState, initialGameSignals).copy(muted = true),
        gameHudState = gameController.hud(initialRuntimeState, initialGameSignals),
      )
    }

    refreshRawPreview()
    refreshMetricPlotSeries()
    refreshSelectedGameUi()
    ensureSessionAudioEngines(fadeIn = false)
    syncFeedbackAudioState(fadeIn = false)

    sessionJob = viewModelScope.launch(Dispatchers.Default) {
      while (_ui.value.sessionRunning) {
        val now = System.currentTimeMillis()
        val elapsedMs = now - sessionStartMs - pausedAccumMs - if (_ui.value.sessionPaused) (now - pausedAtMs) else 0L
        val elapsedSec = (elapsedMs / 1000L).toInt().coerceAtLeast(0)

        if (_ui.value.calibrating) {
          val rem = calibration.remainingSeconds()
          val phase = if (rem > 30) CalibrationPhase.EYES_OPEN else CalibrationPhase.EYES_CLOSED
          _ui.update {
            it.copy(
              calibrationRemainingSec = rem,
              calibrationPhase = phase,
              calibrationInstruction = calibrationInstructionFor(phase),
              sessionElapsedSec = elapsedSec,
            )
          }
          if (calibration.isDone()) {
            finishCleanCalibration(now)
          }
        } else if (_ui.value.artefactCalibrationState.running) {
          updateArtefactCaptureUi(now, elapsedSec)
        } else {
          _ui.update { it.copy(sessionElapsedSec = elapsedSec) }
        }

        delay(200)
      }
    }
    startGameLoop()
  }

  fun stopSession() {
    sessionJob?.cancel()
    sessionJob = null
    gameLoopJob?.cancel()
    gameLoopJob = null

    shutdownAudioEngines()

    recorder?.stop()
    recorder = null
    calibration.resetArtefactCapture()
    artefactPromptIndex = 0
    artefactPromptStartMs = 0L
    pendingGameEvents.clear()
    gameSignalMapper.reset()
    val gameController = GameRegistry.controllerFor(_ui.value.selectedGameId)
    val resetSignals = currentGameSignals(System.currentTimeMillis())
    val resetRuntimeState = gameController.initialState()

    _ui.update {
      it.copy(
        sessionRunning = false,
        sessionPaused = false,
        calibrating = false,
        calibrationPhase = null,
        calibrationInstruction = "",
        calibrationRemainingSec = 0,
        artefactCalibrationState = ArtefactCalibrationUiState(),
        sessionElapsedSec = 0,
        audioRunning = false,
        audioMuted = true,
        gameRunning = false,
        gameSignals = resetSignals,
        gameRuntimeState = resetRuntimeState,
        gameAudioState = gameController.audio(resetRuntimeState, resetSignals).copy(muted = true),
        gameHudState = gameController.hud(resetRuntimeState, resetSignals),
      )
    }
    refreshSelectedGameUi()
  }

  fun togglePause() {
    if (!_ui.value.sessionRunning) return
    val now = System.currentTimeMillis()
    if (!_ui.value.sessionPaused) {
      pausedAtMs = now
      _ui.update { it.copy(sessionPaused = true, audioMuted = true) }
      syncFeedbackAudioState()
    } else {
      pausedAccumMs += (now - pausedAtMs).coerceAtLeast(0L)
      pausedAtMs = 0L
      _ui.update { it.copy(sessionPaused = false, rawPlotOffsetSeconds = 0, metricPlotOffsetSeconds = 0) }
      refreshRawPreview()
      refreshMetricPlotSeries()
      syncFeedbackAudioState(fadeIn = true)
    }
    refreshSelectedGameUi()
  }

  fun toggleVisibleMetric(type: PlotType) {
    if (type == PlotType.RAW) return
    val next = LinkedHashSet(_ui.value.visibleMetrics)
    if (next.contains(type)) {
      if (next.size > 1) next.remove(type)
    } else {
      next.add(type)
    }
    _ui.update { it.copy(visibleMetrics = next, selectedMetricInfo = type) }
    refreshMetricPlotSeries()
  }

  fun focusMetricInfo(type: PlotType) {
    _ui.update { it.copy(selectedMetricInfo = type) }
  }

  fun setFeedbackMetric(value: PlotType) {
    if (!rewardSelectableMetrics().contains(value)) return
    _ui.update {
      it.copy(
        feedbackMetric = value,
        selectedMetricInfo = value,
      )
    }
    refreshMetricPlotSeries()
  }

  fun setAudioEnabled(value: Boolean) {
    _ui.update { it.copy(audioEnabled = value) }
    if (!value) {
      shutdownAudioEngines()
      _ui.update { it.copy(audioRunning = false, audioMuted = true) }
    } else {
      ensureSessionAudioEngines(fadeIn = true)
      syncFeedbackAudioState(fadeIn = true)
    }
  }

  fun setInvertReward(value: Boolean) = _ui.update { it.copy(invertReward = value) }

  fun setGamma(value: Float) = _ui.update { it.copy(gamma = value) }

  fun setGMinDb(value: Int) = _ui.update { it.copy(gMinDb = min(value, _ui.value.gMaxDb - 1)) }

  fun setGMaxDb(value: Int) = _ui.update { it.copy(gMaxDb = max(value, _ui.value.gMinDb + 1)) }

  fun setRecordingEnabled(value: Boolean) = _ui.update { it.copy(recordingEnabled = value) }

  fun panRawPlotBy(deltaSeconds: Int) {
    if (!canPanPlots()) return
    val current = _ui.value.rawPlotOffsetSeconds
    val maxOffset = eegProcessor.maxRawHistoryOffsetSeconds(_ui.value.plotSettings.getValue(PlotType.RAW).windowSeconds)
    _ui.update { it.copy(rawPlotOffsetSeconds = (current + deltaSeconds).coerceIn(0, maxOffset)) }
    refreshRawPreview()
  }

  fun panMetricPlotBy(deltaSeconds: Int) {
    if (!canPanPlots()) return
    val windowSeconds = _ui.value.plotSettings.getValue(PlotType.MEDITATION_PROXY).windowSeconds
    val maxOffset = _ui.value.visibleMetrics.maxOfOrNull { metricHistory.maxOffsetSeconds(it, windowSeconds) } ?: 0
    _ui.update { it.copy(metricPlotOffsetSeconds = (_ui.value.metricPlotOffsetSeconds + deltaSeconds).coerceIn(0, maxOffset)) }
    refreshMetricPlotSeries()
  }

  fun resetRawPlotToLatest() {
    _ui.update { it.copy(rawPlotOffsetSeconds = 0) }
    refreshRawPreview()
  }

  fun resetMetricPlotToLatest() {
    _ui.update { it.copy(metricPlotOffsetSeconds = 0) }
    refreshMetricPlotSeries()
  }

  fun setNotch50Enabled(value: Boolean) {
    eegProcessor.setNotchEnabled(value)
    _ui.update { it.copy(notch50Enabled = value) }
  }

  fun setMetricWindowSeconds(seconds: Int) {
    val normalized = seconds.coerceIn(60, 600)
    val updated = _ui.value.plotSettings.mapValues { (type, settings) ->
      if (type == PlotType.RAW) settings else settings.copy(windowSeconds = normalized)
    }
    _ui.update { it.copy(plotSettings = updated) }
    for ((type, settings) in updated) {
      plotSettingsStore.save(type, settings)
    }
    refreshMetricPlotSeries()
  }

  fun startArtefactCalibration() {
    if (!_ui.value.sessionRunning || _ui.value.calibrating) return
    calibration.resetArtefactCapture()
    artefactPromptIndex = 0
    artefactPromptStartMs = System.currentTimeMillis()
    _ui.update {
      it.copy(
        artefactCalibrationState = ArtefactCalibrationUiState(
          available = true,
          running = true,
          completed = false,
          skipped = false,
          prompt = artefactPromptOrder.first(),
          promptLabel = artefactPromptLabel(artefactPromptOrder.first()),
          remainingSec = 5,
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

  fun skipArtefactCalibration() {
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

  private fun handleThinkGearData(data: ThinkGearData) {
    when (data) {
      is ThinkGearData.PoorSignal -> _ui.update { it.copy(poorSignal = data.value) }
      is ThinkGearData.Attention -> _ui.update { it.copy(attention = data.value) }
      is ThinkGearData.Meditation -> _ui.update { it.copy(meditation = data.value) }
      is ThinkGearData.RawSample -> handleRawSample(data.value)
      else -> Unit
    }
  }

  private fun handleRawSample(raw: Int) {
    val now = System.currentTimeMillis()

    if (lastRateTickMs == 0L) lastRateTickMs = now
    rawCountThisSecond++
    if (now - lastRateTickMs >= 1000L) {
      val samplesPerSecond = rawCountThisSecond * 1000f / max(1L, now - lastRateTickMs).toFloat()
      rawCountThisSecond = 0
      lastRateTickMs = now
      _ui.update { it.copy(samplesPerSecond = samplesPerSecond) }
    }

    recorder?.appendRaw(raw.toShort())

    if (_ui.value.sessionRunning && _ui.value.calibrating) addCalibrationRaw(raw)

    val features = eegProcessor.pushRaw(raw, now)

    rawPreviewDecim++
    if (!_ui.value.sessionPaused && rawPreviewDecim % 32 == 0) {
      refreshRawPreview()
    }

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

    val smoothedProbabilities = StateProbabilities(
      contaminated = contaminatedProbabilitySmoother.add(output.probabilities.contaminated),
      drowsy = drowsyProbabilitySmoother.add(output.probabilities.drowsy),
      settled = settledProbabilitySmoother.add(output.probabilities.settled),
      effortfulFocus = effortfulFocusProbabilitySmoother.add(output.probabilities.effortfulFocus),
      mindWandering = mindWanderingProbabilitySmoother.add(output.probabilities.mindWandering),
      uncertain = uncertainProbabilitySmoother.add(output.probabilities.uncertain),
    )
    val smoothedAlertness = alertnessSmoother.add(output.alertness)
    val smoothedControl = controlSmoother.add(output.control)
    val smoothedSettledness = settlednessSmoother.add(output.settledness)
    val smoothedArtefact = artefactSmoother.add(output.quality.artefactScore)
    val smoothedQualityConfidence = qualityConfidenceSmoother.add(output.qualityConfidence)
    val smoothedMeditationProxy = meditationProxySmoother.add(output.meditationProxy)
    val displayedDrowsy = displayedDrowsySmoother.add(cappedDisplayedDrowsy(output))
    val gatedState = gatedDisplayState(output)
    val displayedState = stateHoldSmoother.update(
      candidate = gatedState,
      confidence = smoothedProbabilities.valueFor(gatedState),
    )

    statsSumMeditationProxy += smoothedMeditationProxy
    statsCount++
    if (lastFeatureTsMs != 0L) {
      val dt = (now - lastFeatureTsMs).coerceAtLeast(0L)
      if (smoothedMeditationProxy >= 0.80f) {
        timeMeditationProxyOver80Ms += dt
      }
    }
    lastFeatureTsMs = now

    metricHistory.add(
      mapOf(
        PlotType.MEDITATION_PROXY to smoothedMeditationProxy,
        PlotType.SETTLEDNESS to smoothedSettledness,
        PlotType.CONTROL to smoothedControl,
        PlotType.ALERTNESS to smoothedAlertness,
        PlotType.DROWSY_SCORE to displayedDrowsy,
        PlotType.ARTEFACT_SCORE to smoothedArtefact,
        PlotType.QUALITY_CONFIDENCE to smoothedQualityConfidence,
        PlotType.EFFORTFUL_FOCUS_SCORE to smoothedProbabilities.effortfulFocus,
        PlotType.MIND_WANDERING_SCORE to smoothedProbabilities.mindWandering,
        PlotType.ESENSE_MEDITATION to _ui.value.meditation.toFloat(),
        PlotType.ESENSE_ATTENTION to _ui.value.attention.toFloat(),
      )
    )

    val feedback = rewardValueForType(
      type = _ui.value.feedbackMetric,
      meditationProxy = smoothedMeditationProxy,
      settledness = smoothedSettledness,
      alertness = smoothedAlertness,
    )

    gameSignalMapper.updateFromClassifier(
      settledness = smoothedSettledness,
      qualityConfidence = smoothedQualityConfidence,
      mindWandering = smoothedProbabilities.mindWandering,
      artefactScore = smoothedArtefact,
      displayedDrowsyScore = displayedDrowsy,
      control = smoothedControl,
      effortfulFocus = smoothedProbabilities.effortfulFocus,
      timestampMs = now,
    )
    val gameSignals = currentGameSignals(now, displayedState = displayedState, poorSignal = poorSignal)
    val selectedGameController = GameRegistry.controllerFor(_ui.value.selectedGameId)
    val currentGameRuntime = _ui.value.gameRuntimeState
    val currentGameHud = selectedGameController.hud(currentGameRuntime, gameSignals).copy(
      inputEnabled = shouldEnableGameInput(_ui.value.selectedGameId),
      inputHint = gameInputHint(_ui.value.selectedGameId, _ui.value.gameRunning),
    )
    val currentGameAudioState = selectedGameController.audio(currentGameRuntime, gameSignals).copy(
      muted = !shouldGameAudioBeAudible(),
    )

    val shouldNoiseAudioRun = shouldNoiseAudioBeAudible()
    if (shouldNoiseAudioRun) {
      ensureSessionAudioEngines()
      audio?.update(
        feedbackValue = feedback,
        invertReward = _ui.value.invertReward,
        gamma = _ui.value.gamma,
        gMinDb = _ui.value.gMinDb,
        gMaxDb = _ui.value.gMaxDb,
      )
      audio?.setMuted(false)
    } else {
      audio?.setMuted(true)
    }

    recorder?.appendFeatures(
      RecordedFeatureRow(
        timestampMs = now,
        poorSignal = poorSignal,
        notch50Enabled = _ui.value.notch50Enabled,
        features = features,
        zScores = output.zScores,
        quality = output.quality,
        artefactCalibrationProfile = artefactCalibrationProfile,
        rawProbabilities = output.probabilities,
        smoothedProbabilities = smoothedProbabilities,
        rawStateLabel = output.rawStateLabel,
        displayedStateLabel = displayedState,
        drowsinessEvidence = output.drowsinessEvidence,
        displayedDrowsyScore = displayedDrowsy,
        alertness = smoothedAlertness,
        control = smoothedControl,
        settledness = smoothedSettledness,
        meditationProxy = smoothedMeditationProxy,
        feedbackMetric = _ui.value.feedbackMetric,
        feedbackValue = feedback,
        selectedGameId = _ui.value.selectedGameId,
        gameSignals = gameSignals,
        gameSummary = gameRuntimeSummary(currentGameRuntime),
      )
    )

    val avgMeditationProxy = if (statsCount == 0) 0f else statsSumMeditationProxy / statsCount.toFloat()
    val over80Seconds = (timeMeditationProxyOver80Ms / 1000L).toInt()

    _ui.update {
      it.copy(
        meditationProxy = smoothedMeditationProxy,
        settledness = smoothedSettledness,
        control = smoothedControl,
        alertness = smoothedAlertness,
        drowsyScore = displayedDrowsy,
        artefactScore = smoothedArtefact,
        qualityConfidence = smoothedQualityConfidence,
        effortfulFocusScore = smoothedProbabilities.effortfulFocus,
        mindWanderingScore = smoothedProbabilities.mindWandering,
        displayedStateLabel = displayedState,
        drowsyTarContribution = output.drowsinessEvidence.tarContribution,
        drowsyTbrContribution = output.drowsinessEvidence.tbrContribution,
        drowsyEntropyContribution = output.drowsinessEvidence.entropyContribution,
        drowsyAbrContribution = output.drowsinessEvidence.abrContribution,
        avgMeditationProxy = avgMeditationProxy,
        timeMeditationProxyOver80Seconds = over80Seconds,
        artefactContact = output.quality.contact,
        artefactLine = output.quality.lineNoise,
        artefactEmg = output.quality.emg,
        artefactBlink = output.quality.blink,
        artefactClip = output.quality.clip,
        artefactStall = output.quality.stall,
        artefactBlinkNormalizationHz = artefactCalibrationProfile.blinkNormalizationHz,
        artefactEmgNormalizationHfRatio = artefactCalibrationProfile.emgNormalizationHfRatio,
        streamStallMs = features.maxGapMs,
        audioRunning = shouldNoiseAudioRun || shouldGameAudioBeAudible(),
        audioMuted = !(shouldNoiseAudioRun || shouldGameAudioBeAudible()),
        audioBaseDb = audio?.debugBaseDb ?: it.audioBaseDb,
        gameSignals = gameSignals,
        gameAudioState = currentGameAudioState,
        gameHudState = currentGameHud,
      )
    }

    refreshMetricPlotSeries()
  }

  private fun finishCleanCalibration(now: Long) {
    scorer.setCalibration(calibration.buildCalibration())
    calibration.seedAdaptiveWindowFromCalibration()
    lastAdaptiveCalibrationUpdateMs = now
    applyCalibratedRawRangeIfNeeded()

    _ui.update {
      it.copy(
        calibrating = false,
        calibrationPhase = CalibrationPhase.BASELINE_COMPLETE,
        calibrationInstruction = calibrationInstructionFor(CalibrationPhase.BASELINE_COMPLETE),
        artefactCalibrationState = ArtefactCalibrationUiState(available = true),
      )
    }

    if (_ui.value.audioEnabled) {
      BellSoundPlayer.playCalibrationComplete()
    }
    syncFeedbackAudioState(fadeIn = true)
  }

  private fun updateArtefactCaptureUi(now: Long, elapsedSec: Int) {
    val elapsedPromptMs = now - artefactPromptStartMs
    if (elapsedPromptMs >= 5_000L) {
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
    val remainingSec = (5 - (elapsedPromptMs / 1000L).toInt()).coerceIn(1, 5)
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
    calibration.buildAdaptiveCalibration()?.let { scorer.setCalibration(it) }
    lastAdaptiveCalibrationUpdateMs = now
  }

  private fun cappedDisplayedDrowsy(output: ClassifierOutput): Float {
    val entropyNotSuppressed = output.zScores.entropy > -0.30f
    val calmSettledWindow = !output.quality.isContaminated &&
      output.settledness > 0.60f &&
      output.qualityConfidence > 0.70f &&
      entropyNotSuppressed
    return if (calmSettledWindow) {
      min(output.drowsyScore, 0.45f)
    } else {
      output.drowsyScore
    }
  }

  private fun gatedDisplayState(output: ClassifierOutput): StateLabel {
    if (output.rawStateLabel != StateLabel.DROWSY) {
      drowsyDisplayWins = 0
      return output.rawStateLabel
    }
    if (output.quality.isContaminated) {
      drowsyDisplayWins = 0
      return StateLabel.SIGNAL_CONTAMINATED
    }
    drowsyDisplayWins++
    return if (output.drowsyScore > 0.80f || drowsyDisplayWins >= 5) {
      StateLabel.DROWSY
    } else {
      StateLabel.UNCERTAIN
    }
  }

  private fun rewardValueForType(
    type: PlotType,
    meditationProxy: Float,
    settledness: Float,
    alertness: Float,
  ): Float {
    return when (type) {
      PlotType.MEDITATION_PROXY -> meditationProxy
      PlotType.SETTLEDNESS -> settledness
      PlotType.ALERTNESS -> alertness
      else -> 0f
    }
  }

  private fun rewardSelectableMetrics(): Set<PlotType> = MetricGlossary.feedbackSourceMetrics().toSet()

  private fun startGameLoop() {
    gameLoopJob?.cancel()
    gameLoopJob = viewModelScope.launch(Dispatchers.Default) {
      while (_ui.value.sessionRunning) {
        val now = System.currentTimeMillis()
        if (_ui.value.selectedTab == AppTab.GAME) {
          val signals = currentGameSignals(now)
          val controller = GameRegistry.controllerFor(_ui.value.selectedGameId)
          val runtimeState = _ui.value.gameRuntimeState
          val dtSeconds = if (lastGameUpdateMs == 0L) {
            1f / 30f
          } else {
            ((now - lastGameUpdateMs).toFloat() / 1000f).coerceIn(1f / 120f, 0.10f)
          }
          lastGameUpdateMs = now

          if (shouldGameLoopRun()) {
            val events = drainGameEvents()
            val nextRuntimeState = controller.step(runtimeState, dtSeconds, signals, events)
            val nextHudState = controller.hud(nextRuntimeState, signals).copy(
              inputEnabled = shouldEnableGameInput(_ui.value.selectedGameId),
              inputHint = gameInputHint(_ui.value.selectedGameId, _ui.value.gameRunning),
            )
            val nextGameAudioState = controller.audio(nextRuntimeState, signals).copy(
              muted = !shouldGameAudioBeAudible(),
            )

            if (shouldGameAudioBeAudible()) {
              ensureSessionAudioEngines()
              gameAudio?.update(_ui.value.selectedGameId, nextGameAudioState)
              gameAudio?.setMuted(false)
            } else {
              gameAudio?.setMuted(true)
            }

            _ui.update {
              it.copy(
                gameSignals = signals,
                gameRuntimeState = nextRuntimeState,
                gameAudioState = nextGameAudioState,
                gameHudState = nextHudState,
                audioRunning = shouldNoiseAudioBeAudible() || shouldGameAudioBeAudible(),
                audioMuted = !(shouldNoiseAudioBeAudible() || shouldGameAudioBeAudible()),
              )
            }
          } else {
            _ui.update {
              it.copy(
                gameSignals = signals,
                gameAudioState = controller.audio(runtimeState, signals).copy(muted = true),
                gameHudState = controller.hud(runtimeState, signals).copy(
                  inputEnabled = false,
                  inputHint = gameInputHint(it.selectedGameId, it.gameRunning),
                ),
              )
            }
            gameAudio?.setMuted(true)
          }
        } else {
          lastGameUpdateMs = now
          gameAudio?.setMuted(true)
        }
        delay(33L)
      }
    }
  }

  private fun drainGameEvents(): List<GameEvent> {
    val drained = mutableListOf<GameEvent>()
    while (true) {
      val next = pendingGameEvents.poll() ?: break
      drained += next
    }
    return drained
  }

  private fun shouldGameLoopRun(): Boolean {
    return _ui.value.sessionRunning &&
      !_ui.value.sessionPaused &&
      !_ui.value.calibrating &&
      !_ui.value.artefactCalibrationState.running &&
      _ui.value.gameRunning &&
      _ui.value.selectedTab == AppTab.GAME
  }

  private fun canStartGame(): Boolean {
    return _ui.value.sessionRunning &&
      !_ui.value.sessionPaused &&
      !_ui.value.calibrating &&
      !_ui.value.artefactCalibrationState.running
  }

  private fun shouldEnableGameInput(gameId: GameId): Boolean {
    return shouldGameLoopRun() && gameId.isHybrid()
  }

  private fun shouldNoiseAudioBeAudible(): Boolean {
    return _ui.value.audioEnabled &&
      _ui.value.sessionRunning &&
      !_ui.value.sessionPaused &&
      !_ui.value.calibrating &&
      !_ui.value.artefactCalibrationState.running &&
      _ui.value.selectedTab != AppTab.GAME
  }

  private fun shouldGameAudioBeAudible(): Boolean {
    return _ui.value.audioEnabled &&
      _ui.value.sessionRunning &&
      !_ui.value.sessionPaused &&
      !_ui.value.calibrating &&
      !_ui.value.artefactCalibrationState.running &&
      _ui.value.gameRunning &&
      _ui.value.selectedTab == AppTab.GAME
  }

  private fun currentGameSignals(
    nowMs: Long,
    displayedState: StateLabel = _ui.value.displayedStateLabel,
    poorSignal: Int = _ui.value.poorSignal,
  ): GameSignalSnapshot {
    return gameSignalMapper.snapshot(
      nowMs = nowMs,
      stateLabel = displayedState,
      poorSignal = poorSignal,
      elapsedSeconds = _ui.value.sessionElapsedSec,
      batteryPercent = _ui.value.batteryPercent,
    )
  }

  private fun refreshSelectedGameUi(nowMs: Long = System.currentTimeMillis()) {
    val controller = GameRegistry.controllerFor(_ui.value.selectedGameId)
    val signals = currentGameSignals(nowMs)
    val runtimeState = _ui.value.gameRuntimeState
    _ui.update {
      it.copy(
        gameSignals = signals,
        gameAudioState = controller.audio(runtimeState, signals).copy(
          muted = !shouldGameAudioBeAudible(),
        ),
        gameHudState = controller.hud(runtimeState, signals).copy(
          inputEnabled = shouldEnableGameInput(it.selectedGameId),
          inputHint = gameInputHint(it.selectedGameId, it.gameRunning),
        ),
      )
    }
  }

  private fun gameInputHint(gameId: GameId, gameRunning: Boolean): String {
    return when {
      !_ui.value.sessionRunning -> "Start a headset session on Dashboard, then return here."
      _ui.value.calibrating -> "Finish calibration, then press Start."
      _ui.value.artefactCalibrationState.running -> "Finish artifact calibration or skip it, then press Start."
      _ui.value.sessionPaused && gameRunning -> "Resume the session to continue ${gameId.displayName()}."
      !gameRunning -> gameId.startHint()
      else -> gameId.inputHint()
    }
  }

  private fun ensureSessionAudioEngines(fadeIn: Boolean = false) {
    if (!_ui.value.audioEnabled || !_ui.value.sessionRunning) return
    if (audio == null) {
      audio = NoiseAudioEngine().apply {
        start()
        setMuted(true)
      }
    }
    if (gameAudio == null) {
      gameAudio = GameSoundEngine().apply {
        start()
        setMuted(true)
      }
    }
    if (fadeIn) {
      audio?.beginFadeIn()
      gameAudio?.beginFadeIn()
    }
  }

  private fun shutdownAudioEngines() {
    audio?.setMuted(true)
    gameAudio?.setMuted(true)
    audio?.stop()
    gameAudio?.stop()
    audio = null
    gameAudio = null
  }

  private fun syncFeedbackAudioState(fadeIn: Boolean = false) {
    val shouldNoiseRun = shouldNoiseAudioBeAudible()
    val shouldGameRun = shouldGameAudioBeAudible()

    if (!_ui.value.audioEnabled || !_ui.value.sessionRunning) {
      shutdownAudioEngines()
      _ui.update {
        it.copy(
          audioRunning = false,
          audioMuted = true,
          gameAudioState = it.gameAudioState.copy(muted = true),
          gameHudState = it.gameHudState.copy(
            inputEnabled = shouldEnableGameInput(it.selectedGameId),
            inputHint = gameInputHint(it.selectedGameId, it.gameRunning),
          ),
        )
      }
      return
    }

    ensureSessionAudioEngines(fadeIn = fadeIn)
    audio?.setMuted(!shouldNoiseRun)
    gameAudio?.update(_ui.value.selectedGameId, _ui.value.gameAudioState)
    gameAudio?.setMuted(!shouldGameRun)

    _ui.update {
      it.copy(
        audioRunning = shouldNoiseRun || shouldGameRun,
        audioMuted = !(shouldNoiseRun || shouldGameRun),
        gameAudioState = it.gameAudioState.copy(muted = !shouldGameRun),
        gameHudState = it.gameHudState.copy(
          inputEnabled = shouldEnableGameInput(it.selectedGameId),
          inputHint = gameInputHint(it.selectedGameId, it.gameRunning),
        ),
      )
    }
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
    _ui.update {
      it.copy(
        rawPreview = values,
        rawPlotOffsetSeconds = clampedOffset,
      )
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

  private fun resetSmoothers() {
    contaminatedProbabilitySmoother.reset()
    drowsyProbabilitySmoother.reset()
    displayedDrowsySmoother.reset()
    settledProbabilitySmoother.reset()
    effortfulFocusProbabilitySmoother.reset()
    mindWanderingProbabilitySmoother.reset()
    uncertainProbabilitySmoother.reset()
    alertnessSmoother.reset()
    controlSmoother.reset()
    settlednessSmoother.reset()
    artefactSmoother.reset()
    qualityConfidenceSmoother.reset()
    meditationProxySmoother.reset()
    stateHoldSmoother.reset()
    drowsyDisplayWins = 0
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

  private fun calibrationInstructionFor(phase: CalibrationPhase): String {
    return when (phase) {
      CalibrationPhase.EYES_OPEN -> ctx.getString(R.string.calibration_phase_eyes_open)
      CalibrationPhase.EYES_CLOSED -> ctx.getString(R.string.calibration_phase_eyes_closed)
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
    super.onCleared()
    sessionJob?.cancel()
    gameLoopJob?.cancel()
    shutdownAudioEngines()
    client?.close()
    client = null
  }
}
