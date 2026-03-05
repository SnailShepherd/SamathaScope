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

  fun feed(bytes: ByteArray, length: Int) {
    for (i in 0 until length) {
      val b = bytes[i].toInt() and 0xFF
      step(b)
    }
  }

  private fun step(b: Int) {
    when (state) {
      State.SYNC1 -> {
        if (b == 0xAA) state = State.SYNC2
      }
      State.SYNC2 -> {
        state = if (b == 0xAA) State.PLEN else State.SYNC1
      }
      State.PLEN -> {
        if (b in 0..169) {
          payloadLen = b
          payloadPos = 0
          state = State.PAYLOAD
        } else {
          state = State.SYNC1
        }
      }
      State.PAYLOAD -> {
        payload[payloadPos++] = b.toByte()
        if (payloadPos >= payloadLen) state = State.CHK
      }
      State.CHK -> {
        val chk = b and 0xFF
        val sum = payload.take(payloadLen).sumOf { it.toInt() and 0xFF } and 0xFF
        val expected = (255 - sum) and 0xFF
        if (chk == expected) {
          parsePayload(payload, payloadLen)
        }
        state = State.SYNC1
      }
    }
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
