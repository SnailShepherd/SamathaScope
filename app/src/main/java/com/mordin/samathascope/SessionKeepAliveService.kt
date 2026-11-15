package com.mordin.samathascope

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Keeps the process and CPU alive while a session runs, so turning the screen off during an
 * eyes-closed session doesn't freeze the Bluetooth reader, the session loop or the audio feedback.
 *
 * It holds no session state: [MainViewModel] still does all the work. The service only marks the
 * process as foreground (with the required notification) and holds a partial wake lock.
 */
class SessionKeepAliveService : Service() {

  private var wakeLock: PowerManager.WakeLock? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // startForeground first, always: it is the promise made by startForegroundService, and breaking
    // it (stopping before calling it) crashes the app.
    val inForeground = enterForeground()
    val keepRunning = synchronized(stateLock) {
      val stopWasRequested = pendingStop
      pendingStop = false
      state = if (inForeground && !stopWasRequested) State.RUNNING else State.IDLE
      state == State.RUNNING
    }
    if (!keepRunning) {
      // The session already ended while the service was starting (or it couldn't go foreground).
      if (inForeground) stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
      return START_NOT_STICKY
    }
    if (wakeLock == null) {
      val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
      wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
        setReferenceCounted(false)
        acquire(MAX_SESSION_MS)
      }
    }
    // Not sticky: if the process dies the session is gone, so there is nothing to restart.
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    // If the system ended the service, let the next session start it again. A start already in
    // flight (STARTING) belongs to a new instance and is left alone.
    synchronized(stateLock) {
      if (state == State.RUNNING) state = State.IDLE
    }
    wakeLock?.let { if (it.isHeld) it.release() }
    wakeLock = null
    super.onDestroy()
  }

  /**
   * Calls startForeground, trying each allowed service type in turn. Once started with
   * startForegroundService, a service that stops without ever calling startForeground crashes the
   * app, so a refused type must not end the attempt: the next type is tried instead.
   */
  private fun enterForeground(): Boolean {
    val notification = buildNotification()
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      return tryStartForeground { startForeground(NOTIFICATION_ID, notification) }
    }
    return foregroundServiceTypes().any { type ->
      tryStartForeground { startForeground(NOTIFICATION_ID, notification, type) }
    }
  }

  private inline fun tryStartForeground(start: () -> Unit): Boolean {
    return try {
      start()
      true
    } catch (e: Exception) {
      Log.w(TAG, "Could not keep the session in the foreground", e)
      false
    }
  }

  /**
   * The headset link is the main reason to stay alive; the audio feedback is the fallback, e.g. for
   * a debug-replay session without the Bluetooth permission that connectedDevice requires.
   */
  @RequiresApi(Build.VERSION_CODES.Q)
  private fun foregroundServiceTypes(): List<Int> {
    val hasBluetoothConnect = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
      checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    return buildList {
      if (hasBluetoothConnect) add(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
      add(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }
  }

  private fun buildNotification(): Notification {
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(
      NotificationChannel(
        CHANNEL_ID,
        getString(R.string.session_notification_channel),
        NotificationManager.IMPORTANCE_LOW,
      )
    )
    val openApp = packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
      PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE)
    }
    return Notification.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_session_notification)
      .setContentTitle(getString(R.string.session_notification_title))
      .setContentText(getString(R.string.session_notification_text))
      .setOngoing(true)
      .setContentIntent(openApp)
      .build()
  }

  companion object {
    private const val TAG = "SessionKeepAlive"
    private const val CHANNEL_ID = "session"
    private const val NOTIFICATION_ID = 1
    private const val WAKE_LOCK_TAG = "SamathaScope:session"
    // Safety net so a lost stop() can't drain the battery indefinitely.
    private const val MAX_SESSION_MS = 4L * 60L * 60L * 1000L

    // Start/stop requests come from the main and Bluetooth threads; onStartCommand runs on main.
    private enum class State { IDLE, STARTING, RUNNING }
    private val stateLock = Any()
    private var state = State.IDLE
    /** A stop that arrived while the service was still starting; honoured once it has gone foreground. */
    private var pendingStop = false

    fun start(context: Context) = synchronized(stateLock) {
      when (state) {
        State.RUNNING -> return@synchronized
        State.STARTING -> {
          // Started, stopped and started again before the service ran: just keep it.
          pendingStop = false
          return@synchronized
        }
        State.IDLE -> Unit
      }
      try {
        context.startForegroundService(Intent(context, SessionKeepAliveService::class.java))
        state = State.STARTING
        pendingStop = false
      } catch (e: Exception) {
        // E.g. started from the background on Android 12+. The session still runs while visible.
        Log.w(TAG, "Could not start the session keep-alive service", e)
      }
    }

    /**
     * Never stops the service before it has called startForeground: a stop during start-up is
     * deferred to [onStartCommand].
     */
    fun stop(context: Context) = synchronized(stateLock) {
      when (state) {
        State.IDLE -> Unit
        State.STARTING -> pendingStop = true
        State.RUNNING -> {
          state = State.IDLE
          context.stopService(Intent(context, SessionKeepAliveService::class.java))
        }
      }
    }
  }
}
