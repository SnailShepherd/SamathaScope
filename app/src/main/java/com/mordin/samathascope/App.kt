package com.mordin.samathascope

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

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
        actions = {
          if (ui.selectedTab == AppTab.DASHBOARD) {
            TextButton(onClick = { vm.setSettingsPanelVisible(true) }) {
              Text(stringResource(R.string.settings))
            }
          }
        }
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
        )

        AppTab.SIGNALS -> SignalsTab(
          ui = ui,
          onFeedbackMetricChange = vm::setFeedbackMetric,
          onPlotTypeChange = vm::setPlotType,
          onInvertRewardChange = vm::setInvertReward,
          onArtefactsReduceChange = vm::setArtefactsReduceScore,
          onCrackleEnabledChange = vm::setCrackleEnabled,
          onGammaChange = vm::setGamma,
          onGMinDbChange = vm::setGMinDb,
          onGMaxDbChange = vm::setGMaxDb,
          onCrackleIntensityChange = vm::setCrackleIntensity,
          onTestBeep = vm::testBeep,
          onPlotWindowChange = vm::setPlotWindowSeconds,
          onPlotYMinChange = vm::setPlotYMin,
          onPlotYMaxChange = vm::setPlotYMax,
          onPlotReset = vm::resetPlotSettings,
        )

        AppTab.GAME -> GameTab(
          ui = ui,
          onMetricChange = vm::setGameMetric,
        )

        AppTab.LEARN -> LearnTab()
      }
    }

    if (ui.settingsPanelVisible) {
      ModalBottomSheet(onDismissRequest = { vm.setSettingsPanelVisible(false) }) {
        SettingsSheet(
          ui = ui,
          onRecordingChange = vm::setRecordingEnabled,
          onClose = { vm.setSettingsPanelVisible(false) },
          onTestBeep = vm::testBeep,
        )
      }
    }
  }
}

@Composable
private fun tabLabel(tab: AppTab): String {
  return when (tab) {
    AppTab.DASHBOARD -> stringResource(R.string.tab_dashboard)
    AppTab.SIGNALS -> stringResource(R.string.tab_signals)
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
) {
  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(12.dp)
  ) {
    val wide = maxWidth > 760.dp

    if (wide) {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          ConnectionSection(
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
          )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          DashboardRawPlotSection(ui)
          DiagnosticsSection(ui)
        }
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ConnectionSection(
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
        )
        DashboardRawPlotSection(ui)
        DiagnosticsSection(ui)
      }
    }
    Spacer(modifier = Modifier.height(12.dp))
  }
}

@Composable
private fun ConnectionSection(
  ui: UiState,
  onGrantPermissions: () -> Unit,
  onRefreshDevices: () -> Unit,
  onSelectDevice: (String) -> Unit,
  onConnectToggle: () -> Unit,
) {
  Panel {
    Text(stringResource(R.string.section_headset), fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        stringResource(
          if (ui.connected) R.string.headset_connected else R.string.headset_disconnected
        )
      )
      if (!ui.btPermissionGranted && Build.VERSION.SDK_INT >= 31) {
        OutlinedButton(onClick = onGrantPermissions) {
          Text(stringResource(R.string.grant_permissions))
        }
      }
    }

    Spacer(Modifier.height(8.dp))

    DevicePicker(
      devices = ui.bondedDevices,
      selectedMac = ui.selectedDeviceMac,
      enabled = ui.btPermissionGranted && !ui.connected,
      onRefresh = onRefreshDevices,
      onSelect = onSelectDevice,
    )

    Spacer(Modifier.height(8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(
        onClick = onConnectToggle,
        enabled = ui.btPermissionGranted && ui.selectedDeviceMac != null,
        modifier = Modifier.weight(1f)
      ) {
        Text(stringResource(if (ui.connected) R.string.disconnect else R.string.connect))
      }
      OutlinedButton(onClick = onRefreshDevices, enabled = ui.btPermissionGranted, modifier = Modifier.weight(1f)) {
        Text(stringResource(R.string.refresh_devices))
      }
    }

    Spacer(Modifier.height(8.dp))
    ValueText(
      stringResource(
        R.string.telemetry_line,
        ui.poorSignal,
        ui.samplesPerSecond.roundToInt(),
        ui.streamStallMs,
      )
    )
  }
}

