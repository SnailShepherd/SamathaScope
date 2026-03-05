package com.mordin.samathascope

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.InputStream
import java.util.UUID

class BluetoothMindWaveClient(
  private val device: BluetoothDevice
) {

  // Standard SPP UUID often used for RFCOMM serial devices.
  private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

  private var socket: BluetoothSocket? = null
  private var input: InputStream? = null
  private var scope: CoroutineScope? = null
  private var job: Job? = null

  private val parser = ThinkGearParser()

  fun connect(
    onConnected: () -> Unit,
    onDisconnected: (Throwable?) -> Unit,
    onData: (ThinkGearData) -> Unit
  ) {
    close()

    scope = CoroutineScope(Dispatchers.IO)
    job = scope?.launch {
      try {
        // Cancel discovery can improve connection reliability.
        // (We don't do active scanning in v0.1; we connect to bonded devices.)
        socket = device.createRfcommSocketToServiceRecord(sppUuid)
        socket?.connect()
        input = socket?.inputStream

        parser.onData = onData
        onConnected()

        readLoop(input!!)
      } catch (t: Throwable) {
        onDisconnected(t)
        close()
      }
    }
  }

  private fun readLoop(ins: InputStream) {
    val buf = ByteArray(1024)
    while (true) {
      val n = ins.read(buf)
      if (n <= 0) break
      parser.feed(buf, n)
    }
  }

  fun close() {
    try { input?.close() } catch (_: Throwable) {}
    try { socket?.close() } catch (_: Throwable) {}
    input = null
    socket = null
    job?.cancel()
    job = null
    scope?.cancel()
    scope = null
  }
}
