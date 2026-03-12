package com.mordin.samathascope

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

private val metricPalette = listOf(
  Color(0xFF0B8F94),
  Color(0xFFE08A1E),
  Color(0xFF4F6BED),
  Color(0xFF4E9F3D),
  Color(0xFFC15F7A),
  Color(0xFF7A6FF0),
)

@Composable
fun App(vm: MainViewModel) {
  MainScreen(vm)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(vm: MainViewModel) {
  val ui by vm.ui.collectAsState()

  val permissions = remember {
    buildList {
      if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
      if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_SCAN)
    }.toTypedArray()
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { result ->
    vm.onPermissionsResult(result)
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.app_title),
            fontWeight = FontWeight.SemiBold,
          )
        },
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .padding(padding)
        .fillMaxSize()
    ) {
      TabRow(selectedTabIndex = ui.selectedTab.ordinal) {
        AppTab.entries.forEach { tab ->
          Tab(
            selected = ui.selectedTab == tab,
            onClick = { vm.selectTab(tab) },
            text = { Text(tabLabel(tab)) }
          )
        }
      }

      when (ui.selectedTab) {
        AppTab.DASHBOARD -> DashboardTab(
          ui = ui,
          onGrantPermissions = { permissionLauncher.launch(permissions) },
          onRefreshDevices = vm::refreshBondedDevices,
          onSelectDevice = vm::selectDevice,
          onConnectToggle = { if (ui.connected) vm.disconnect() else vm.connect() },
          onStartSession = vm::startSession,
          onTogglePause = vm::togglePause,
          onStopSession = vm::stopSession,
          onToggleMetric = vm::toggleVisibleMetric,
          onFocusMetricInfo = vm::focusMetricInfo,
          onSetFeedbackMetric = vm::setFeedbackMetric,
          onStartArtefactCalibration = vm::startArtefactCalibration,
          onLaterArtefactCalibration = vm::dismissArtefactCalibrationOffer,
          onSkipArtefactCalibration = vm::skipArtefactCalibration,
        )

        AppTab.SETTINGS -> SettingsTab(
          ui = ui,
          onAudioEnabledChange = vm::setAudioEnabled,
          onInvertRewardChange = vm::setInvertReward,
          onCrackleEnabledChange = vm::setCrackleEnabled,
          onGammaChange = vm::setGamma,
          onGMinDbChange = vm::setGMinDb,
          onGMaxDbChange = vm::setGMaxDb,
          onCrackleIntensityChange = vm::setCrackleIntensity,
          onTestBeep = vm::testBeep,
          onMetricWindowChange = vm::setMetricWindowSeconds,
          onRecordingChange = vm::setRecordingEnabled,
          onNotch50Change = vm::setNotch50Enabled,
        )

        AppTab.GAME -> GameTab(
          ui = ui,
          onMetricChange = vm::setFeedbackMetric,
        )

        AppTab.LEARN -> LearnTab()
      }
    }
  }
}

@Composable
private fun tabLabel(tab: AppTab): String {
  return when (tab) {
    AppTab.DASHBOARD -> stringResource(R.string.tab_dashboard)
    AppTab.SETTINGS -> stringResource(R.string.tab_settings)
    AppTab.GAME -> stringResource(R.string.tab_game)
    AppTab.LEARN -> stringResource(R.string.tab_learn)
  }
}

