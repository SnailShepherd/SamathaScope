package com.mordin.samathascope

/**
 * Minimal ThinkGear packet parser:
 *
 * Packet: [0xAA][0xAA][PLENGTH][PAYLOAD...][CHKSUM]
 * CHKSUM = 255 - (sum(payload bytes) & 0xFF)
 *
 * DataRows in payload:
 * - One or more 0x55 EXCODE bytes (ignored for v0.1)
 * - CODE byte
 * - if CODE < 0x80: single-byte value (no LENGTH byte)
 * - else: LENGTH byte, then that many value bytes
 *
 * Codes we care about:
 * 0x01 BATTERY (single byte)
 * 0x02 POOR_SIGNAL (single byte)
 * 0x04 ATTENTION (single byte)
 * 0x05 MEDITATION (single byte)
 * 0x80 RAW sample (2 bytes, signed big-endian)
 * 0x83 ASIC_EEG_POWER (24 bytes, 8x 3-byte unsigned ints)
 *
 * See NeuroSky ThinkGear Communications Protocol for the code table.
 */
class ThinkGearParser {

  var onData: ((ThinkGearData) -> Unit)? = null

  private enum class State { SYNC1, SYNC2, PLEN, PAYLOAD, CHK }
  private var state = State.SYNC1

  private var payloadLen = 0
  private var payload = ByteArray(169)
  private var payloadPos = 0

  // Loss tracking. A healthy stream is back-to-back packets, so once we have seen one valid packet,
  // any byte skipped while hunting for sync, or a failed checksum, means data was dropped or corrupted.
  // Raw-sample packets dominate the stream (8 bytes each), so skipped bytes / 8 estimates lost samples.
  private var synced = false
  private var skippedBytes = 0

  fun feed(bytes: ByteArray, length: Int) {
    for (i in 0 until length) {
      val b = bytes[i].toInt() and 0xFF
      step(b)
    }
  }

  private fun skip(count: Int) {
    if (synced) skippedBytes += count
  }

  private fun reportSkippedBytes() {
    if (skippedBytes == 0) return
    val lost = (skippedBytes + RAW_PACKET_BYTES - 1) / RAW_PACKET_BYTES
    skippedBytes = 0
    onData?.invoke(ThinkGearData.PacketLoss(lost))
  }

  private fun step(b: Int) {
    when (state) {
      State.SYNC1 -> {
        if (b == 0xAA) state = State.SYNC2 else skip(1)
      }
      State.SYNC2 -> {
        if (b == 0xAA) {
          state = State.PLEN
        } else {
          skip(2)
          state = State.SYNC1
        }
      }
      State.PLEN -> {
        when {
          // Per the ThinkGear spec, a PLENGTH of 0xAA is just another SYNC byte: stay in sync.
          b == 0xAA -> state = State.PLEN
          b in 0..169 -> {
            reportSkippedBytes()
            payloadLen = b
            payloadPos = 0
            // An empty payload goes straight to the checksum byte.
            state = if (b == 0) State.CHK else State.PAYLOAD
          }
          else -> {
            skip(3)
            state = State.SYNC1
          }
        }
      }
      State.PAYLOAD -> {
        payload[payloadPos++] = b.toByte()
        if (payloadPos >= payloadLen) state = State.CHK
      }
      State.CHK -> {
        val chk = b and 0xFF
        var sum = 0
        for (k in 0 until payloadLen) sum += payload[k].toInt() and 0xFF
        sum = sum and 0xFF
        val expected = (255 - sum) and 0xFF
        if (chk == expected) {
          synced = true
          parsePayload(payload, payloadLen)
        } else if (synced) {
          val lost = lostSamplesForCorruptPacket(payloadLen)
          if (lost > 0) onData?.invoke(ThinkGearData.PacketLoss(lost))
        }
        state = State.SYNC1
      }
    }
  }

  /**
   * Raw samples lost with a packet that failed its checksum. A corrupt packet of the once-per-second
   * eSense/band-power size loses no raw samples. Any other length means the bytes consumed were most
   * likely raw-sample packets: either a corrupt raw packet, or a corrupted length byte that made the
   * parser swallow several packets as one payload.
   */
  private fun lostSamplesForCorruptPacket(payloadLen: Int): Int {
    if (payloadLen == ESENSE_PAYLOAD_BYTES) return 0
    val consumedBytes = payloadLen + PACKET_OVERHEAD_BYTES
    return (consumedBytes + RAW_PACKET_BYTES - 1) / RAW_PACKET_BYTES
  }

  private companion object {
    const val RAW_PACKET_BYTES = 8 // 2 sync + PLEN + 4 payload + checksum.
    const val PACKET_OVERHEAD_BYTES = 4 // 2 sync + PLEN + checksum.
    const val ESENSE_PAYLOAD_BYTES = 32 // Poor signal, ASIC band powers, attention, meditation.
  }

  private fun parsePayload(buf: ByteArray, len: Int) {
    var i = 0
    while (i < len) {
      // EXCODE bytes (0x55) can be repeated; ignore depth for v0.1
      while (i < len && (buf[i].toInt() and 0xFF) == 0x55) i++

      if (i >= len) break
      val code = buf[i].toInt() and 0xFF
      i++

      if (code < 0x80) {
        // single-byte value
        if (i >= len) break
        val v = buf[i].toInt() and 0xFF
        i++
        when (code) {
          0x01 -> onData?.invoke(ThinkGearData.Battery(v))
          0x02 -> onData?.invoke(ThinkGearData.PoorSignal(v))
          0x04 -> onData?.invoke(ThinkGearData.Attention(v))
          0x05 -> onData?.invoke(ThinkGearData.Meditation(v))
          else -> onData?.invoke(ThinkGearData.Unknown(code, byteArrayOf(v.toByte())))
        }
      } else {
        if (i >= len) break
        val vlen = buf[i].toInt() and 0xFF
        i++
        if (i + vlen > len) break
        val vbytes = buf.copyOfRange(i, i + vlen)
        i += vlen

        when (code) {
          0x80 -> {
            if (vlen == 2) {
              val hi = vbytes[0].toInt() and 0xFF
              val lo = vbytes[1].toInt() and 0xFF
              var raw = hi * 256 + lo
              if (raw >= 32768) raw -= 65536
              onData?.invoke(ThinkGearData.RawSample(raw))
            }
          }
          0x83 -> {
            if (vlen == 24) {
              val bands = IntArray(8)
              for (k in 0 until 8) {
                val a = vbytes[k*3].toInt() and 0xFF
                val b = vbytes[k*3 + 1].toInt() and 0xFF
                val c = vbytes[k*3 + 2].toInt() and 0xFF
                bands[k] = (a shl 16) or (b shl 8) or c
              }
              onData?.invoke(ThinkGearData.AsicEegPower(bands))
            }
          }
          else -> onData?.invoke(ThinkGearData.Unknown(code, vbytes))
        }
      }
    }
  }
}
