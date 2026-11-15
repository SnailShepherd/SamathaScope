package com.mordin.samathascope

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

// Dashboard tab: headset, raw EEG, session, metric explorer and diagnostics.

@Composable
internal fun DashboardTab(
  ui: UiState,
  rawPreview: StateFlow<IntArray>,
  onGrantPermissions: () -> Unit,
  onRefreshDevices: () -> Unit,
  onSelectDevice: (String) -> Unit,
  onConnectToggle: () -> Unit,
  onDebugRawLoopEnabledChange: (Boolean) -> Unit,
  onStartSession: () -> Unit,
  onSessionModeChange: (SessionMode) -> Unit,
  onSkipCalibration: () -> Unit,
  onTogglePause: () -> Unit,
  onStopSession: () -> Unit,
  onSetFeedbackMetric: (PlotType) -> Unit,
  onToggleMetric: (PlotType) -> Unit,
  onFocusMetricInfo: (PlotType) -> Unit,
  onPanRawPlot: (Int) -> Unit,
  onResetRawPlot: () -> Unit,
  onPanMetricPlot: (Int) -> Unit,
  onResetMetricPlot: () -> Unit,
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
            onDebugRawLoopEnabledChange = onDebugRawLoopEnabledChange,
          )
          RawEegSection(
            ui = ui,
            rawPreview = rawPreview,
            onPan = onPanRawPlot,
            onResetToLatest = onResetRawPlot,
          )
          SessionSection(
            ui = ui,
            onStartSession = onStartSession,
            onSessionModeChange = onSessionModeChange,
            onSkipCalibration = onSkipCalibration,
            onTogglePause = onTogglePause,
            onStopSession = onStopSession,
            onSetFeedbackMetric = onSetFeedbackMetric,
            onStartArtefactCalibration = onStartArtefactCalibration,
            onLaterArtefactCalibration = onLaterArtefactCalibration,
            onSkipArtefactCalibration = onSkipArtefactCalibration,
          )
          MetricExplorerSection(
            ui = ui,
            onToggleMetric = onToggleMetric,
            onFocusMetricInfo = onFocusMetricInfo,
            onPanPlot = onPanMetricPlot,
            onResetToLatest = onResetMetricPlot,
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
          onDebugRawLoopEnabledChange = onDebugRawLoopEnabledChange,
        )
        RawEegSection(
          ui = ui,
          rawPreview = rawPreview,
          onPan = onPanRawPlot,
          onResetToLatest = onResetRawPlot,
        )
        SessionSection(
          ui = ui,
          onStartSession = onStartSession,
          onSessionModeChange = onSessionModeChange,
          onSkipCalibration = onSkipCalibration,
          onTogglePause = onTogglePause,
          onStopSession = onStopSession,
          onSetFeedbackMetric = onSetFeedbackMetric,
          onStartArtefactCalibration = onStartArtefactCalibration,
          onLaterArtefactCalibration = onLaterArtefactCalibration,
          onSkipArtefactCalibration = onSkipArtefactCalibration,
        )
        MetricExplorerSection(
          ui = ui,
          onToggleMetric = onToggleMetric,
          onFocusMetricInfo = onFocusMetricInfo,
          onPanPlot = onPanMetricPlot,
          onResetToLatest = onResetMetricPlot,
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
  onDebugRawLoopEnabledChange: (Boolean) -> Unit,
) {
  Panel {
    Text(stringResource(R.string.section_headset), fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        stringResource(
          when {
            ui.debugRawLoopEnabled -> R.string.debug_raw_replay_active
            ui.headsetConnecting -> R.string.connecting
            ui.connected -> R.string.headset_connected
            else -> R.string.headset_disconnected
          }
        )
      )
      if (!ui.btPermissionGranted && Build.VERSION.SDK_INT >= 31) {
        OutlinedButton(onClick = onGrantPermissions) {
          Text(stringResource(R.string.grant_permissions))
        }
      }
    }

    Spacer(Modifier.height(6.dp))
    HudRow(
      label = stringResource(R.string.headset_link_label),
      value = stringResource(
        when {
          ui.debugRawLoopEnabled -> R.string.debug_raw_replay_active
          ui.headsetConnecting -> R.string.connecting
          ui.connected -> R.string.headset_connected
          else -> R.string.headset_disconnected
        }
      ),
    )
    HudRow(
      label = stringResource(R.string.headset_stream_label),
      value = stringResource(eegStreamStatusLabel(ui.eegStreamStatus)),
    )
    Text(
      text = stringResource(
        if (ui.debugRawLoopEnabled && !ui.eegStreamReady) {
          R.string.headset_status_helper_debug_replay_waiting
        } else if (ui.headsetConnecting) {
          R.string.headset_status_helper_connecting
        } else {
          headsetStatusHelperText(ui.eegStreamStatus)
        }
      ),
      style = MaterialTheme.typography.bodySmall,
    )

    ui.connectionError?.let { message ->
      Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag("dashboard_connection_error"),
      )
    }

    Spacer(Modifier.height(6.dp))
    DevicePicker(
      devices = ui.bondedDevices,
      selectedMac = ui.selectedDeviceMac,
      enabled = ui.btPermissionGranted && !ui.connected && !ui.headsetConnecting && !ui.debugRawLoopEnabled,
      onSelect = onSelectDevice,
    )

    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      CompactActionButton(
        label = stringResource(
          when {
            ui.debugRawLoopEnabled -> R.string.debug_raw_replay_active
            ui.headsetConnecting -> R.string.connecting
            ui.connected -> R.string.disconnect
            else -> R.string.connect
          }
        ),
        enabled = ui.btPermissionGranted &&
          ui.selectedDeviceMac != null &&
          !ui.headsetConnecting &&
          !ui.debugRawLoopEnabled,
        filled = true,
        onClick = onConnectToggle,
        modifier = Modifier.weight(1f),
      )
      CompactActionButton(
        label = stringResource(R.string.refresh_devices),
        enabled = ui.btPermissionGranted && !ui.headsetConnecting && !ui.debugRawLoopEnabled,
        filled = false,
        onClick = onRefreshDevices,
        modifier = Modifier.weight(1f),
      )
    }

    if (ui.developerOptionsEnabled) {
      DebugReplayToggle(ui = ui, onDebugRawLoopEnabledChange = onDebugRawLoopEnabledChange)
    }
  }
}

