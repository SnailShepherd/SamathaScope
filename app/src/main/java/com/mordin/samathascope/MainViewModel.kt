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

/**
 * Main app state + orchestration.
 *
 * Responsibilities:
 * - list paired Bluetooth devices (MindWave Mobile 2)
 * - connect / disconnect
 * - parse ThinkGear packets and feed raw samples into the processing pipeline
 * - manage session lifecycle: calibration → running → pause/stop
 * - push live values into UiState
 * - drive the audio engine (white noise + crackle overlay)
 *
 * This is intentionally a single ViewModel for v0.1.
 * Splitting into separate layers (Bluetooth, DSP, UI state) can happen later if it becomes painful.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

  private val _ui = MutableStateFlow(UiState())
  val ui: StateFlow<UiState> = _ui

  private val ctx = app.applicationContext

  private val btAdapter: BluetoothAdapter? by lazy {
    val mgr = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    mgr.adapter
  }

  private var client: BluetoothMindWaveClient? = null

  // DSP + scoring
  private val eegProcessor = EegProcessor(sampleRateHz = 512)
  private val calibration = CalibrationManager(calibrationSeconds = 60)
  private val scorer = ScoreModel()

  // History for metric plots (4 Hz update, 60 sec window by default)
  private val metricHistory = MetricHistory(maxSeconds = 60, pointsPerSecond = 4)

  // Session stats (simple, to mimic EEG Meditation app)
  private var statsSumS = 0f
  private var statsCount = 0
  private var timeSge80Ms = 0L
  private var lastFeatureTsMs = 0L

  // Session timing
  private var sessionStartMs: Long = 0L
  private var pausedAtMs: Long = 0L
  private var pausedAccumMs: Long = 0L

  private var sessionJob: Job? = null

  // Audio + recorder
  private var audio: NoiseAudioEngine? = null
  private var recorder: SessionRecorder? = null

  // For stream rate / stalls
  private var lastSampleAtMs: Long = 0
  private var rawCountThisSecond: Int = 0
  private var lastRateTickMs: Long = 0
  private var rawPreviewDecim: Int = 0

  init {
    // Android 11 and below: no runtime Bluetooth permission required for bonded device access.
    if (Build.VERSION.SDK_INT < 31) {
      _ui.update { it.copy(btPermissionGranted = true) }
      refreshBondedDevices()
    }
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

    // Reset stats
    statsSumS = 0f
    statsCount = 0
    timeSge80Ms = 0L
    lastFeatureTsMs = 0L

    sessionStartMs = System.currentTimeMillis()
    pausedAtMs = 0L
    pausedAccumMs = 0L

    // Recording (optional)
    if (_ui.value.recordingEnabled) {
      recorder = SessionRecorder(ctx).apply { start(sampleRateHz = 512) }
      _ui.update { it.copy(lastRecordingPath = recorder?.sessionDir?.absolutePath) }
    } else {
      recorder = null
      _ui.update { it.copy(lastRecordingPath = null) }
    }

    // Audio engine (start muted; unmute after calibration)
    audio = NoiseAudioEngine().apply { start(); setMuted(true) }

    _ui.update {
      it.copy(
        sessionRunning = true,
        sessionPaused = false,
        calibrating = true,
        calibrationRemainingSec = calibration.calibrationSeconds,
        sessionElapsedSec = 0,
        audioRunning = true,
        audioMuted = true
      )
    }

    // Session tick: update countdown + elapsed time.
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
          _ui.update { it.copy(calibrating = false) }

          // Fade in feedback now that we have baseline percentiles.
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
      // If calibration is still happening, stay muted.
      val shouldUnmute = !_ui.value.calibrating
      audio?.setMuted(!shouldUnmute)
      _ui.update { it.copy(sessionPaused = false, audioMuted = !shouldUnmute) }
    }
  }

  fun setInvertReward(v: Boolean) = _ui.update { it.copy(invertReward = v) }
  fun setArtefactsReduceScore(v: Boolean) = _ui.update { it.copy(artefactsReduceScore = v) }
  fun setCrackleEnabled(v: Boolean) = _ui.update { it.copy(crackleEnabled = v) }
  fun setGamma(v: Float) = _ui.update { it.copy(gamma = v) }
  fun setGMinDb(v: Int) = _ui.update { it.copy(gMinDb = minOf(v, _ui.value.gMaxDb - 1)) }
  fun setGMaxDb(v: Int) = _ui.update { it.copy(gMaxDb = maxOf(v, _ui.value.gMinDb + 1)) }
  fun setCrackleIntensity(v: Float) = _ui.update { it.copy(crackleIntensity = v) }
  fun setRecordingEnabled(v: Boolean) = _ui.update { it.copy(recordingEnabled = v) }

  fun setFeedbackMetric(v: PlotType) {
    // RAW is not a meaningful feedback source; ignore.
    if (v == PlotType.RAW) return
    _ui.update { it.copy(feedbackMetric = v) }
  }

  fun setPlotType(v: PlotType) {
    _ui.update { it.copy(plotType = v) }
  }

  /**
   * Simple audio sanity-check:
   * plays a short beep on the MUSIC stream.
   *
   * This is intentionally separate from the white-noise engine so that when audio is “silent”
   * we can distinguish “engine bug” from “device volume muted”.
   */
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
        // If this fails, the device audio stack is probably muted or restricted.
      }
    }
  }

  private fun handleThinkGearData(d: ThinkGearData) {
    when (d) {
      is ThinkGearData.PoorSignal -> _ui.update { it.copy(poorSignal = d.value) }
      is ThinkGearData.Attention -> _ui.update { it.copy(attention = d.value) }
      is ThinkGearData.Meditation -> _ui.update { it.copy(meditation = d.value) }
      is ThinkGearData.RawSample -> handleRawSample(d.value)
      else -> Unit
    }
  }

  private fun handleRawSample(raw: Int) {
    val now = System.currentTimeMillis()

    // Stream stall estimate: time since previous sample.
    val prev = lastSampleAtMs
    lastSampleAtMs = now

    // samples/s estimate
    if (lastRateTickMs == 0L) lastRateTickMs = now
    rawCountThisSecond++
    if (now - lastRateTickMs >= 1000) {
      val sps = rawCountThisSecond * 1000f / max(1L, (now - lastRateTickMs)).toFloat()
      rawCountThisSecond = 0
      lastRateTickMs = now
      _ui.update { it.copy(samplesPerSecond = sps) }
    }

    recorder?.appendRaw(raw.toShort())

    // Push into DSP pipeline
    val features = eegProcessor.pushRaw(raw, now)

    // Update waveform preview more frequently than feature extraction (otherwise it looks “slow and drunk”).
    rawPreviewDecim++
    if (rawPreviewDecim % 32 == 0) {
      _ui.update { it.copy(rawPreview = eegProcessor.rawPreview()) }
    }

    if (features == null) return

    val poor = _ui.value.poorSignal
    val stallMs = if (prev == 0L) 0L else (now - prev).coerceAtLeast(0)
    val artefacts = scorer.artefacts(poorSignal = poor, features = features, stallMs = stallMs)

    // During calibration we collect baseline distributions.
    if (_ui.value.sessionRunning && _ui.value.calibrating) {
      calibration.addSample(rai = features.rai, artefactA = artefacts.totalA)
    }

    // Core score S derived from raw EEG (RAI + artefact penalty).
    val scoreS = scorer.scoreS(
      rai = features.rai,
      artefactA = artefacts.totalA,
      artefactsReduceScore = _ui.value.artefactsReduceScore
    )

    // Session stats
    if (_ui.value.sessionRunning && !_ui.value.sessionPaused) {
      statsSumS += scoreS
      statsCount++

      if (lastFeatureTsMs != 0L) {
        val dt = (now - lastFeatureTsMs).coerceAtLeast(0)
        if (scoreS >= 0.80f) timeSge80Ms += dt
      }
      lastFeatureTsMs = now
    }

    // Store metric history for plotting (only 60s window; keeps UI cheap).
    metricHistory.add(
      scoreS = scoreS,
      scoreA = artefacts.totalA,
      rai = scorer.normaliseRai(features.rai),
      meditation = _ui.value.meditation,
      attention = _ui.value.attention
    )

    val plotSeries = when (_ui.value.plotType) {
      PlotType.RAW -> emptyList()
      PlotType.SAMATHA_S -> metricHistory.seriesS()
      PlotType.ARTEFACT_A -> metricHistory.seriesA()
      PlotType.RAI -> metricHistory.seriesRai()
      PlotType.ESENSE_MEDITATION -> metricHistory.seriesMeditation()
      PlotType.ESENSE_ATTENTION -> metricHistory.seriesAttention()
    }

    // Choose feedback value based on UI selection.
    val feedback = when (_ui.value.feedbackMetric) {
      PlotType.SAMATHA_S -> scoreS
      PlotType.ARTEFACT_A -> (1f - artefacts.totalA).coerceIn(0f, 1f) // “better” = fewer artefacts
      PlotType.RAI -> scorer.normaliseRai(features.rai)
      PlotType.ESENSE_MEDITATION -> (_ui.value.meditation / 100f).coerceIn(0f, 1f)
      PlotType.ESENSE_ATTENTION -> (_ui.value.attention / 100f).coerceIn(0f, 1f)
      PlotType.RAW -> scoreS
    }

    // Drive audio if session is running, not paused, and calibration finished.
    val shouldAudioRun = _ui.value.sessionRunning && !_ui.value.sessionPaused && !_ui.value.calibrating
    if (shouldAudioRun) {
      audio?.update(
        scoreS = feedback,
        artefactA = artefacts.totalA,
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

    // Record features
    recorder?.appendFeatures(
      timestampMs = now,
      rai = features.rai,
      scoreS = scoreS,
      scoreA = artefacts.totalA,
      poorSignal = poor,
      aContact = artefacts.contact,
      aLine = artefacts.line,
      aEmg = artefacts.emg,
      aBlink = artefacts.blink,
      aStall = artefacts.stall,
    )

    val avgS = if (statsCount == 0) 0f else (statsSumS / statsCount.toFloat())
    val sge80sec = (timeSge80Ms / 1000L).toInt()

    // Push UI update
    _ui.update {
      it.copy(
        rai = features.rai,
        scoreS = scoreS,
        scoreA = artefacts.totalA,
        avgS = avgS,
        timeSge80Sec = sge80sec,

        aContact = artefacts.contact,
        aLine = artefacts.line,
        aEmg = artefacts.emg,
        aBlink = artefacts.blink,
        aStall = artefacts.stall,
        streamStallMs = artefacts.stallMs,

        plotHistory = plotSeries,
        audioRunning = (audio != null),
        audioMuted = !shouldAudioRun,
        audioBaseDb = audio?.debugBaseDb ?: it.audioBaseDb,
      )
    }
  }
}