@Composable
private fun SessionSection(
  ui: UiState,
  onStartSession: () -> Unit,
  onTogglePause: () -> Unit,
  onStopSession: () -> Unit,
) {
  Panel {
    Text(stringResource(R.string.section_session), fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(
        onClick = onStartSession,
        enabled = ui.connected && !ui.sessionRunning,
        modifier = Modifier.weight(1f)
      ) {
        Text(stringResource(R.string.start_session))
      }
      OutlinedButton(
        onClick = onTogglePause,
        enabled = ui.sessionRunning,
        modifier = Modifier.weight(1f)
      ) {
        Text(stringResource(if (ui.sessionPaused) R.string.resume_session else R.string.pause_session))
      }
      OutlinedButton(
        onClick = onStopSession,
        enabled = ui.sessionRunning,
        modifier = Modifier.weight(1f)
      ) {
        Text(stringResource(R.string.stop_session))
      }
    }

    Spacer(Modifier.height(10.dp))

    if (ui.calibrating) {
      Text(
        stringResource(R.string.calibrating_countdown, ui.calibrationRemainingSec),
        fontWeight = FontWeight.SemiBold
      )
      LinearProgressIndicator(
        progress = ((60 - ui.calibrationRemainingSec).coerceIn(0, 60) / 60f),
        modifier = Modifier.fillMaxWidth()
      )
      Text(stringResource(R.string.calibration_hint), style = MaterialTheme.typography.bodySmall)
    } else if (ui.sessionRunning) {
      ValueText(
        stringResource(
          R.string.session_stats,
          ui.sessionElapsedSec,
          (ui.avgSamathaScore * 100).roundToInt(),
          ui.timeSamathaOver80Seconds,
        )
      )
    } else {
      Text(stringResource(R.string.idle_status), style = MaterialTheme.typography.bodySmall)
    }
  }
}