@Composable
private fun DashboardTab(
  ui: UiState,
  onGrantPermissions: () -> Unit,
  onRefreshDevices: () -> Unit,
  onSelectDevice: (String) -> Unit,
  onConnectToggle: () -> Unit,
  onStartSession: () -> Unit,
  onTogglePause: () -> Unit,
  onStopSession: () -> Unit,
  onToggleMetric: (PlotType) -> Unit,
  onFocusMetricInfo: (PlotType) -> Unit,
  onSetFeedbackMetric: (PlotType) -> Unit,
  onStartArtefactCalibration: () -> Unit,
  onLaterArtefactCalibration: () -> Unit,
  onSkipArtefactCalibration: () -> Unit,
) {
  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(12.dp)
  ) {
    val wide = maxWidth > 820.dp

    if (wide) {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1.35f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          HeadsetSection(
            ui = ui,
            onGrantPermissions = onGrantPermissions,
            onRefreshDevices = onRefreshDevices,
            onSelectDevice = onSelectDevice,
            onConnectToggle = onConnectToggle,
          )
          SessionSection(
            ui = ui,
            onStartSession = onStartSession,
            onTogglePause = onTogglePause,
            onStopSession = onStopSession,
            onStartArtefactCalibration = onStartArtefactCalibration,
            onLaterArtefactCalibration = onLaterArtefactCalibration,
            onSkipArtefactCalibration = onSkipArtefactCalibration,
          )
          RawEegSection(ui)
          MetricExplorerSection(
            ui = ui,
            onToggleMetric = onToggleMetric,
            onFocusMetricInfo = onFocusMetricInfo,
            onSetFeedbackMetric = onSetFeedbackMetric,
          )
        }
        Column(modifier = Modifier.weight(0.95f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          DiagnosticsSection(ui)
        }
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HeadsetSection(
          ui = ui,
          onGrantPermissions = onGrantPermissions,
          onRefreshDevices = onRefreshDevices,
          onSelectDevice = onSelectDevice,
          onConnectToggle = onConnectToggle,
        )
        SessionSection(
          ui = ui,
          onStartSession = onStartSession,
          onTogglePause = onTogglePause,
          onStopSession = onStopSession,
          onStartArtefactCalibration = onStartArtefactCalibration,
          onLaterArtefactCalibration = onLaterArtefactCalibration,
          onSkipArtefactCalibration = onSkipArtefactCalibration,
        )
        RawEegSection(ui)
        MetricExplorerSection(
          ui = ui,
          onToggleMetric = onToggleMetric,
          onFocusMetricInfo = onFocusMetricInfo,
          onSetFeedbackMetric = onSetFeedbackMetric,
        )
        DiagnosticsSection(ui)
      }
    }
  }
}

