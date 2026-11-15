package com.mordin.samathascope

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

// App root: tabs, permissions and app-level actions (sharing).

@Composable
fun App(vm: MainViewModel) {
  MainScreen(vm)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(vm: MainViewModel) {
  val ui by vm.ui.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val permissions = remember {
    buildList {
      if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
    }.toTypedArray()
  }

  val shareFailedMessage = stringResource(R.string.share_failed)
  val shareChooserTitle = stringResource(R.string.share_chooser_title)
  val shareLatestSession: () -> Unit = {
    scope.launch {
      val zip = vm.exportLatestSession()
      if (zip == null) {
        Toast.makeText(context, shareFailedMessage, Toast.LENGTH_LONG).show()
        return@launch
      }
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zip)
      val send = Intent(Intent.ACTION_SEND)
        .setType("application/zip")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      // ClipData carries the read grant through the chooser to the app the user picks.
      send.clipData = ClipData.newRawUri(zip.name, uri)
      context.startActivity(Intent.createChooser(send, shareChooserTitle))
    }
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { result ->
    vm.onPermissionsResult(result)
  }

  // Android 13+ hides the "Session running" notification without this permission. It is asked for
  // when a session starts, where the reason is obvious, and the session starts whatever the answer.
  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { _ -> vm.startSession() }
  val startSession: () -> Unit = {
    val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
      PackageManager.PERMISSION_GRANTED
    if (needsNotificationPermission) {
      // After a permanent denial the system answers at once, so this never blocks starting.
      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      vm.startSession()
    }
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
      PrimaryTabRow(selectedTabIndex = ui.selectedTab.ordinal) {
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
          rawPreview = vm.rawPreview,
          onGrantPermissions = { permissionLauncher.launch(permissions) },
          onRefreshDevices = vm::refreshBondedDevices,
          onSelectDevice = vm::selectDevice,
          onConnectToggle = { if (ui.connected) vm.disconnect() else vm.connect() },
          onDebugRawLoopEnabledChange = vm::setDebugRawLoopEnabled,
          onStartSession = startSession,
          onSessionModeChange = vm::setSessionMode,
          onSkipCalibration = vm::skipCalibration,
          onTogglePause = vm::togglePause,
          onStopSession = vm::stopSession,
          onSetFeedbackMetric = vm::setFeedbackMetric,
          onToggleMetric = vm::toggleVisibleMetric,
          onFocusMetricInfo = vm::focusMetricInfo,
          onPanRawPlot = vm::panRawPlotBy,
          onResetRawPlot = vm::resetRawPlotToLatest,
          onPanMetricPlot = vm::panMetricPlotBy,
          onResetMetricPlot = vm::resetMetricPlotToLatest,
          onStartArtefactCalibration = vm::startArtefactCalibration,
          onLaterArtefactCalibration = vm::dismissArtefactCalibrationOffer,
          onSkipArtefactCalibration = vm::skipArtefactCalibration,
        )

        AppTab.SETTINGS -> SettingsTab(
          ui = ui,
          onAudioEnabledChange = vm::setAudioEnabled,
          onInvertRewardChange = vm::setInvertReward,
          onNoiseColorChange = vm::setNoiseColor,
          onGammaChange = vm::setGamma,
          onGMinDbChange = vm::setGMinDb,
          onGMaxDbChange = vm::setGMaxDb,
          onTestBeep = vm::testBeep,
          onMetricWindowChange = vm::setMetricWindowSeconds,
          onRecordingChange = vm::setRecordingEnabled,
          onShareLatestSession = shareLatestSession,
          onThemeModeChange = vm::setThemeMode,
          onDeveloperOptionsChange = vm::setDeveloperOptionsEnabled,
          onNotch50Change = vm::setNotch50Enabled,
          onStartDebugRawLoopCapture = vm::startDebugRawLoopCapture,
          onCancelDebugRawLoopCapture = vm::cancelDebugRawLoopCapture,
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
    AppTab.LEARN -> stringResource(R.string.tab_learn)
  }
}