@Composable
private fun DashboardRawPlotSection(ui: UiState) {
  val settings = ui.plotSettings.getValue(PlotType.RAW)
  Panel {
    Text(stringResource(R.string.section_raw_eeg), fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    WaveformPlot(
      samples = ui.rawPreview,
      yMin = settings.yMin,
      yMax = settings.yMax,
    )
    Spacer(Modifier.height(6.dp))
    ValueText(
      stringResource(
        R.string.raw_range_label,
        settings.windowSeconds,
        settings.yMin.roundToInt(),
        settings.yMax.roundToInt(),
      )
    )
  }
}

@Composable
private fun DiagnosticsSection(ui: UiState) {
  Panel {
    Text(stringResource(R.string.section_diagnostics), fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    ArtefactBar(stringResource(R.string.diagnostic_contact), ui.artefactContact)
    ArtefactBar(stringResource(R.string.diagnostic_line), ui.artefactLine)
    ArtefactBar(stringResource(R.string.diagnostic_emg), ui.artefactEmg)
    ArtefactBar(stringResource(R.string.diagnostic_blink), ui.artefactBlink)
    ArtefactBar(stringResource(R.string.diagnostic_stall), ui.artefactStall)
    Spacer(Modifier.height(8.dp))
    ArtefactBar(stringResource(R.string.diagnostic_total), ui.artefactScore, emphasise = true)
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
private fun SignalsTab(
  ui: UiState,
  onFeedbackMetricChange: (PlotType) -> Unit,
  onPlotTypeChange: (PlotType) -> Unit,
  onInvertRewardChange: (Boolean) -> Unit,
  onArtefactsReduceChange: (Boolean) -> Unit,
  onCrackleEnabledChange: (Boolean) -> Unit,
  onGammaChange: (Float) -> Unit,
  onGMinDbChange: (Int) -> Unit,
  onGMaxDbChange: (Int) -> Unit,
  onCrackleIntensityChange: (Float) -> Unit,
  onTestBeep: () -> Unit,
  onPlotWindowChange: (PlotType, Int) -> Unit,
  onPlotYMinChange: (PlotType, Float) -> Unit,
  onPlotYMaxChange: (PlotType, Float) -> Unit,
  onPlotReset: (PlotType) -> Unit,
) {
  val metricTypes = remember {
    listOf(
      PlotType.SAMATHA_SCORE,
      PlotType.ESENSE_MEDITATION,
      PlotType.ESENSE_ATTENTION,
      PlotType.ARTEFACT_SCORE,
      PlotType.RELAXED_ALERTNESS_INDEX,
    )
  }

  val plotTypes = remember {
    listOf(
      PlotType.RAW,
      PlotType.SAMATHA_SCORE,
      PlotType.ARTEFACT_SCORE,
      PlotType.RELAXED_ALERTNESS_INDEX,
      PlotType.ESENSE_MEDITATION,
      PlotType.ESENSE_ATTENTION,
    )
  }

  val currentPlotSettings = ui.plotSettings.getValue(ui.plotType)

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Panel {
      Text(stringResource(R.string.section_feedback_source), fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(8.dp))
      ChoiceRow(
        options = metricTypes,
        selected = ui.feedbackMetric,
        onSelect = onFeedbackMetricChange,
      )

      Spacer(Modifier.height(10.dp))

      LabeledCheckbox(
        checked = ui.invertReward,
        label = stringResource(R.string.invert_reward),
        onCheckedChange = onInvertRewardChange,
      )
      LabeledCheckbox(
        checked = ui.artefactsReduceScore,
        label = stringResource(R.string.artefacts_reduce_score),
        onCheckedChange = onArtefactsReduceChange,
      )
      LabeledCheckbox(
        checked = ui.crackleEnabled,
        label = stringResource(R.string.crackle_overlay),
        onCheckedChange = onCrackleEnabledChange,
      )

      Spacer(Modifier.height(8.dp))

      Text(stringResource(R.string.gamma_value, ui.gamma), style = MaterialTheme.typography.bodySmall)
      Slider(value = ui.gamma, onValueChange = onGammaChange, valueRange = 0.6f..3.0f)

      Text(
        stringResource(R.string.base_noise_range, ui.gMinDb, ui.gMaxDb),
        style = MaterialTheme.typography.bodySmall
      )
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

      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onTestBeep, modifier = Modifier.weight(1f)) {
          Text(stringResource(R.string.test_beep))
        }
        StatusTag(stringResource(if (ui.audioRunning) R.string.audio_running else R.string.audio_off))
        StatusTag(stringResource(if (ui.audioMuted) R.string.audio_muted else R.string.audio_on))
        StatusTag(stringResource(R.string.audio_db, ui.audioBaseDb.roundToInt()))
      }
    }

    Panel {
      Text(stringResource(R.string.section_live_plot), fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(8.dp))
      ChoiceRow(
        options = plotTypes,
        selected = ui.plotType,
        onSelect = onPlotTypeChange,
      )

      Spacer(Modifier.height(10.dp))
      PlotSettingsEditor(
        plotType = ui.plotType,
        settings = currentPlotSettings,
        onWindowChange = { onPlotWindowChange(ui.plotType, it) },
        onYMinChange = { onPlotYMinChange(ui.plotType, it) },
        onYMaxChange = { onPlotYMaxChange(ui.plotType, it) },
        onReset = { onPlotReset(ui.plotType) },
      )

      Spacer(Modifier.height(10.dp))
      if (ui.plotType == PlotType.RAW) {
        WaveformPlot(
          samples = ui.rawPreview,
          yMin = currentPlotSettings.yMin,
          yMax = currentPlotSettings.yMax,
        )
      } else {
        MetricPlot(
          values = ui.plotHistory,
          yMin = currentPlotSettings.yMin,
          yMax = currentPlotSettings.yMax,
        )
      }
    }
  }
}

@Composable
private fun GameTab(
  ui: UiState,
  onMetricChange: (PlotType) -> Unit,
) {
  val gameMetrics = remember {
    listOf(
      PlotType.SAMATHA_SCORE,
      PlotType.ESENSE_MEDITATION,
      PlotType.ESENSE_ATTENTION,
      PlotType.ARTEFACT_SCORE,
      PlotType.RELAXED_ALERTNESS_INDEX,
    )
  }

  val altitudeAnim = remember { androidx.compose.animation.core.Animatable(ui.gameState.altitude) }

  LaunchedEffect(ui.gameState.altitude) {
    altitudeAnim.animateTo(
      targetValue = ui.gameState.altitude,
      animationSpec = androidx.compose.animation.core.tween(durationMillis = 180)
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
      Text(stringResource(R.string.section_game), fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(8.dp))
      ChoiceRow(
        options = gameMetrics,
        selected = ui.gameState.metric,
        onSelect = onMetricChange,
      )
    }

    Panel {
      Text(stringResource(R.string.game_scene_title), fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(8.dp))
      LevitationScene(altitude = altitudeAnim.value)
      Spacer(Modifier.height(8.dp))

      HudRow(label = stringResource(R.string.game_hud_metric), value = "${ui.gameHudState.metricValuePercent}%")
      HudRow(label = stringResource(R.string.game_hud_artefact), value = "${ui.gameHudState.artefactPercent}%")
      HudRow(label = stringResource(R.string.game_hud_signal), value = "${ui.gameHudState.poorSignal}")
      HudRow(label = stringResource(R.string.game_hud_elapsed), value = "${ui.gameHudState.elapsedSeconds}s")
      if (shouldShowBatteryRow(ui.gameHudState.batteryPercent)) {
        HudRow(label = stringResource(R.string.game_hud_battery), value = "${ui.gameHudState.batteryPercent}%")
      }
    }
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
      title = stringResource(R.string.learn_caveat_title),
      body = stringResource(R.string.learn_caveat_body),
    )
    LearnCard(
      title = stringResource(R.string.learn_wellbeing_title),
      body = stringResource(R.string.learn_wellbeing_body),
    )

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
private fun SettingsSheet(
  ui: UiState,
  onRecordingChange: (Boolean) -> Unit,
  onClose: () -> Unit,
  onTestBeep: () -> Unit,
) {
  Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
    Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
    LabeledCheckbox(
      checked = ui.recordingEnabled,
      label = stringResource(R.string.record_local),
      onCheckedChange = onRecordingChange,
    )
    ui.lastRecordingPath?.let {
      Text(stringResource(R.string.saved_under, it), style = MaterialTheme.typography.bodySmall)
    }

    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onTestBeep) {
      Text(stringResource(R.string.test_beep))
    }
    Spacer(Modifier.height(6.dp))
    TextButton(onClick = onClose) {
      Text(stringResource(R.string.close))
    }
    Spacer(Modifier.height(16.dp))
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
private fun ValueText(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    fontFamily = FontFamily.Monospace,
  )
}

@Composable
private fun StatusTag(text: String) {
  Box(
    modifier = Modifier
      .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
      .padding(horizontal = 8.dp, vertical = 6.dp)
  ) {
    ValueText(text)
  }
}

@Composable
private fun LabeledCheckbox(
  checked: Boolean,
  label: String,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    Text(label)
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
private fun LevitationScene(altitude: Float) {
  val clampedAltitude = altitude.coerceIn(0f, 1f)
  val colorScheme = MaterialTheme.colorScheme
  val outlineColor = colorScheme.outline
  val primaryColor = colorScheme.primary

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(240.dp)
      .border(1.dp, outlineColor, RoundedCornerShape(4.dp))
  ) {
    androidx.compose.foundation.Image(
      painter = androidx.compose.ui.res.painterResource(id = R.drawable.game_landscape_placeholder),
      contentDescription = null,
      contentScale = androidx.compose.ui.layout.ContentScale.Crop,
      modifier = Modifier.matchParentSize()
    )

    Canvas(
      modifier = Modifier
        .matchParentSize()
        .padding(6.dp)
    ) {
      val w = size.width
      val h = size.height

      val horizonY = h * 0.65f
      drawLine(
        color = outlineColor,
        start = Offset(0f, horizonY),
        end = Offset(w, horizonY),
        strokeWidth = 2f,
      )

      for (i in 1..4) {
        val y = h * i / 5f
        drawLine(
          color = outlineColor.copy(alpha = 0.35f),
          start = Offset(0f, y),
          end = Offset(w, y),
          strokeWidth = 1f,
        )
      }

      val markerX = w * 0.12f
      drawLine(
        color = outlineColor,
        start = Offset(markerX, 0f),
        end = Offset(markerX, h),
        strokeWidth = 2f,
      )

      val balloonRadius = h * 0.06f
      val y = h - (clampedAltitude * h)
      val x = w * 0.5f

      drawCircle(
        color = primaryColor,
        radius = balloonRadius,
        center = Offset(x, y),
      )
      drawLine(
        color = primaryColor,
        start = Offset(x, y + balloonRadius),
        end = Offset(x, y + balloonRadius + h * 0.08f),
        strokeWidth = 2f,
      )
    }
  }
}

@Composable
private fun DevicePicker(
  devices: List<BondedDevice>,
  selectedMac: String?,
  enabled: Boolean,
  onRefresh: () -> Unit,
  onSelect: (String) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  val selectedName = devices.firstOrNull { it.mac == selectedMac }?.display
    ?: stringResource(R.string.select_device)

  Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(modifier = Modifier.weight(1f)) {
      OutlinedButton(
        onClick = { if (enabled) expanded = true },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
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

    OutlinedButton(onClick = onRefresh, enabled = enabled) {
      Text(stringResource(R.string.refresh))
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(
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
        colors = ButtonDefaults.outlinedButtonColors(
          containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
          contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(
          2.dp,
          if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
      ) {
        Text(plotTypeLabel(option))
      }
    }
  }
}

@Composable
private fun plotTypeLabel(type: PlotType): String {
  return when (type) {
    PlotType.RAW -> stringResource(R.string.plot_raw)
    PlotType.SAMATHA_SCORE -> stringResource(R.string.plot_samatha_score)
    PlotType.ARTEFACT_SCORE -> stringResource(R.string.plot_artefact_score)
    PlotType.RELAXED_ALERTNESS_INDEX -> stringResource(R.string.plot_relaxed_alertness)
    PlotType.ESENSE_MEDITATION -> stringResource(R.string.plot_esense_meditation)
    PlotType.ESENSE_ATTENTION -> stringResource(R.string.plot_esense_attention)
  }
}

@Composable
private fun PlotSettingsEditor(
  plotType: PlotType,
  settings: PlotSettings,
  onWindowChange: (Int) -> Unit,
  onYMinChange: (Float) -> Unit,
  onYMaxChange: (Float) -> Unit,
  onReset: () -> Unit,
) {
  Text(stringResource(R.string.plot_settings_title), fontWeight = FontWeight.SemiBold)
  Spacer(Modifier.height(6.dp))

  val windowOptions = if (plotType == PlotType.RAW) {
    listOf(3, 5, 10, 20)
  } else {
    listOf(60, 180, 300, 600)
  }

  WindowSecondsDropdown(
    selectedSeconds = settings.windowSeconds,
    options = windowOptions,
    onSelected = onWindowChange,
  )

  Spacer(Modifier.height(8.dp))

  Text(
    stringResource(R.string.plot_y_range, settings.yMin.roundToInt(), settings.yMax.roundToInt()),
    style = MaterialTheme.typography.bodySmall,
  )

  if (plotType == PlotType.RAW) {
    Text(stringResource(R.string.plot_y_min), style = MaterialTheme.typography.labelSmall)
    Slider(value = settings.yMin, onValueChange = onYMinChange, valueRange = -4000f..-50f)
    Text(stringResource(R.string.plot_y_max), style = MaterialTheme.typography.labelSmall)
    Slider(value = settings.yMax, onValueChange = onYMaxChange, valueRange = 50f..4000f)
  } else {
    Text(stringResource(R.string.plot_y_min), style = MaterialTheme.typography.labelSmall)
    Slider(value = settings.yMin, onValueChange = onYMinChange, valueRange = 0f..99f)
    Text(stringResource(R.string.plot_y_max), style = MaterialTheme.typography.labelSmall)
    Slider(value = settings.yMax, onValueChange = onYMaxChange, valueRange = 1f..100f)
  }

  OutlinedButton(onClick = onReset) {
    Text(stringResource(R.string.reset_plot_settings))
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

@Composable
private fun ArtefactBar(label: String, value: Float, emphasise: Boolean = false) {
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
private fun WaveformPlot(samples: List<Int>, yMin: Float, yMax: Float) {
  if (samples.size < 8) return

  val safeYMax = if (yMax <= yMin) yMin + 1f else yMax
  val colorScheme = MaterialTheme.colorScheme
  val surfaceColor = colorScheme.surface
  val outlineColor = colorScheme.outline
  val primaryColor = colorScheme.primary

  val centered = samples.map { it.toFloat().coerceIn(yMin, safeYMax) }
  val points = PlotMath.toPlotPoints(
    values = centered,
    yMin = yMin,
    yMax = safeYMax,
    width = 1f,
    height = 1f,
  )

  Canvas(
    modifier = Modifier
      .fillMaxWidth()
      .height(190.dp)
      .background(surfaceColor)
      .border(1.dp, outlineColor, RoundedCornerShape(4.dp))
      .padding(6.dp)
  ) {
    val w = size.width
    val h = size.height
    val zeroY = h - ((0f - yMin) / (safeYMax - yMin)) * h

    drawLine(
      color = outlineColor.copy(alpha = 0.5f),
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

    drawPath(
      path = path,
      color = primaryColor,
      style = Stroke(width = 2f),
    )
  }
}

@Composable
private fun MetricPlot(values: List<Float>, yMin: Float, yMax: Float) {
  if (values.size < 2) return

  val safeYMax = if (yMax <= yMin) yMin + 1f else yMax
  val colorScheme = MaterialTheme.colorScheme
  val surfaceColor = colorScheme.surface
  val outlineColor = colorScheme.outline
  val primaryColor = colorScheme.primary

  val points = PlotMath.toPlotPoints(values, yMin, safeYMax, width = 1f, height = 1f)

  Canvas(
    modifier = Modifier
      .fillMaxWidth()
      .height(200.dp)
      .background(surfaceColor)
      .border(1.dp, outlineColor, RoundedCornerShape(4.dp))
      .padding(6.dp)
  ) {
    val w = size.width
    val h = size.height

    for (i in 0..4) {
      val y = h * i / 4f
      drawLine(
        color = outlineColor.copy(alpha = 0.35f),
        start = Offset(0f, y),
        end = Offset(w, y),
        strokeWidth = 1f,
      )
    }

    val path = Path()
    points.forEachIndexed { index, point ->
      val x = point.x * w
      val y = point.y * h
      if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    drawPath(
      path = path,
      color = primaryColor,
      style = Stroke(width = 2.2f),
    )
  }
}