@Composable
private fun HeadsetSection(
  ui: UiState,
  onGrantPermissions: () -> Unit,
  onRefreshDevices: () -> Unit,
  onSelectDevice: (String) -> Unit,
  onConnectToggle: () -> Unit,
) {
  Panel {
    Text(stringResource(R.string.section_headset), fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(stringResource(if (ui.connected) R.string.headset_connected else R.string.headset_disconnected))
      if (!ui.btPermissionGranted && Build.VERSION.SDK_INT >= 31) {
        OutlinedButton(onClick = onGrantPermissions) {
          Text(stringResource(R.string.grant_permissions))
        }
      }
    }

    Spacer(Modifier.height(6.dp))
    DevicePicker(
      devices = ui.bondedDevices,
      selectedMac = ui.selectedDeviceMac,
      enabled = ui.btPermissionGranted && !ui.connected,
      onSelect = onSelectDevice,
    )

    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      CompactActionButton(
        label = stringResource(if (ui.connected) R.string.disconnect else R.string.connect),
        enabled = ui.btPermissionGranted && ui.selectedDeviceMac != null,
        filled = true,
        onClick = onConnectToggle,
        modifier = Modifier.weight(1f),
      )
      CompactActionButton(
        label = stringResource(R.string.refresh_devices),
        enabled = ui.btPermissionGranted,
        filled = false,
        onClick = onRefreshDevices,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun SessionSection(
  ui: UiState,
  onStartSession: () -> Unit,
  onTogglePause: () -> Unit,
  onStopSession: () -> Unit,
  onStartArtefactCalibration: () -> Unit,
  onLaterArtefactCalibration: () -> Unit,
  onSkipArtefactCalibration: () -> Unit,
) {
  Panel {
    Text(stringResource(R.string.section_session), fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      CompactActionButton(
        label = stringResource(R.string.start_session),
        enabled = ui.connected && !ui.sessionRunning,
        filled = true,
        onClick = onStartSession,
        modifier = Modifier.weight(1f),
      )
      CompactActionButton(
        label = stringResource(if (ui.sessionPaused) R.string.resume_session else R.string.pause_session),
        enabled = ui.sessionRunning,
        filled = false,
        onClick = onTogglePause,
        modifier = Modifier.weight(1f),
      )
      CompactActionButton(
        label = stringResource(R.string.stop_session),
        enabled = ui.sessionRunning,
        filled = false,
        onClick = onStopSession,
        modifier = Modifier.weight(1f),
      )
    }

    Spacer(Modifier.height(8.dp))
    if (ui.calibrating) {
      Text(stringResource(R.string.calibrating_countdown, ui.calibrationRemainingSec), fontWeight = FontWeight.SemiBold)
      LinearProgressIndicator(
        progress = ((60 - ui.calibrationRemainingSec).coerceIn(0, 60) / 60f),
        modifier = Modifier.fillMaxWidth()
      )
      Text(ui.calibrationInstruction, style = MaterialTheme.typography.bodySmall)
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
        HudRow(label = stringResource(R.string.session_elapsed_label), value = "${ui.sessionElapsedSec}s")
        HudRow(label = stringResource(R.string.session_state_label), value = stateLabelLabel(ui.displayedStateLabel))
        HudRow(label = stringResource(R.string.session_avg_proxy_label), value = "${(ui.avgMeditationProxy * 100).roundToInt()}%")
        HudRow(label = stringResource(R.string.session_over_80_label), value = "${ui.timeMeditationProxyOver80Seconds}s")
      }
    }

    ArtefactCalibrationCallout(
      ui = ui,
      onStartArtefactCalibration = onStartArtefactCalibration,
      onLaterArtefactCalibration = onLaterArtefactCalibration,
      onSkipArtefactCalibration = onSkipArtefactCalibration,
    )
  }
}

@Composable
private fun RawEegSection(ui: UiState) {
  val settings = ui.plotSettings.getValue(PlotType.RAW)
  Panel {
    Text(stringResource(R.string.section_raw_eeg), fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    WaveformPlot(
      samples = ui.rawPreview,
      yMin = settings.yMin,
      yMax = settings.yMax,
      heightDp = 120.dp,
    )
  }
}

@Composable
private fun MetricExplorerSection(
  ui: UiState,
  onToggleMetric: (PlotType) -> Unit,
  onFocusMetricInfo: (PlotType) -> Unit,
  onSetFeedbackMetric: (PlotType) -> Unit,
) {
  val infoEntry = MetricGlossary.entryFor(ui.selectedMetricInfo)

  Panel {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(stringResource(R.string.section_live_plot), fontWeight = FontWeight.SemiBold)
      ValueText(stringResource(R.string.feedback_source_label, MetricGlossary.entryFor(ui.feedbackMetric).plainName))
    }
    Text(stringResource(R.string.metric_explorer_hint), style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(6.dp))

    MetricChipRow(
      options = MetricGlossary.dashboardMetrics(),
      visibleMetrics = ui.visibleMetrics,
      feedbackMetric = ui.feedbackMetric,
      onTap = {
        onToggleMetric(it)
        onFocusMetricInfo(it)
      },
      onLongPress = {
        onFocusMetricInfo(it)
        if (MetricGlossary.entryFor(it).canBeFeedbackSource) {
          onSetFeedbackMetric(it)
        }
      },
    )

    Spacer(Modifier.height(10.dp))
    MultiMetricPlot(series = ui.metricPlotSeries)

    Spacer(Modifier.height(8.dp))
    MetricExplainer(entry = infoEntry)
  }
}

@Composable
private fun SettingsTab(
  ui: UiState,
  onAudioEnabledChange: (Boolean) -> Unit,
  onInvertRewardChange: (Boolean) -> Unit,
  onCrackleEnabledChange: (Boolean) -> Unit,
  onGammaChange: (Float) -> Unit,
  onGMinDbChange: (Int) -> Unit,
  onGMaxDbChange: (Int) -> Unit,
  onCrackleIntensityChange: (Float) -> Unit,
  onTestBeep: () -> Unit,
  onMetricWindowChange: (Int) -> Unit,
  onRecordingChange: (Boolean) -> Unit,
  onNotch50Change: (Boolean) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Panel {
      Text(stringResource(R.string.settings_audio_title), fontWeight = FontWeight.SemiBold)
      LabeledCheckbox(
        checked = ui.audioEnabled,
        label = stringResource(R.string.audio_enabled),
        onCheckedChange = onAudioEnabledChange,
      )
      LabeledCheckbox(
        checked = ui.invertReward,
        label = stringResource(R.string.invert_reward),
        onCheckedChange = onInvertRewardChange,
      )
      LabeledCheckbox(
        checked = ui.crackleEnabled,
        label = stringResource(R.string.crackle_overlay),
        onCheckedChange = onCrackleEnabledChange,
      )
      Text(stringResource(R.string.crackle_helper), style = MaterialTheme.typography.bodySmall)
      Text(stringResource(R.string.gamma_value, ui.gamma), style = MaterialTheme.typography.bodySmall)
      Slider(value = ui.gamma, onValueChange = onGammaChange, valueRange = 0.6f..3.0f)
      Text(stringResource(R.string.base_noise_range, ui.gMinDb, ui.gMaxDb), style = MaterialTheme.typography.bodySmall)
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(modifier = Modifier.weight(1f)) {
          Text(stringResource(R.string.base_noise_min), style = MaterialTheme.typography.labelSmall)
          Slider(value = ui.gMinDb.toFloat(), onValueChange = { onGMinDbChange(it.roundToInt()) }, valueRange = -60f..-10f)
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(stringResource(R.string.base_noise_max), style = MaterialTheme.typography.labelSmall)
          Slider(value = ui.gMaxDb.toFloat(), onValueChange = { onGMaxDbChange(it.roundToInt()) }, valueRange = -30f..0f)
        }
      }
      Text(stringResource(R.string.crackle_intensity, ui.crackleIntensity), style = MaterialTheme.typography.bodySmall)
      Slider(value = ui.crackleIntensity, onValueChange = onCrackleIntensityChange, valueRange = 0f..1f)
      OutlinedButton(onClick = onTestBeep) {
        Text(stringResource(R.string.test_beep))
      }
    }

    Panel {
      Text(stringResource(R.string.settings_plot_title), fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(6.dp))
      WindowSecondsDropdown(
        selectedSeconds = ui.plotSettings.getValue(PlotType.MEDITATION_PROXY).windowSeconds,
        options = listOf(60, 180, 300, 600),
        onSelected = onMetricWindowChange,
      )
      Text(stringResource(R.string.settings_plot_helper), style = MaterialTheme.typography.bodySmall)
    }

    Panel {
      Text(stringResource(R.string.settings_data_title), fontWeight = FontWeight.SemiBold)
      LabeledCheckbox(
        checked = ui.recordingEnabled,
        label = stringResource(R.string.record_local),
        onCheckedChange = onRecordingChange,
      )
      LabeledCheckbox(
        checked = ui.notch50Enabled,
        label = stringResource(R.string.notch_50_enabled),
        onCheckedChange = onNotch50Change,
      )
      Text(stringResource(R.string.notch_50_helper), style = MaterialTheme.typography.bodySmall)
      ui.lastRecordingPath?.let {
        Text(stringResource(R.string.saved_under, it), style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}

@Composable
private fun GameTab(
  ui: UiState,
  onMetricChange: (PlotType) -> Unit,
) {
  val altitudeAnim = remember { Animatable(ui.gameState.altitude) }

  LaunchedEffect(ui.gameState.altitude) {
    altitudeAnim.animateTo(
      targetValue = ui.gameState.altitude,
      animationSpec = tween(durationMillis = 240)
    )
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Panel {
      Text(stringResource(R.string.game_scene_title), fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(8.dp))
      LanternScene(
        altitude = altitudeAnim.value,
        glow = ui.gameHudState.metricValuePercent / 100f,
      )
      Spacer(Modifier.height(8.dp))
      HudRow(label = stringResource(R.string.game_hud_metric), value = "${ui.gameHudState.metricValuePercent}%")
      HudRow(label = stringResource(R.string.game_hud_artefact), value = "${ui.gameHudState.artefactPercent}%")
      HudRow(label = stringResource(R.string.game_hud_state), value = stateLabelLabel(ui.gameHudState.stateLabel))
      HudRow(label = stringResource(R.string.game_hud_signal), value = "${ui.gameHudState.poorSignal}")
      HudRow(label = stringResource(R.string.game_hud_elapsed), value = "${ui.gameHudState.elapsedSeconds}s")
      if (shouldShowBatteryRow(ui.gameHudState.batteryPercent)) {
        HudRow(label = stringResource(R.string.game_hud_battery), value = "${ui.gameHudState.batteryPercent}%")
      }
    }

    Panel {
      Text(stringResource(R.string.section_feedback_source), fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(6.dp))
      MetricSourceRow(
        options = MetricGlossary.feedbackSourceMetrics(),
        selected = ui.feedbackMetric,
        onSelect = onMetricChange,
      )
    }
  }
}

@Composable
private fun DiagnosticsSection(ui: UiState) {
  Panel {
    Text(stringResource(R.string.section_diagnostics), fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    HudRow(label = stringResource(R.string.state_label), value = stateLabelLabel(ui.displayedStateLabel))
    HudRow(label = stringResource(R.string.telemetry_poor_signal), value = "${ui.poorSignal}")
    HudRow(label = stringResource(R.string.telemetry_samples_per_second), value = "${ui.samplesPerSecond.roundToInt()}")
    HudRow(label = stringResource(R.string.telemetry_stall_ms), value = "${ui.streamStallMs} ms")

    Spacer(Modifier.height(8.dp))
    MetricBar(label = MetricGlossary.entryFor(PlotType.SETTLEDNESS).plainName, value = ui.settledness)
    MetricBar(label = MetricGlossary.entryFor(PlotType.CONTROL).plainName, value = ui.control)
    MetricBar(label = MetricGlossary.entryFor(PlotType.ALERTNESS).plainName, value = ui.alertness)
    MetricBar(label = MetricGlossary.entryFor(PlotType.DROWSY_SCORE).plainName, value = ui.drowsyScore)
    MetricBar(label = MetricGlossary.entryFor(PlotType.QUALITY_CONFIDENCE).plainName, value = ui.qualityConfidence)

    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.drowsy_contributors_title), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    SignedValueRow(label = "Theta/Alpha ratio (TAR)", value = ui.drowsyTarContribution)
    SignedValueRow(label = "Theta/Beta ratio (TBR)", value = ui.drowsyTbrContribution)
    SignedValueRow(label = "Spectral entropy", value = ui.drowsyEntropyContribution)
    SignedValueRow(label = "Alpha/Beta ratio (ABR)", value = ui.drowsyAbrContribution)

    Spacer(Modifier.height(8.dp))
    MetricBar(stringResource(R.string.diagnostic_contact), ui.artefactContact)
    MetricBar(stringResource(R.string.diagnostic_line), ui.artefactLine)
    MetricBar(stringResource(R.string.diagnostic_emg), ui.artefactEmg)
    MetricBar(stringResource(R.string.diagnostic_blink), ui.artefactBlink)
    MetricBar(stringResource(R.string.diagnostic_clip), ui.artefactClip)
    MetricBar(stringResource(R.string.diagnostic_stall), ui.artefactStall)
    MetricBar(stringResource(R.string.diagnostic_total), ui.artefactScore, emphasise = true)
    HudRow(
      label = stringResource(R.string.personalized_blink_label),
      value = "${String.format(Locale.US, "%.2f", ui.artefactBlinkNormalizationHz)} Hz"
    )
    HudRow(
      label = stringResource(R.string.personalized_emg_label),
      value = String.format(Locale.US, "%.2f", ui.artefactEmgNormalizationHfRatio)
    )

    Spacer(Modifier.height(8.dp))
    ValueText(
      stringResource(
        R.string.esense_line,
        ui.attention,
        ui.meditation,
      )
    )
  }
}

@Composable
private fun LearnTab() {
  val uriHandler = LocalUriHandler.current

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    LearnCard(
      title = stringResource(R.string.learn_samatha_title),
      body = stringResource(R.string.learn_samatha_body),
    )
    LearnCard(
      title = stringResource(R.string.learn_score_title),
      body = stringResource(R.string.learn_score_body),
    )
    LearnCard(
      title = stringResource(R.string.learn_metric_difference_title),
      body = stringResource(R.string.learn_metric_difference_body),
    )
    LearnCard(
      title = stringResource(R.string.learn_calibration_title),
      body = stringResource(R.string.learn_calibration_body),
    )
    LearnCard(
      title = stringResource(R.string.learn_artifact_calibration_title),
      body = stringResource(R.string.learn_artifact_calibration_body),
    )
    LearnCard(
      title = stringResource(R.string.learn_caveat_title),
      body = stringResource(R.string.learn_caveat_body),
    )

    MetricGlossary.learnEntries().forEach { entry ->
      LearnCard(
        title = "${entry.plainName} (${entry.abbreviation})",
        body = buildString {
          append(entry.shortMeaning)
          append("\n\n")
          append("What usually raises it: ")
          append(entry.drivers)
          append("\n\n")
          append(entry.longMeaning)
          append("\n\nFormula: ")
          append(entry.formula)
        }
      )
    }

    Panel {
      Text(stringResource(R.string.learn_sources_title), fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(8.dp))
      Text(stringResource(R.string.learn_sources_intro), style = MaterialTheme.typography.bodySmall)
      Spacer(Modifier.height(8.dp))

      TextButton(onClick = { uriHandler.openUri("https://www.accesstoinsight.org/tipitaka/an/an11/an11.002.than.html") }) {
        Text(stringResource(R.string.learn_source_1))
      }
      TextButton(onClick = { uriHandler.openUri("https://suttacentral.net/an4.41/en/sujato") }) {
        Text(stringResource(R.string.learn_source_2))
      }
      TextButton(onClick = { uriHandler.openUri("https://www.dhammatalks.org/books/WithEachAndEveryBreath/Section0005.html") }) {
        Text(stringResource(R.string.learn_source_3))
      }
    }
  }
}

@Composable
private fun ArtefactCalibrationCallout(
  ui: UiState,
  onStartArtefactCalibration: () -> Unit,
  onLaterArtefactCalibration: () -> Unit,
  onSkipArtefactCalibration: () -> Unit,
) {
  val artefact = ui.artefactCalibrationState
  if (ui.calibrating) return

  when {
    artefact.running -> {
      Spacer(Modifier.height(8.dp))
      Text(stringResource(R.string.artifact_calibration_title), fontWeight = FontWeight.SemiBold)
      Text(artefact.promptLabel, style = MaterialTheme.typography.bodySmall)
      LinearProgressIndicator(
        progress = ((artefact.completedPrompts * 5f) + (5 - artefact.remainingSec)) / (artefact.totalPrompts * 5f),
        modifier = Modifier.fillMaxWidth()
      )
      Text(stringResource(R.string.artifact_calibration_remaining, artefact.remainingSec), style = MaterialTheme.typography.bodySmall)
    }

    artefact.completed -> {
      Spacer(Modifier.height(8.dp))
      Text(stringResource(R.string.artifact_calibration_complete), style = MaterialTheme.typography.bodySmall)
    }

    artefact.available && !artefact.skipped -> {
      Spacer(Modifier.height(8.dp))
      Text(stringResource(R.string.artifact_calibration_prompt), style = MaterialTheme.typography.bodySmall)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onStartArtefactCalibration) {
          Text(stringResource(R.string.artifact_calibration_run))
        }
        if (!artefact.dismissed) {
          TextButton(onClick = onLaterArtefactCalibration) {
            Text(stringResource(R.string.artifact_calibration_later))
          }
          TextButton(onClick = onSkipArtefactCalibration) {
            Text(stringResource(R.string.artifact_calibration_skip))
          }
        }
      }
    }
  }
}

@Composable
private fun LearnCard(title: String, body: String) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    shape = RoundedCornerShape(4.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(title, fontWeight = FontWeight.SemiBold)
      Text(body, style = MaterialTheme.typography.bodySmall)
    }
  }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(4.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
      content = content,
    )
  }
}

