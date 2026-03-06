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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class MainViewModel(app: Application) : AndroidViewModel(app) {

  private val _ui = MutableStateFlow(
    UiState(
      plotSettings = createPlotSettingsStore(app.applicationContext).load()
    )
  )
  val ui: StateFlow<UiState> = _ui

  private val ctx = app.applicationContext
  private val plotSettingsStore = createPlotSettingsStore(ctx)

  private val btAdapter: BluetoothAdapter? by lazy {
    val mgr = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    mgr.adapter
  }

  private var client: BluetoothMindWaveClient? = null

  private val rawSampleRateHz = 512
  private val eegProcessor = EegProcessor(sampleRateHz = rawSampleRateHz)
  private val calibration = CalibrationManager(calibrationSeconds = 60)
  private val scorer = ScoreModel()
  private val metricHistory = MetricHistory(maxSeconds = 600, pointsPerSecond = 4)
  private val smoothingWindowPoints = 16
  private val samathaSmoother = RollingAverage(smoothingWindowPoints)
  private val relaxedAlertnessSmoother = RollingAverage(smoothingWindowPoints)
  private val artefactSmoother = RollingAverage(smoothingWindowPoints)

  private var statsSumSamatha = 0f
  private var statsCount = 0
  private var timeSamathaOver80Ms = 0L
  private var lastFeatureTsMs = 0L

  private var sessionStartMs: Long = 0L
  private var pausedAtMs: Long = 0L
  private var pausedAccumMs: Long = 0L

  private var sessionJob: Job? = null

  private var audio: NoiseAudioEngine? = null
  private var recorder: SessionRecorder? = null

  private var lastSampleAtMs: Long = 0
  private var rawCountThisSecond: Int = 0
  private var lastRateTickMs: Long = 0
  private var rawPreviewDecim: Int = 0

  private var lastGameUpdateMs: Long = 0L
  private var lastAdaptiveCalibrationUpdateMs: Long = 0L
  private var calibrationRawCount = 0
  private var calibrationRawMean = 0.0
  private var calibrationRawM2 = 0.0

  init {
    if (Build.VERSION.SDK_INT < 31) {
      _ui.update { it.copy(btPermissionGranted = true) }
      refreshBondedDevices()
    }
    refreshSelectedPlotSeries()
    refreshRawPreview()
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

    _ui.update { it.copy(bondedDevices = bonded) }
    if (_ui.value.selectedDeviceMac == null && bonded.isNotEmpty()) {
      _ui.update { it.copy(selectedDeviceMac = bonded.first().mac) }
    }
  }

  fun selectDevice(mac: String) {
    _ui.update { it.copy(selectedDeviceMac = mac) }
  }

  fun selectTab(tab: AppTab) {
    _ui.update { it.copy(selectedTab = tab) }
  }

  fun setSettingsPanelVisible(visible: Boolean) {
    _ui.update { it.copy(settingsPanelVisible = visible) }
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
    if (_ui.value.sessionRunning) return
    if (!_ui.value.connected) return

    eegProcessor.reset()
    calibration.reset()
    scorer.reset()
    metricHistory.reset()
    resetRawCalibrationStats()

    statsSumSamatha = 0f
    statsCount = 0
    timeSamathaOver80Ms = 0L
    lastFeatureTsMs = 0L
    lastGameUpdateMs = 0L
    lastAdaptiveCalibrationUpdateMs = 0L
    samathaSmoother.reset()
    relaxedAlertnessSmoother.reset()
    artefactSmoother.reset()

    sessionStartMs = System.currentTimeMillis()
    pausedAtMs = 0L
    pausedAccumMs = 0L

    if (!_ui.value.plotSettings.getValue(PlotType.RAW).isUserLocked) {
      val (yMin, yMax) = PlotMath.defaultRawRange()
      updatePlotSettings(
        type = PlotType.RAW,
        newSettings = _ui.value.plotSettings.getValue(PlotType.RAW).copy(yMin = yMin, yMax = yMax, isUserLocked = false),
        persist = true,
        refresh = false
      )
    }

    if (_ui.value.recordingEnabled) {
      recorder = SessionRecorder(ctx).apply { start(sampleRateHz = rawSampleRateHz) }
      _ui.update { it.copy(lastRecordingPath = recorder?.sessionDir?.absolutePath) }
    } else {
      recorder = null
      _ui.update { it.copy(lastRecordingPath = null) }
    }

    audio = NoiseAudioEngine().apply {
      start()
      setMuted(true)
    }

    _ui.update {
      it.copy(
        sessionRunning = true,
        sessionPaused = false,
        calibrating = true,
        calibrationRemainingSec = calibration.calibrationSeconds,
        sessionElapsedSec = 0,
        audioRunning = true,
        audioMuted = true,
        plotHistory = emptyList(),
      )
    }

    refreshRawPreview()
    refreshSelectedPlotSeries()

    sessionJob = viewModelScope.launch(Dispatchers.Default) {
      while (_ui.value.sessionRunning) {
        val now = System.currentTimeMillis()
        val rem = calibration.remainingSeconds()

        val elapsedMs = now - sessionStartMs - pausedAccumMs - if (_ui.value.sessionPaused) (now - pausedAtMs) else 0L
        val elapsedSec = (elapsedMs / 1000L).toInt().coerceAtLeast(0)

        _ui.update {
          it.copy(
            calibrationRemainingSec = rem,
            sessionElapsedSec = elapsedSec
          )
        }

        if (calibration.isDone() && _ui.value.calibrating) {
          scorer.setCalibration(calibration.buildCalibration())
          calibration.seedAdaptiveWindowFromCalibration()
          lastAdaptiveCalibrationUpdateMs = now
          applyCalibratedRawRangeIfNeeded()
          _ui.update { it.copy(calibrating = false) }

          audio?.beginFadeIn()
          audio?.setMuted(false)
          _ui.update { it.copy(audioMuted = false) }
        }

        kotlinx.coroutines.delay(200)
      }
    }
  }

  fun stopSession() {
    sessionJob?.cancel()
    sessionJob = null

    audio?.stop()
    audio = null

    recorder?.stop()
    recorder = null

    _ui.update {
      it.copy(
        sessionRunning = false,
        sessionPaused = false,
        calibrating = false,
        calibrationRemainingSec = 0,
        sessionElapsedSec = 0,
        audioRunning = false,
        audioMuted = true,
      )
    }
  }

  fun togglePause() {
    if (!_ui.value.sessionRunning) return
    val now = System.currentTimeMillis()
    val paused = _ui.value.sessionPaused
    if (!paused) {
      pausedAtMs = now
      audio?.setMuted(true)
      _ui.update { it.copy(sessionPaused = true, audioMuted = true) }
    } else {
      pausedAccumMs += (now - pausedAtMs).coerceAtLeast(0)
      pausedAtMs = 0L
      val shouldUnmute = !_ui.value.calibrating
      audio?.setMuted(!shouldUnmute)
      _ui.update { it.copy(sessionPaused = false, audioMuted = !shouldUnmute) }
    }
  }

  fun setInvertReward(value: Boolean) = _ui.update { it.copy(invertReward = value) }

  fun setArtefactsReduceScore(value: Boolean) = _ui.update { it.copy(artefactsReduceScore = value) }

  fun setCrackleEnabled(value: Boolean) = _ui.update { it.copy(crackleEnabled = value) }

  fun setGamma(value: Float) = _ui.update { it.copy(gamma = value) }

  fun setGMinDb(value: Int) = _ui.update { it.copy(gMinDb = min(value, _ui.value.gMaxDb - 1)) }

  fun setGMaxDb(value: Int) = _ui.update { it.copy(gMaxDb = max(value, _ui.value.gMinDb + 1)) }

  fun setCrackleIntensity(value: Float) = _ui.update { it.copy(crackleIntensity = value) }

  fun setRecordingEnabled(value: Boolean) = _ui.update { it.copy(recordingEnabled = value) }

  fun setFeedbackMetric(value: PlotType) {
    if (value == PlotType.RAW) return
    _ui.update { it.copy(feedbackMetric = value) }
  }

  fun setGameMetric(value: PlotType) {
    if (value == PlotType.RAW) return
    _ui.update { state ->
      state.copy(
        gameState = state.gameState.copy(metric = value)
      )
    }
  }

  fun setPlotType(value: PlotType) {
    _ui.update { it.copy(plotType = value) }
    if (value == PlotType.RAW) {
      refreshRawPreview()
    }
    refreshSelectedPlotSeries()
  }

  fun setPlotWindowSeconds(type: PlotType, seconds: Int) {
    val base = _ui.value.plotSettings.getValue(type)
    val normalized = when (type) {
      PlotType.RAW -> seconds.coerceIn(3, 20)
      else -> seconds.coerceIn(60, 600)
    }
    updatePlotSettings(
      type = type,
      newSettings = base.copy(windowSeconds = normalized),
      persist = true,
      refresh = true,
    )
  }

  fun setPlotYMin(type: PlotType, yMin: Float) {
    val base = _ui.value.plotSettings.getValue(type)
    val lowerBound = when (type) {
      PlotType.RAW -> -4000f
      else -> 0f
    }
    val upperBound = when (type) {
      PlotType.RAW -> base.yMax - 50f
      else -> base.yMax - 1f
    }
    val clamped = yMin.coerceIn(lowerBound, upperBound)
    updatePlotSettings(
      type = type,
      newSettings = base.copy(yMin = clamped, isUserLocked = true),
      persist = true,
      refresh = true,
    )
  }

  fun setPlotYMax(type: PlotType, yMax: Float) {
    val base = _ui.value.plotSettings.getValue(type)
    val lowerBound = when (type) {
      PlotType.RAW -> base.yMin + 50f
      else -> base.yMin + 1f
    }
    val upperBound = when (type) {
      PlotType.RAW -> 4000f
      else -> 100f
    }
    val clamped = yMax.coerceIn(lowerBound, upperBound)
    updatePlotSettings(
      type = type,
      newSettings = base.copy(yMax = clamped, isUserLocked = true),
      persist = true,
      refresh = true,
    )
  }

  fun resetPlotSettings(type: PlotType) {
    val defaults = defaultPlotSettings().getValue(type)
    updatePlotSettings(type = type, newSettings = defaults, persist = true, refresh = true)
  }

  fun testBeep() {
    viewModelScope.launch(Dispatchers.Default) {
      try {
        val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
        try {
          tg.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
          kotlinx.coroutines.delay(350)
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

    val prev = lastSampleAtMs
    lastSampleAtMs = now

    if (lastRateTickMs == 0L) lastRateTickMs = now
    rawCountThisSecond++
    if (now - lastRateTickMs >= 1000) {
      val samplesPerSecond = rawCountThisSecond * 1000f / max(1L, (now - lastRateTickMs)).toFloat()
      rawCountThisSecond = 0
      lastRateTickMs = now
      _ui.update { it.copy(samplesPerSecond = samplesPerSecond) }
    }

    recorder?.appendRaw(raw.toShort())

    if (_ui.value.sessionRunning && _ui.value.calibrating) addCalibrationRaw(raw)

    val features = eegProcessor.pushRaw(raw, now)

    rawPreviewDecim++
    if (rawPreviewDecim % 32 == 0) {
      refreshRawPreview()
    }

    if (features == null) return

    val poorSignal = _ui.value.poorSignal
    val stallMs = if (prev == 0L) 0L else (now - prev).coerceAtLeast(0)
    val artefacts = scorer.artefacts(poorSignal = poorSignal, features = features, stallMs = stallMs)

    val runningActive = _ui.value.sessionRunning && !_ui.value.sessionPaused

    if (_ui.value.sessionRunning && _ui.value.calibrating) {
      calibration.addSample(rai = features.rai, artefactA = artefacts.totalA)
    } else if (runningActive && !_ui.value.calibrating) {
      calibration.addAdaptiveSample(
        rai = features.rai,
        artefactA = artefacts.totalA,
        poorSignal = poorSignal,
      )
      maybeUpdateAdaptiveCalibration(now)
    }

    val samathaScoreInstant = scorer.scoreS(
      rai = features.rai,
      artefactA = artefacts.totalA,
      artefactsReduceScore = _ui.value.artefactsReduceScore
    )
    val relaxedAlertnessInstant = scorer.normaliseRai(features.rai)
    val artefactInstant = artefacts.totalA

    val samathaScoreSmoothed = samathaSmoother.add(samathaScoreInstant)
    val relaxedAlertnessSmoothed = relaxedAlertnessSmoother.add(relaxedAlertnessInstant)
    val artefactSmoothed = artefactSmoother.add(artefactInstant)

    if (runningActive) {
      statsSumSamatha += samathaScoreInstant
      statsCount++

      if (lastFeatureTsMs != 0L) {
        val dt = (now - lastFeatureTsMs).coerceAtLeast(0)
        if (samathaScoreInstant >= 0.80f) timeSamathaOver80Ms += dt
      }
      lastFeatureTsMs = now
    }

    metricHistory.add(
      samathaScore = samathaScoreSmoothed,
      artefactScore = artefactSmoothed,
      relaxedAlertnessIndex = relaxedAlertnessSmoothed,
      meditationValue = _ui.value.meditation,
      attentionValue = _ui.value.attention
    )

    val feedback = metricValueForType(
      type = _ui.value.feedbackMetric,
      samathaScore = samathaScoreSmoothed,
      artefactScore = artefactSmoothed,
      relaxedAlertnessIndex = relaxedAlertnessSmoothed,
      meditation = _ui.value.meditation,
      attention = _ui.value.attention,
    )

    val shouldAudioRun = _ui.value.sessionRunning && !_ui.value.sessionPaused && !_ui.value.calibrating
    if (shouldAudioRun) {
      audio?.update(
        scoreS = feedback,
        artefactA = artefactSmoothed,
        invertReward = _ui.value.invertReward,
        gamma = _ui.value.gamma,
        gMinDb = _ui.value.gMinDb,
        gMaxDb = _ui.value.gMaxDb,
        crackleEnabled = _ui.value.crackleEnabled,
        crackleIntensity = _ui.value.crackleIntensity
      )
      audio?.setMuted(false)
    } else {
      audio?.setMuted(true)
    }

    recorder?.appendFeatures(
      timestampMs = now,
      rai = features.rai,
      scoreS = samathaScoreInstant,
      scoreA = artefactInstant,
      poorSignal = poorSignal,
      aContact = artefacts.contact,
      aLine = artefacts.line,
      aEmg = artefacts.emg,
      aBlink = artefacts.blink,
      aStall = artefacts.stall,
    )

    val avgSamatha = if (statsCount == 0) 0f else (statsSumSamatha / statsCount.toFloat())
    val over80Seconds = (timeSamathaOver80Ms / 1000L).toInt()

    val gameMetricValue = metricValueForType(
      type = _ui.value.gameState.metric,
      samathaScore = samathaScoreSmoothed,
      artefactScore = artefactSmoothed,
      relaxedAlertnessIndex = relaxedAlertnessSmoothed,
      meditation = _ui.value.meditation,
      attention = _ui.value.attention,
    )

    val dtSeconds = if (lastGameUpdateMs == 0L) {
      0.25f
    } else {
      ((now - lastGameUpdateMs).toFloat() / 1000f).coerceIn(0.016f, 0.25f)
    }
    lastGameUpdateMs = now

    val nextLevitation = GamePhysics.step(
      state = LevitationState(
        altitude = _ui.value.gameState.altitude,
        velocity = _ui.value.gameState.velocity,
      ),
      target = GamePhysics.metricToTargetHeight(gameMetricValue),
      dtSeconds = dtSeconds,
    )

    _ui.update {
      it.copy(
        relaxedAlertnessIndex = relaxedAlertnessInstant,
        samathaScore = samathaScoreInstant,
        artefactScore = artefactInstant,
        avgSamathaScore = avgSamatha,
        timeSamathaOver80Seconds = over80Seconds,

        artefactContact = artefacts.contact,
        artefactLine = artefacts.line,
        artefactEmg = artefacts.emg,
        artefactBlink = artefacts.blink,
        artefactStall = artefacts.stall,
        streamStallMs = artefacts.stallMs,

        audioRunning = audio != null,
        audioMuted = !shouldAudioRun,
        audioBaseDb = audio?.debugBaseDb ?: it.audioBaseDb,

        gameState = it.gameState.copy(
          altitude = nextLevitation.altitude,
          velocity = nextLevitation.velocity,
        ),
        gameHudState = it.gameHudState.copy(
          metricValuePercent = (gameMetricValue * 100f).toInt().coerceIn(0, 100),
          artefactPercent = (artefactSmoothed * 100f).toInt().coerceIn(0, 100),
          poorSignal = poorSignal,
          elapsedSeconds = it.sessionElapsedSec,
          batteryPercent = it.batteryPercent,
        )
      )
    }

    refreshSelectedPlotSeries()
  }

  private fun maybeUpdateAdaptiveCalibration(now: Long) {
    if (now - lastAdaptiveCalibrationUpdateMs < 1000L) return
    calibration.buildAdaptiveCalibration()?.let { scorer.setCalibration(it) }
    lastAdaptiveCalibrationUpdateMs = now
  }

  private fun metricValueForType(
    type: PlotType,
    samathaScore: Float,
    artefactScore: Float,
    relaxedAlertnessIndex: Float,
    meditation: Int,
    attention: Int,
  ): Float {
    return when (type) {
      PlotType.SAMATHA_SCORE -> samathaScore
      PlotType.ARTEFACT_SCORE -> (1f - artefactScore).coerceIn(0f, 1f)
      PlotType.RELAXED_ALERTNESS_INDEX -> relaxedAlertnessIndex.coerceIn(0f, 1f)
      PlotType.ESENSE_MEDITATION -> (meditation / 100f).coerceIn(0f, 1f)
      PlotType.ESENSE_ATTENTION -> (attention / 100f).coerceIn(0f, 1f)
      PlotType.RAW -> samathaScore
    }
  }

  private fun updatePlotSettings(
    type: PlotType,
    newSettings: PlotSettings,
    persist: Boolean,
    refresh: Boolean,
  ) {
    _ui.update { state ->
      state.copy(
        plotSettings = state.plotSettings + (type to newSettings)
      )
    }
    if (persist) {
      plotSettingsStore.save(type, newSettings)
    }
    if (refresh) {
      if (type == PlotType.RAW) {
        refreshRawPreview()
      }
      refreshSelectedPlotSeries()
    }
  }

  private fun refreshRawPreview() {
    val setting = _ui.value.plotSettings.getValue(PlotType.RAW)
    val sampleCount = (setting.windowSeconds * rawSampleRateHz).coerceIn(64, rawSampleRateHz * 20)
    val values = eegProcessor.rawPreview(sampleCount)
    _ui.update { it.copy(rawPreview = values) }
  }

  private fun refreshSelectedPlotSeries() {
    val plotType = _ui.value.plotType
    if (plotType == PlotType.RAW) {
      _ui.update { it.copy(plotHistory = emptyList()) }
      return
    }
    val windowSeconds = _ui.value.plotSettings.getValue(plotType).windowSeconds
    val series = metricHistory.series(plotType, windowSeconds)
    _ui.update { it.copy(plotHistory = convertSeriesForDisplay(plotType, series)) }
  }

  private fun convertSeriesForDisplay(type: PlotType, values: List<Float>): List<Float> {
    return when (type) {
      PlotType.SAMATHA_SCORE,
      PlotType.ARTEFACT_SCORE,
      PlotType.RELAXED_ALERTNESS_INDEX -> values.map { (it * 100f).coerceIn(0f, 100f) }
      PlotType.ESENSE_MEDITATION,
      PlotType.ESENSE_ATTENTION -> values.map { it.coerceIn(0f, 100f) }
      PlotType.RAW -> values
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
      refresh = true,
    )
  }
}
