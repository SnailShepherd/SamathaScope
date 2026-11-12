package com.mordin.samathascope

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.util.UUID

/**
 * One-shot RFCOMM connection to a MindWave headset. Create a new instance per connection attempt.
 *
 * Callbacks run on the client's reader thread:
 * - [connect]'s `onDisconnected` fires exactly once when the link ends on its own (error or end of
 *   stream), and never after [close] was called by the app.
 * - Exceptions thrown by `onData` are logged and swallowed, so a bug or storage failure while
 *   processing a sample can't tear down the Bluetooth link.
 */
class BluetoothMindWaveClient(
  private val device: BluetoothDevice
) {

  // Standard SPP UUID used for RFCOMM serial devices.
  private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

  @Volatile private var socket: BluetoothSocket? = null
  @Volatile private var closedByApp = false
  private var thread: Thread? = null

  private val parser = ThinkGearParser()

  fun connect(
    onConnected: () -> Unit,
    onDisconnected: (Throwable?) -> Unit,
    onData: (ThinkGearData) -> Unit
  ) {
    check(thread == null) { "BluetoothMindWaveClient.connect() may only be called once" }
    parser.onData = { data ->
      try {
        onData(data)
      } catch (t: Throwable) {
        Log.e(TAG, "Error while handling headset data", t)
      }
    }
    thread = Thread({ run(onConnected, onDisconnected) }, "MindWaveReader").apply {
      isDaemon = true
      start()
    }
  }

  private fun run(onConnected: () -> Unit, onDisconnected: (Throwable?) -> Unit) {
    var error: Throwable? = null
    try {
      val s = device.createRfcommSocketToServiceRecord(sppUuid)
      socket = s
      // close() may have run before the socket existed; it can't have closed this one.
      if (closedByApp) return
      s.connect()
      if (closedByApp) return
      onConnected()

      val ins = s.inputStream
      val buf = ByteArray(1024)
      while (!closedByApp) {
        val n = ins.read(buf)
        if (n < 0) break // End of stream: the headset went away without an IOException.
        if (n > 0) parser.feed(buf, n)
      }
    } catch (t: Throwable) {
      error = t
    } finally {
      closeSocket()
    }
    if (!closedByApp) onDisconnected(error)
  }

  fun close() {
    closedByApp = true
    closeSocket()
  }

  private fun closeSocket() {
    try { socket?.close() } catch (_: Throwable) {}
  }

  private companion object {
    const val TAG = "MindWaveClient"
  }
}