@Composable
private fun CompactActionButton(
  label: String,
  enabled: Boolean,
  filled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (filled) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.height(40.dp)) {
      Text(label)
    }
  } else {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.height(40.dp)) {
      Text(label)
    }
  }
}

@Composable
private fun ValueText(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    fontFamily = FontFamily.Monospace,
    maxLines = 1,
  )
}

@Composable
private fun LabeledCheckbox(
  checked: Boolean,
  label: String,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    Text(label, style = MaterialTheme.typography.bodySmall)
  }
}

@Composable
private fun HudRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall)
    ValueText(value)
  }
}

@Composable
private fun SignedValueRow(label: String, value: Float) {
  HudRow(label = label, value = String.format("%+.2f", value))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricChipRow(
  options: List<PlotType>,
  visibleMetrics: Set<PlotType>,
  feedbackMetric: PlotType,
  onTap: (PlotType) -> Unit,
  onLongPress: (PlotType) -> Unit,
) {
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    options.forEach { option ->
      val entry = MetricGlossary.entryFor(option)
      val isVisible = visibleMetrics.contains(option)
      val isSource = feedbackMetric == option
      Box(
        modifier = Modifier
          .border(
            width = if (isSource) 2.dp else 1.dp,
            color = if (isSource) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            shape = RoundedCornerShape(20.dp),
          )
          .background(
            color = if (isVisible) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
          )
          .combinedClickable(
            onClick = { onTap(option) },
            onLongClick = { onLongPress(option) },
          )
          .padding(horizontal = 12.dp, vertical = 8.dp)
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(entry.plainName, style = MaterialTheme.typography.labelMedium)
          if (isSource) {
            Text(stringResource(R.string.metric_source_short), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricSourceRow(
  options: List<PlotType>,
  selected: PlotType,
  onSelect: (PlotType) -> Unit,
) {
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    options.forEach { option ->
      val isSelected = option == selected
      OutlinedButton(
        onClick = { onSelect(option) },
        modifier = Modifier.height(40.dp),
        border = BorderStroke(2.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
      ) {
        Text(MetricGlossary.entryFor(option).plainName)
      }
    }
  }
}

@Composable
private fun MetricExplainer(entry: MetricGlossaryEntry) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text("${entry.plainName} (${entry.abbreviation})", fontWeight = FontWeight.SemiBold)
    Text(entry.shortMeaning, style = MaterialTheme.typography.bodySmall)
    Text("What usually raises it: ${entry.drivers}", style = MaterialTheme.typography.bodySmall)
    Text(
      text = if (entry.canBeFeedbackSource) {
        stringResource(R.string.metric_source_eligible)
      } else {
        stringResource(R.string.metric_source_plot_only)
      },
      style = MaterialTheme.typography.bodySmall,
    )
    Text(entry.formula, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
  }
}

@Composable
private fun DevicePicker(
  devices: List<BondedDevice>,
  selectedMac: String?,
  enabled: Boolean,
  onSelect: (String) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  val selectedName = devices.firstOrNull { it.mac == selectedMac }?.display
    ?: stringResource(R.string.select_device)

  Box(modifier = Modifier.fillMaxWidth()) {
    OutlinedButton(
      onClick = { if (enabled) expanded = true },
      enabled = enabled,
      modifier = Modifier
        .fillMaxWidth()
        .height(40.dp)
    ) {
      Text(selectedName, maxLines = 1)
    }

    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      modifier = Modifier.fillMaxWidth()
    ) {
      devices.forEach { device ->
        DropdownMenuItem(
          text = { Text(device.display) },
          onClick = {
            onSelect(device.mac)
            expanded = false
          }
        )
      }
    }
  }
}

@Composable
private fun stateLabelLabel(state: StateLabel): String {
  return when (state) {
    StateLabel.SIGNAL_CONTAMINATED -> stringResource(R.string.state_signal_contaminated)
    StateLabel.DROWSY -> stringResource(R.string.state_drowsy)
    StateLabel.SETTLED -> stringResource(R.string.state_settled)
    StateLabel.EFFORTFUL_FOCUS -> stringResource(R.string.state_effortful_focus)
    StateLabel.MIND_WANDERING -> stringResource(R.string.state_mind_wandering)
    StateLabel.UNCERTAIN -> stringResource(R.string.state_uncertain)
  }
}

@Composable
private fun MetricBar(label: String, value: Float, emphasise: Boolean = false) {
  Column {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(
        label,
        style = if (emphasise) {
          MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        } else {
          MaterialTheme.typography.bodySmall
        }
      )
      ValueText("${(value * 100).roundToInt()}%")
    }
    LinearProgressIndicator(
      progress = value.coerceIn(0f, 1f),
      modifier = Modifier.fillMaxWidth()
    )
  }
}

@Composable
private fun WaveformPlot(
  samples: List<Int>,
  yMin: Float,
  yMax: Float,
  heightDp: androidx.compose.ui.unit.Dp = 180.dp,
) {
  if (samples.size < 8) return

  val safeYMax = if (yMax <= yMin) yMin + 1f else yMax
  val colorScheme = MaterialTheme.colorScheme
  val centered = samples.map { it.toFloat().coerceIn(yMin, safeYMax) }
  val points = PlotMath.toPlotPoints(centered, yMin, safeYMax, width = 1f, height = 1f)

  Canvas(
    modifier = Modifier
      .fillMaxWidth()
      .height(heightDp)
      .background(colorScheme.surface)
      .border(1.dp, colorScheme.outline, RoundedCornerShape(4.dp))
      .padding(6.dp)
  ) {
    val w = size.width
    val h = size.height
    val zeroY = h - ((0f - yMin) / (safeYMax - yMin)) * h

    drawLine(
      color = colorScheme.outline.copy(alpha = 0.5f),
      start = Offset(0f, zeroY.coerceIn(0f, h)),
      end = Offset(w, zeroY.coerceIn(0f, h)),
      strokeWidth = 1f
    )

    val path = Path()
    points.forEachIndexed { index, point ->
      val x = point.x * w
      val y = point.y * h
      if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path = path, color = colorScheme.primary, style = Stroke(width = 2f))
  }
}

@Composable
private fun MultiMetricPlot(series: Map<PlotType, List<Float>>) {
  if (series.isEmpty()) return

  val colorScheme = MaterialTheme.colorScheme
  Canvas(
    modifier = Modifier
      .fillMaxWidth()
      .height(220.dp)
      .background(colorScheme.surface)
      .border(1.dp, colorScheme.outline, RoundedCornerShape(4.dp))
      .padding(6.dp)
  ) {
    val w = size.width
    val h = size.height

    for (i in 0..4) {
      val y = h * i / 4f
      drawLine(
        color = colorScheme.outline.copy(alpha = 0.25f),
        start = Offset(0f, y),
        end = Offset(w, y),
        strokeWidth = 1f,
      )
    }

    series.entries.forEachIndexed { index, entry ->
      val values = entry.value
      if (values.size < 2) return@forEachIndexed
      val points = PlotMath.toPlotPoints(values, 0f, 100f, width = 1f, height = 1f)
      val path = Path()
      points.forEachIndexed { pointIndex, point ->
        val x = point.x * w
        val y = point.y * h
        if (pointIndex == 0) path.moveTo(x, y) else path.lineTo(x, y)
      }
      drawPath(
        path = path,
        color = metricPalette[index % metricPalette.size],
        style = Stroke(width = if (entry.key == PlotType.MEDITATION_PROXY) 2.8f else 2.1f),
      )
    }
  }
}

@Composable
private fun LanternScene(altitude: Float, glow: Float) {
  val clampedAltitude = altitude.coerceIn(0f, 1f)
  val glowStrength = glow.coerceIn(0f, 1f)
  val colorScheme = MaterialTheme.colorScheme

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(260.dp)
      .background(
        brush = Brush.verticalGradient(
          listOf(
            Color(0xFFDCE9EA),
            colorScheme.surfaceVariant,
            colorScheme.surface,
          )
        ),
        shape = RoundedCornerShape(4.dp),
      )
      .border(1.dp, colorScheme.outline, RoundedCornerShape(4.dp))
  ) {
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .padding(8.dp)
    ) {
      val w = size.width
      val h = size.height
      val horizonY = h * 0.72f

      drawRect(
        brush = Brush.verticalGradient(
          listOf(
            Color(0x00FFFFFF),
            Color(0x22C9D7D9),
            Color(0x33AABEC0),
          )
        ),
        topLeft = Offset(0f, horizonY - h * 0.30f),
        size = androidx.compose.ui.geometry.Size(w, h * 0.40f),
      )

      drawLine(
        color = colorScheme.outline.copy(alpha = 0.5f),
        start = Offset(0f, horizonY),
        end = Offset(w, horizonY),
        strokeWidth = 2f,
      )

      drawCircle(
        color = Color(0x33F0B54D),
        radius = h * (0.12f + glowStrength * 0.04f),
        center = Offset(w * 0.5f, h * (0.65f - 0.42f * clampedAltitude)),
      )

      val lanternCenter = Offset(w * 0.5f, h * (0.72f - 0.50f * clampedAltitude))
      val lanternRadius = h * 0.07f
      drawCircle(
        color = Color(0xFFF4B64B),
        radius = lanternRadius,
        center = lanternCenter,
      )
      drawCircle(
        color = Color(0x66FFF4C7),
        radius = lanternRadius * (1.5f + glowStrength * 0.6f),
        center = lanternCenter,
      )
      drawLine(
        color = Color(0xFF8E6130),
        start = Offset(lanternCenter.x, lanternCenter.y + lanternRadius),
        end = Offset(lanternCenter.x, lanternCenter.y + lanternRadius + h * 0.08f),
        strokeWidth = 3f,
      )

      drawCircle(
        color = Color(0x55FFFFFF),
        radius = h * 0.18f,
        center = Offset(w * 0.2f, horizonY + h * 0.06f),
      )
      drawCircle(
        color = Color(0x33FFFFFF),
        radius = h * 0.12f,
        center = Offset(w * 0.78f, horizonY - h * 0.02f),
      )
    }
  }
}

@Composable
private fun WindowSecondsDropdown(
  selectedSeconds: Int,
  options: List<Int>,
  onSelected: (Int) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }

  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(stringResource(R.string.plot_window_label), style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.width(8.dp))
    Box {
      OutlinedButton(onClick = { expanded = true }) {
        Text(stringResource(R.string.seconds_value, selectedSeconds))
      }
      DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        options.forEach { seconds ->
          DropdownMenuItem(
            text = { Text(stringResource(R.string.seconds_value, seconds)) },
            onClick = {
              onSelected(seconds)
              expanded = false
            }
          )
        }
      }
    }
  }
}
