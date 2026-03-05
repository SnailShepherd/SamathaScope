package com.mordin.samathascope

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * UI entry point.
 *
 * The layout is intentionally "flat" and control-heavy, inspired by the EEG Meditation app:
 * - obvious state at the top
 * - big Start/Pause/Stop row
 * - lots of toggles / options without hiding everything behind navigation
 */
@Composable
fun App(vm: MainViewModel) {
  MainScreen(vm)
}

@Composable
private fun MainScreen(vm: MainViewModel) {
  val ui by vm.ui.collectAsState()

  // Android 12+ needs runtime permissions for Bluetooth operations.
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

  Scaffold { padding ->

    // Single-scroll "control panel" style, similar to EEG Meditation app.
    Column(
      modifier = Modifier
        .padding(padding)
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

// Header (EEG Meditation style)
Text("SamathaScope v0.1", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge)
Spacer(Modifier.height(6.dp))
      Section {
        HeadsetHeader(ui = ui, onGrantPerms = { permissionLauncher.launch(permissions) })
        Spacer(Modifier.height(8.dp))
        DevicePicker(
          devices = ui.bondedDevices,
          selectedMac = ui.selectedDeviceMac,
          enabled = ui.btPermissionGranted && !ui.connected,
          onRefresh = { vm.refreshBondedDevices() },
          onSelect = { vm.selectDevice(it) },
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = { if (ui.connected) vm.disconnect() else vm.connect() },
            enabled = ui.btPermissionGranted && ui.selectedDeviceMac != null,
            modifier = Modifier.weight(1f)
          ) {
            Text(if (ui.connected) "DISCONNECT" else "CONNECT")
          }
          OutlinedButton(
            onClick = { vm.refreshBondedDevices() },
            enabled = ui.btPermissionGranted,
            modifier = Modifier.weight(1f)
          ) { Text("REFRESH") }
        }

        Spacer(Modifier.height(6.dp))
        Text(
          "PoorSignal: ${ui.poorSignal}   Samples/s: ${ui.samplesPerSecond.roundToInt()}   Stalls: ${ui.streamStallMs} ms",
          style = MaterialTheme.typography.bodySmall
        )
      }

      Section {
        Text("Session", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = { vm.startSession() },
            enabled = ui.connected && !ui.sessionRunning,
            modifier = Modifier.weight(1f)
          ) { Text("START") }

          OutlinedButton(
            onClick = { vm.togglePause() },
            enabled = ui.sessionRunning,
            modifier = Modifier.weight(1f)
          ) { Text(if (ui.sessionPaused) "RESUME" else "PAUSE") }

          OutlinedButton(
            onClick = { vm.stopSession() },
            enabled = ui.sessionRunning,
            modifier = Modifier.weight(1f)
          ) { Text("STOP") }
        }

        Spacer(Modifier.height(10.dp))

        if (ui.calibrating) {
          Text("Calibrating… ${ui.calibrationRemainingSec}s", fontWeight = FontWeight.SemiBold)
          LinearProgressIndicator(
            progress = (60 - ui.calibrationRemainingSec).coerceIn(0, 60) / 60f,
            modifier = Modifier.fillMaxWidth()
          )
          Text(
            "Sit still. Relax your face. Forehead clenching is not enlightenment.",
            style = MaterialTheme.typography.bodySmall
          )
        } else if (ui.sessionRunning) {
          Text(
            "Session time: ${ui.sessionElapsedSec}s   Avg S: ${(ui.avgS * 100).roundToInt()}   S≥80%: ${ui.timeSge80Sec}s",
            style = MaterialTheme.typography.bodySmall
          )
        } else {
          Text("Idle", style = MaterialTheme.typography.bodySmall)
        }
      }

      Section {
        Text("Feedback source", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        ChoiceRow(
          options = listOf(
            PlotType.SAMATHA_S to "Samatha (S)",
            PlotType.ESENSE_MEDITATION to "eSense Med",
            PlotType.ESENSE_ATTENTION to "eSense Att",
            PlotType.ARTEFACT_A to "Artefacts",
            PlotType.RAI to "RAI"
          ),
          selected = ui.feedbackMetric,
          onSelect = { vm.setFeedbackMetric(it) }
        )

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = ui.invertReward, onCheckedChange = { vm.setInvertReward(it) })
          Text("Invert reward (louder = better)")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = ui.artefactsReduceScore, onCheckedChange = { vm.setArtefactsReduceScore(it) })
          Text("Artefacts reduce Samatha score")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = ui.crackleEnabled, onCheckedChange = { vm.setCrackleEnabled(it) })
          Text("Crackle overlay (independent)")
        }

        Spacer(Modifier.height(8.dp))

        Text("Sensitivity (gamma): ${"%.2f".format(ui.gamma)}", style = MaterialTheme.typography.bodySmall)
        Slider(value = ui.gamma, onValueChange = { vm.setGamma(it) }, valueRange = 0.6f..3.0f)

        Text("Base noise range (dB): min ${ui.gMinDb}  max ${ui.gMaxDb}", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Column(Modifier.weight(1f)) {
            Text("Min (quiet)", style = MaterialTheme.typography.labelSmall)
            Slider(
              value = ui.gMinDb.toFloat(),
              onValueChange = { vm.setGMinDb(it.roundToInt()) },
              valueRange = -60f..-10f
            )
          }
          Column(Modifier.weight(1f)) {
            Text("Max (annoying)", style = MaterialTheme.typography.labelSmall)
            Slider(
              value = ui.gMaxDb.toFloat(),
              onValueChange = { vm.setGMaxDb(it.roundToInt()) },
              valueRange = -30f..0f
            )
          }
        }

        Text("Crackle intensity: ${"%.2f".format(ui.crackleIntensity)}", style = MaterialTheme.typography.bodySmall)
        Slider(value = ui.crackleIntensity, onValueChange = { vm.setCrackleIntensity(it) }, valueRange = 0f..1f)

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = { vm.testBeep() }, modifier = Modifier.weight(1f)) {
            Text("TEST BEEP")
          }
          AssistChip(
            onClick = {},
            label = { Text(if (ui.audioRunning) "Audio: RUN" else "Audio: OFF") }
          )
          AssistChip(
            onClick = {},
            label = { Text(if (ui.audioMuted) "Muted" else "On") }
          )
          AssistChip(
            onClick = {},
            label = { Text("${ui.audioBaseDb.roundToInt()} dB") }
          )
        }
      }

      Section {
        Text("Live plot", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        ChoiceRow(
          options = listOf(
            PlotType.RAW to "Raw",
            PlotType.SAMATHA_S to "S",
            PlotType.ARTEFACT_A to "A",
            PlotType.ESENSE_MEDITATION to "Med",
            PlotType.ESENSE_ATTENTION to "Att",
            PlotType.RAI to "RAI"
          ),
          selected = ui.plotType,
          onSelect = { vm.setPlotType(it) }
        )

        Spacer(Modifier.height(8.dp))

        if (ui.plotType == PlotType.RAW) {
          WaveformPlot(samples = ui.rawPreview)
        } else {
          MetricPlot(
            values = ui.plotHistory,
            plotType = ui.plotType
          )
        }
      }

      Section {
        Text("Diagnostics", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        ArtefactBar("Contact", ui.aContact)
        ArtefactBar("Line 50Hz", ui.aLine)
        ArtefactBar("EMG proxy", ui.aEmg)
        ArtefactBar("Blinks/transients", ui.aBlink)
        ArtefactBar("Packet stalls", ui.aStall)
        Spacer(Modifier.height(6.dp))
        ArtefactBar("Total A", ui.scoreA, emphasise = true)

        Spacer(Modifier.height(8.dp))
        Text("eSense: attention=${ui.attention} meditation=${ui.meditation}", style = MaterialTheme.typography.bodySmall)
      }

      Section {
        Text("Recording", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = ui.recordingEnabled, onCheckedChange = { vm.setRecordingEnabled(it) })
          Text("Record raw + features (local)")
        }
        ui.lastRecordingPath?.let {
          Text("Saved under:\n$it", style = MaterialTheme.typography.bodySmall)
        }
      }

      Spacer(Modifier.height(16.dp))
    }
  }
}

