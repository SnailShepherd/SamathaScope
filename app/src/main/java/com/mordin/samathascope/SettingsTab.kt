package com.mordin.samathascope

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// Settings tab: audio, plots, data and sharing, developer options, appearance.

@Composable
internal fun SettingsTab(
  ui: UiState,
  onAudioEnabledChange: (Boolean) -> Unit,
  onInvertRewardChange: (Boolean) -> Unit,
  onNoiseColorChange: (NoiseColor) -> Unit,
  onGammaChange: (Float) -> Unit,
  onGMinDbChange: (Int) -> Unit,
  onGMaxDbChange: (Int) -> Unit,
  onTestBeep: () -> Unit,
  onMetricWindowChange: (Int) -> Unit,
  onRecordingChange: (Boolean) -> Unit,
  onShareLatestSession: () -> Unit,
  onThemeModeChange: (ThemeMode) -> Unit,
  onDeveloperOptionsChange: (Boolean) -> Unit,
  onNotch50Change: (Boolean) -> Unit,
  onStartDebugRawLoopCapture: () -> Unit,
  onCancelDebugRawLoopCapture: () -> Unit,
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
      Spacer(Modifier.height(6.dp))
      Text(stringResource(R.string.noise_color_label), style = MaterialTheme.typography.bodySmall)
      Spacer(Modifier.height(6.dp))
      NoiseColorSelector(
        selected = ui.noiseColor,
        onSelect = onNoiseColorChange,
      )
      Spacer(Modifier.height(6.dp))
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
      OutlinedButton(onClick = onTestBeep) {
        Text(stringResource(R.string.test_beep))
      }
    }

    Panel {
      Text(stringResource(R.string.settings_plot_title), fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(6.dp))
      WindowSecondsSlider(
        selectedSeconds = ui.plotSettings.getValue(PlotType.MEDITATION_PROXY).windowSeconds,
        options = METRIC_WINDOW_OPTIONS,
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
      Spacer(Modifier.height(6.dp))
      OutlinedButton(
        onClick = onShareLatestSession,
        enabled = ui.recordedSessionAvailable && !ui.recordingActive,
        modifier = Modifier.testTag("settings_share_last_session"),
      ) {
        Text(stringResource(R.string.share_last_session))
      }
      Text(
        text = stringResource(
          when {
            !ui.recordedSessionAvailable -> R.string.share_none
            ui.recordingActive -> R.string.share_unavailable_recording
            else -> R.string.share_last_session_helper
          }
        ),
        style = MaterialTheme.typography.bodySmall,
      )

      if (ui.developerOptionsEnabled) {
        Spacer(Modifier.height(10.dp))
        DebugReplayCapture(
          ui = ui,
          onStartDebugRawLoopCapture = onStartDebugRawLoopCapture,
          onCancelDebugRawLoopCapture = onCancelDebugRawLoopCapture,
        )
      }
    }

    Panel {
      Text(stringResource(R.string.developer_options_title), fontWeight = FontWeight.SemiBold)
      LabeledCheckbox(
        checked = ui.developerOptionsEnabled,
        label = stringResource(R.string.developer_options_enabled),
        onCheckedChange = onDeveloperOptionsChange,
        modifier = Modifier.testTag("settings_developer_options_toggle"),
      )
      Text(stringResource(R.string.developer_options_helper), style = MaterialTheme.typography.bodySmall)
    }

    Panel {
      Text(stringResource(R.string.settings_appearance_title), fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(6.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { mode ->
          CompactActionButton(
            label = stringResource(
              when (mode) {
                ThemeMode.SYSTEM -> R.string.theme_system
                ThemeMode.LIGHT -> R.string.theme_light
                ThemeMode.DARK -> R.string.theme_dark
              }
            ),
            enabled = true,
            filled = ui.themeMode == mode,
            onClick = { onThemeModeChange(mode) },
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}

@Composable
private fun DebugReplayCapture(
  ui: UiState,
  onStartDebugRawLoopCapture: () -> Unit,
  onCancelDebugRawLoopCapture: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(stringResource(R.string.debug_raw_replay_title), fontWeight = FontWeight.SemiBold)
      Text(stringResource(R.string.debug_raw_replay_helper), style = MaterialTheme.typography.bodySmall)
      Spacer(Modifier.height(6.dp))
      Text(
        text = when {
          ui.debugRawLoopCapturing -> stringResource(
            R.string.debug_raw_replay_status_capturing,
            ui.debugRawLoopCapturedSeconds,
          )
          ui.debugRawLoopEnabled -> stringResource(R.string.debug_raw_replay_status_active)
          ui.debugRawLoopAvailable -> stringResource(R.string.debug_raw_replay_status_ready)
          else -> stringResource(R.string.debug_raw_replay_status_missing)
        },
        style = MaterialTheme.typography.bodySmall,
      )
      Text(
        stringResource(R.string.debug_raw_replay_storage_hint),
        style = MaterialTheme.typography.bodySmall,
      )
      Spacer(Modifier.height(6.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (ui.debugRawLoopCapturing) {
          OutlinedButton(
            onClick = onCancelDebugRawLoopCapture,
            modifier = Modifier.weight(1f),
          ) {
            Text(stringResource(R.string.debug_raw_replay_cancel_capture))
          }
        } else {
          OutlinedButton(
            onClick = onStartDebugRawLoopCapture,
            enabled = ui.connected && ui.eegStreamReady && !ui.debugRawLoopEnabled,
            modifier = Modifier.weight(1f),
          ) {
            Text(stringResource(R.string.debug_raw_replay_capture))
          }
        }
      }
  }
}

@Composable
private fun WindowSecondsSlider(
  selectedSeconds: Int,
  options: List<Int>,
  onSelected: (Int) -> Unit,
) {
  val selectedIndex = options.indexOf(selectedSeconds).coerceAtLeast(0)

  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(stringResource(R.string.plot_window_label), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.seconds_value, selectedSeconds), style = MaterialTheme.typography.labelMedium)
    Slider(
      value = selectedIndex.toFloat(),
      onValueChange = { value ->
        val nextIndex = value.roundToInt().coerceIn(0, options.lastIndex)
        onSelected(options[nextIndex])
      },
      valueRange = 0f..options.lastIndex.toFloat(),
      steps = (options.size - 2).coerceAtLeast(0),
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      options.forEach { seconds ->
        Text(
          text = stringResource(R.string.seconds_value, seconds),
          style = MaterialTheme.typography.labelSmall,
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoiseColorSelector(
  selected: NoiseColor,
  onSelect: (NoiseColor) -> Unit,
) {
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    NoiseColor.entries.forEach { option ->
      OutlinedButton(
        onClick = { onSelect(option) },
        border = BorderStroke(
          width = if (selected == option) 2.dp else 1.dp,
          color = if (selected == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
      ) {
        Text(noiseColorLabel(option))
      }
    }
  }
}

@Composable
private fun noiseColorLabel(color: NoiseColor): String {
  return when (color) {
    NoiseColor.WHITE -> stringResource(R.string.noise_color_white)
    NoiseColor.PINK -> stringResource(R.string.noise_color_pink)
    NoiseColor.BROWN -> stringResource(R.string.noise_color_brown)
    NoiseColor.BLUE -> stringResource(R.string.noise_color_blue)
  }
}