@Composable
private fun DebugReplayToggle(ui: UiState, onDebugRawLoopEnabledChange: (Boolean) -> Unit) {
  Column {
    Spacer(Modifier.height(8.dp))
    if (ui.debugRawLoopAvailable) {
      LabeledCheckbox(
        checked = ui.debugRawLoopEnabled,
        label = stringResource(R.string.debug_raw_replay_use_saved),
        enabled = !ui.sessionRunning && !ui.debugRawLoopCapturing,
        modifier = Modifier.testTag("dashboard_debug_raw_replay_toggle"),
        onCheckedChange = onDebugRawLoopEnabledChange,
      )
      Text(
        text = if (ui.debugRawLoopEnabled) {
          stringResource(R.string.debug_raw_replay_status_active)
        } else {
          stringResource(R.string.debug_raw_replay_status_ready)
        },
        style = MaterialTheme.typography.bodySmall,
      )
    } else {
      Text(
        stringResource(R.string.debug_raw_replay_headset_missing),
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}

@Composable
private fun SessionSection(
  ui: UiState,
  onStartSession: () -> Unit,
  onSessionModeChange: (SessionMode) -> Unit,
  onSkipCalibration: () -> Unit,
  onTogglePause: () -> Unit,
  onStopSession: () -> Unit,
  onSetFeedbackMetric: (PlotType) -> Unit,
  onStartArtefactCalibration: () -> Unit,
  onLaterArtefactCalibration: () -> Unit,
  onSkipArtefactCalibration: () -> Unit,
) {
  Panel {
    Text(stringResource(R.string.section_session), fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.session_mode_label), style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      SessionMode.entries.forEach { mode ->
        CompactActionButton(
          label = stringResource(
            when (mode) {
              SessionMode.EYES_CLOSED -> R.string.session_mode_eyes_closed
              SessionMode.EYES_OPEN -> R.string.session_mode_eyes_open
            }
          ),
          enabled = !ui.sessionRunning,
          filled = ui.sessionMode == mode,
          onClick = { onSessionModeChange(mode) },
          modifier = Modifier.weight(1f),
        )
      }
    }
    Text(stringResource(R.string.session_mode_helper), style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      CompactActionButton(
        label = stringResource(R.string.start_session),
        enabled = ui.eegStreamReady && !ui.sessionRunning,
        filled = true,
        onClick = onStartSession,
        modifier = Modifier
          .weight(1f)
          .testTag("dashboard_start_session"),
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

    if (ui.pausedByAudioFocus) {
      Spacer(Modifier.height(8.dp))
      Text(
        stringResource(R.string.session_paused_audio_focus),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    }

    if (!ui.sessionRunning) {
      Spacer(Modifier.height(8.dp))
      Text(
        text = stringResource(
          when {
            ui.debugRawLoopEnabled && !ui.eegStreamReady -> R.string.session_start_blocked_debug_replay_waiting
            ui.debugRawLoopEnabled -> R.string.session_start_ready_debug_replay
            ui.headsetConnecting -> R.string.session_start_blocked_connecting
            !ui.connected -> R.string.session_start_blocked_disconnected
            ui.eegStreamStatus == EegStreamStatus.WRONG_RATE -> R.string.session_start_blocked_wrong_rate
            !ui.eegStreamReady -> R.string.session_start_blocked_waiting_for_stream
            else -> R.string.session_start_ready
          }
        ),
        style = MaterialTheme.typography.bodySmall,
      )
    }

    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.section_feedback_source), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(4.dp))
    FeedbackSourceDropdown(
      selected = ui.feedbackMetric,
      options = MetricGlossary.feedbackSourceMetrics(),
      onSelect = onSetFeedbackMetric,
    )

    Spacer(Modifier.height(8.dp))
    if (ui.sessionRunning && !ui.calibrating && ui.baselineProvisional) {
      Text(
        stringResource(R.string.calibration_provisional_baseline),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.testTag("dashboard_provisional_baseline"),
      )
      Spacer(Modifier.height(4.dp))
    }
    if (ui.calibrating) {
      Text(stringResource(R.string.calibrating_countdown, ui.calibrationRemainingSec), fontWeight = FontWeight.SemiBold)
      LinearProgressIndicator(
        progress = { (CALIBRATION_SECONDS - ui.calibrationRemainingSec).coerceIn(0, CALIBRATION_SECONDS) / CALIBRATION_SECONDS.toFloat() },
        modifier = Modifier.fillMaxWidth()
      )
      Text(ui.calibrationInstruction, style = MaterialTheme.typography.bodySmall)
      Text(stringResource(R.string.calibration_skip_helper), style = MaterialTheme.typography.bodySmall)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = onSkipCalibration,
          modifier = Modifier.testTag("dashboard_skip_calibration"),
        ) {
          Text(stringResource(R.string.skip_calibration))
        }
      }
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
private fun RawEegSection(
  ui: UiState,
  rawPreview: StateFlow<IntArray>,
  onPan: (Int) -> Unit,
  onResetToLatest: () -> Unit,
) {
  val settings = ui.plotSettings.getValue(PlotType.RAW)
  Panel {
    Text(stringResource(R.string.section_raw_eeg), fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    WaveformPlot(
      samples = rawPreview.collectAsStateWithLifecycle().value,
      yMin = settings.yMin,
      yMax = settings.yMax,
      heightDp = 120.dp,
      pannable = !ui.sessionRunning || ui.sessionPaused,
      onPan = onPan,
    )
    if (ui.rawPlotOffsetSeconds > 0) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        TextButton(onClick = onResetToLatest) {
          Text(stringResource(R.string.plot_latest))
        }
      }
    }
  }
}

@Composable
private fun MetricExplorerSection(
  ui: UiState,
  onToggleMetric: (PlotType) -> Unit,
  onFocusMetricInfo: (PlotType) -> Unit,
  onPanPlot: (Int) -> Unit,
  onResetToLatest: () -> Unit,
) {
  val infoEntry = MetricGlossary.entryFor(ui.selectedMetricInfo)
  val bringIntoViewRequester = remember { BringIntoViewRequester() }
  val coroutineScope = rememberCoroutineScope()

  Panel {
    Text(stringResource(R.string.section_live_plot), fontWeight = FontWeight.SemiBold)
    Text(stringResource(R.string.metric_explorer_hint), style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(6.dp))
    MultiMetricPlot(
      series = ui.metricPlotSeries,
      pannable = !ui.sessionRunning || ui.sessionPaused,
      onPan = onPanPlot,
    )
    Spacer(Modifier.height(8.dp))
    MetricChipRow(
      options = MetricGlossary.dashboardMetrics(),
      visibleMetrics = ui.visibleMetrics,
      onTap = onToggleMetric,
      onLongPress = {
        onFocusMetricInfo(it)
        coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
      },
    )
    if (ui.metricPlotOffsetSeconds > 0) {
      Spacer(Modifier.height(8.dp))
      TextButton(onClick = onResetToLatest) {
        Text(stringResource(R.string.plot_latest))
      }
    }

    Spacer(Modifier.height(8.dp))
    MetricExplainer(
      entry = infoEntry,
      modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester),
    )
  }
}

@Composable
private fun DiagnosticsSection(ui: UiState) {
  Panel {
    Text(stringResource(R.string.section_diagnostics), fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    ReliabilityBadge(ui)
    Spacer(Modifier.height(8.dp))
    HudRow(label = stringResource(R.string.state_label), value = stateLabelLabel(ui.displayedStateLabel))
    HudRow(label = stringResource(R.string.telemetry_poor_signal), value = "${ui.poorSignal}")
    HudRow(label = stringResource(R.string.telemetry_samples_per_second), value = "${ui.samplesPerSecond.roundToInt()}")
    HudRow(label = stringResource(R.string.telemetry_stall_ms), value = "${ui.streamStallMs} ms")
    if (ui.batteryPercent != null) {
      HudRow(label = stringResource(R.string.battery_label), value = "${ui.batteryPercent}%")
    }

    Spacer(Modifier.height(8.dp))
    MetricBar(label = stringResource(MetricGlossary.entryFor(PlotType.SETTLEDNESS).plainName), value = ui.settledness)
    MetricBar(label = stringResource(MetricGlossary.entryFor(PlotType.CONTROL).plainName), value = ui.control)
    MetricBar(label = stringResource(MetricGlossary.entryFor(PlotType.ALERTNESS).plainName), value = ui.alertness)
    MetricBar(label = stringResource(MetricGlossary.entryFor(PlotType.DROWSY_SCORE).plainName), value = ui.drowsyScore)
    MetricBar(label = stringResource(MetricGlossary.entryFor(PlotType.QUALITY_CONFIDENCE).plainName), value = ui.qualityConfidence)

    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.drowsy_contributors_title), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    SignedValueRow(label = stringResource(R.string.drowsy_term_tar), value = ui.drowsyTarContribution)
    SignedValueRow(label = stringResource(R.string.drowsy_term_tbr), value = ui.drowsyTbrContribution)
    SignedValueRow(label = stringResource(R.string.drowsy_term_entropy), value = ui.drowsyEntropyContribution)
    SignedValueRow(label = stringResource(R.string.drowsy_term_abr), value = ui.drowsyAbrContribution)

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
private fun ReliabilityBadge(ui: UiState) {
  val confidencePercent = (ui.qualityConfidence * 100f).roundToInt().coerceIn(0, 100)
  val level = when {
    ui.displayedStateLabel == StateLabel.SIGNAL_CONTAMINATED || ui.qualityConfidence < 0.40f -> ReliabilityLevel.LOW
    ui.qualityConfidence < 0.70f -> ReliabilityLevel.MEDIUM
    else -> ReliabilityLevel.HIGH
  }
  val title = when (level) {
    ReliabilityLevel.HIGH -> stringResource(R.string.reliability_state_high)
    ReliabilityLevel.MEDIUM -> stringResource(R.string.reliability_state_medium)
    ReliabilityLevel.LOW -> stringResource(R.string.reliability_state_low)
  }
  val message = when {
    ui.displayedStateLabel == StateLabel.SIGNAL_CONTAMINATED -> stringResource(R.string.reliability_message_contaminated)
    level == ReliabilityLevel.LOW -> stringResource(R.string.reliability_message_low)
    level == ReliabilityLevel.MEDIUM -> stringResource(R.string.reliability_message_medium)
    else -> stringResource(R.string.reliability_message_high)
  }

  val containerColor = when (level) {
    ReliabilityLevel.HIGH -> MaterialTheme.colorScheme.primaryContainer
    ReliabilityLevel.MEDIUM -> MaterialTheme.colorScheme.secondaryContainer
    ReliabilityLevel.LOW -> MaterialTheme.colorScheme.errorContainer
  }
  val contentColor = when (level) {
    ReliabilityLevel.HIGH -> MaterialTheme.colorScheme.onPrimaryContainer
    ReliabilityLevel.MEDIUM -> MaterialTheme.colorScheme.onSecondaryContainer
    ReliabilityLevel.LOW -> MaterialTheme.colorScheme.onErrorContainer
  }

  Card(
    colors = CardDefaults.cardColors(
      containerColor = containerColor,
      contentColor = contentColor,
    ),
    shape = RoundedCornerShape(10.dp),
  ) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
      Text(
        text = stringResource(R.string.reliability_label, title, confidencePercent),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Spacer(Modifier.height(2.dp))
      Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}

private enum class ReliabilityLevel {
  LOW,
  MEDIUM,
  HIGH,
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
        progress = {
          ((artefact.completedPrompts * ARTEFACT_PROMPT_SECONDS) + (ARTEFACT_PROMPT_SECONDS - artefact.remainingSec)) /
            (artefact.totalPrompts * ARTEFACT_PROMPT_SECONDS).toFloat()
        },
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
          TextButton(
            onClick = onSkipArtefactCalibration,
            modifier = Modifier.testTag("dashboard_skip_artefact_calibration"),
          ) {
            Text(stringResource(R.string.artifact_calibration_skip))
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricChipRow(
  options: List<PlotType>,
  visibleMetrics: Set<PlotType>,
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
      Box(
        modifier = Modifier
          .border(
            width = if (isVisible) 2.dp else 1.dp,
            color = if (isVisible) metricColorFor(option) else MaterialTheme.colorScheme.outline,
            shape = RoundedCornerShape(20.dp),
          )
          .background(
            color = if (isVisible) metricColorFor(option).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
          )
          .combinedClickable(
            onClick = { onTap(option) },
            onLongClick = { onLongPress(option) },
          )
          .padding(horizontal = 12.dp, vertical = 8.dp)
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          Canvas(modifier = Modifier.width(10.dp).height(10.dp)) {
            drawCircle(color = metricColorFor(option))
          }
          Text(stringResource(entry.plainName), style = MaterialTheme.typography.labelMedium)
        }
      }
    }
  }
}

@Composable
private fun FeedbackSourceDropdown(
  options: List<PlotType>,
  selected: PlotType,
  onSelect: (PlotType) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }

  Box(modifier = Modifier.fillMaxWidth()) {
    OutlinedButton(
      onClick = { expanded = true },
      modifier = Modifier
        .fillMaxWidth()
        .height(40.dp),
      border = BorderStroke(2.dp, metricColorFor(selected)),
    ) {
      Text(stringResource(MetricGlossary.entryFor(selected).plainName))
    }

    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      modifier = Modifier.fillMaxWidth(),
    ) {
      options.forEach { option ->
        DropdownMenuItem(
          text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
              Canvas(modifier = Modifier.width(10.dp).height(10.dp)) {
                drawCircle(metricColorFor(option))
              }
              Text(stringResource(MetricGlossary.entryFor(option).plainName))
            }
          },
          onClick = {
            onSelect(option)
            expanded = false
          }
        )
      }
    }
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

private fun eegStreamStatusLabel(status: EegStreamStatus): Int {
  return when (status) {
    EegStreamStatus.DISCONNECTED -> R.string.eeg_stream_disconnected
    EegStreamStatus.WAITING_FOR_RAW -> R.string.eeg_stream_waiting_for_raw
    EegStreamStatus.CONFIRMING -> R.string.eeg_stream_confirming
    EegStreamStatus.LIVE -> R.string.eeg_stream_live
    EegStreamStatus.DEBUG_REPLAY -> R.string.eeg_stream_debug_replay
    EegStreamStatus.STALLED -> R.string.eeg_stream_stalled
    EegStreamStatus.WRONG_RATE -> R.string.eeg_stream_wrong_rate
  }
}

private fun headsetStatusHelperText(status: EegStreamStatus): Int {
  return when (status) {
    EegStreamStatus.DISCONNECTED -> R.string.headset_status_helper_disconnected
    EegStreamStatus.WAITING_FOR_RAW -> R.string.headset_status_helper_waiting_for_raw
    EegStreamStatus.CONFIRMING -> R.string.headset_status_helper_confirming
    EegStreamStatus.LIVE -> R.string.headset_status_helper_live
    EegStreamStatus.DEBUG_REPLAY -> R.string.headset_status_helper_debug_replay
    EegStreamStatus.STALLED -> R.string.headset_status_helper_stalled
    EegStreamStatus.WRONG_RATE -> R.string.headset_status_helper_wrong_rate
  }
}