@Composable
private fun HeadsetHeader(ui: UiState, onGrantPerms: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
    Text(
      "Headset state: ${if (ui.connected) "Connected" else "Disconnected"}",
      fontWeight = FontWeight.SemiBold
    )
    if (!ui.btPermissionGranted && Build.VERSION.SDK_INT >= 31) {
      OutlinedButton(onClick = onGrantPerms) { Text("PERMS") }
    }
  }
}

@Composable
private fun Section(content: @Composable ColumnScope.() -> Unit) {
  Surface(
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
      content = content
    )
  }
}

/**
 * Picker for already-paired Bluetooth devices.
 *
 * v0.1 avoids scanning to keep the permission story simple:
 * - you pair the headset in Android system settings
 * - the app connects to bonded devices only
 */
@Composable
private fun DevicePicker(
  devices: List<BondedDevice>,
  selectedMac: String?,
  enabled: Boolean,
  onRefresh: () -> Unit,
  onSelect: (String) -> Unit
) {
  // NOTE: We intentionally avoid ExposedDropdownMenuBox here because it is marked experimental
  // in Material3 on some versions and will hard-fail compilation without an explicit opt-in.
  // A simple DropdownMenu is stable and works fine for a "paired device" picker.
  var expanded by remember { mutableStateOf(false) }
  val selectedName = devices.firstOrNull { it.mac == selectedMac }?.display ?: "Select paired device"

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
        // Keep the label compact; long device names are common.
        Text(selectedName, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Text("▼")
      }

      DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = Modifier.fillMaxWidth()
      ) {
        devices.forEach { d ->
          DropdownMenuItem(
            text = { Text(d.display) },
            onClick = {
              onSelect(d.mac)
              expanded = false
            }
          )
        }
      }
    }

    OutlinedButton(onClick = onRefresh, enabled = enabled) { Text("↻") }
  }
}

/**
 * Simple row of selectable buttons (EEG Meditation app style).
 */
@Composable
private fun ChoiceRow(
  options: List<Pair<PlotType, String>>,
  selected: PlotType,
  onSelect: (PlotType) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    options.forEach { (value, label) ->
      val isSelected = value == selected
      OutlinedButton(
        onClick = { onSelect(value) },
        colors = ButtonDefaults.outlinedButtonColors(
          containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
          contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        ),
        border = androidx.compose.foundation.BorderStroke(
          2.dp,
          if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
      ) { Text(label) }
    }
  }
}

@Composable
private fun ArtefactBar(label: String, value: Float, emphasise: Boolean = false) {
  Column {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(
        label,
        style = if (emphasise) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold) else MaterialTheme.typography.bodySmall
      )
      Text("${(value * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
    }
    LinearProgressIndicator(
      progress = value.coerceIn(0f, 1f),
      modifier = Modifier.fillMaxWidth()
    )
  }
}

/**
 * Improved raw waveform plot.
 *
 * Fixes the two obvious problems in v0.0x:
 * 1) DC offset: MindWave raw often drifts; we subtract the mean so the trace is centred.
 * 2) “Skew / slowness”: the UI now refreshes the raw buffer ~16 Hz (not 4 Hz), so it looks live.
 */
@Composable
private fun WaveformPlot(samples: List<Int>) {
  if (samples.size < 8) return

  // Compute mean (DC offset) and a robust scale based on standard deviation.
  val mean = samples.average()
  var varSum = 0.0
  for (v in samples) {
    val d = v - mean
    varSum += d * d
  }
  val sd = sqrt((varSum / samples.size).coerceAtLeast(1e-9))

  // Using 6*sd is a pragmatic "ignore extreme spikes" scale.
  val scale = (6.0 * sd).coerceAtLeast(1.0)

  val primary = MaterialTheme.colorScheme.primary
  val outline = MaterialTheme.colorScheme.outline

  Canvas(
    modifier = Modifier
      .fillMaxWidth()
      .height(160.dp)
      .background(MaterialTheme.colorScheme.surface)
      .padding(6.dp)
  ) {
    val w = size.width
    val h = size.height
    val mid = h / 2f
    val step = w / (samples.size - 1).toFloat()

    // Midline
    drawLine(outline, Offset(0f, mid), Offset(w, mid))

    val path = Path()
    samples.forEachIndexed { i, v ->
      val x = i * step
      val y = (mid - (((v - mean) / scale) * (h * 0.45)).toFloat())
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color = primary)
  }
}

/**
 * Metric time series plot for S/A/eSense.
 *
 * - For S and A we display 0..100%
 * - For eSense values already 0..100
 * - For RAI we also display 0..100% (RAI normalised during history storage)
 */
@Composable
private fun MetricPlot(values: List<Float>, plotType: PlotType) {
  if (values.size < 2) return

  val (yMin, yMax, label) = when (plotType) {
    PlotType.SAMATHA_S -> Triple(0f, 100f, "S (%)")
    PlotType.ARTEFACT_A -> Triple(0f, 100f, "A (%)")
    PlotType.ESENSE_MEDITATION -> Triple(0f, 100f, "Med")
    PlotType.ESENSE_ATTENTION -> Triple(0f, 100f, "Att")
    PlotType.RAI -> Triple(0f, 100f, "RAI (%)")
    PlotType.RAW -> Triple(0f, 100f, "")
  }

  // Convert to chart units
  val ys = values.map { v ->
    when (plotType) {
      PlotType.SAMATHA_S, PlotType.ARTEFACT_A, PlotType.RAI -> (v * 100f).coerceIn(yMin, yMax)
      PlotType.ESENSE_MEDITATION, PlotType.ESENSE_ATTENTION -> v.coerceIn(yMin, yMax)
      PlotType.RAW -> v
    }
  }

  val primary = MaterialTheme.colorScheme.primary
  val outline = MaterialTheme.colorScheme.outline

  Canvas(
    modifier = Modifier
      .fillMaxWidth()
      .height(180.dp)
      .background(MaterialTheme.colorScheme.surface)
      .padding(6.dp)
  ) {
    val w = size.width
    val h = size.height

    // Light grid (5 lines)
    for (i in 0..4) {
      val y = h * i / 4f
      drawLine(outline.copy(alpha = 0.35f), Offset(0f, y), Offset(w, y))
    }

    val step = w / (ys.size - 1).toFloat()
    val path = Path()
    ys.forEachIndexed { i, v ->
      val x = i * step
      val y = h - ((v - yMin) / (yMax - yMin)) * h
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color = primary)

    // Tiny label in corner (we avoid text APIs here to keep it simple)
  }

  Text(label, style = MaterialTheme.typography.bodySmall)
}