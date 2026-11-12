package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThinkGearParserTest {

  @Test
  fun batteryPacket_isDecoded() {
    val parser = ThinkGearParser()
    val events = mutableListOf<ThinkGearData>()
    parser.onData = { events += it }

    parser.feed(
      byteArrayOf(
        0xAA.toByte(),
        0xAA.toByte(),
        0x02,
        0x01,
        0x32,
        0xCC.toByte(),
      ),
      6,
    )

    assertThat(events).containsExactly(ThinkGearData.Battery(50))
  }

  @Test
  fun emptyPacket_doesNotSwallowFollowingPacket() {
    val parser = ThinkGearParser()
    val events = mutableListOf<ThinkGearData>()
    parser.onData = { events += it }

    val bytes = byteArrayOf(
      // Empty packet: PLEN 0, checksum 0xFF.
      0xAA.toByte(), 0xAA.toByte(), 0x00, 0xFF.toByte(),
      // Battery packet right after it.
      0xAA.toByte(), 0xAA.toByte(), 0x02, 0x01, 0x32, 0xCC.toByte(),
    )
    parser.feed(bytes, bytes.size)

    assertThat(events).containsExactly(ThinkGearData.Battery(50))
  }

  @Test
  fun extraSyncBytes_areTolerated() {
    val parser = ThinkGearParser()
    val events = mutableListOf<ThinkGearData>()
    parser.onData = { events += it }

    val bytes = byteArrayOf(
      0xAA.toByte(), 0xAA.toByte(), 0xAA.toByte(), 0x02, 0x01, 0x32, 0xCC.toByte(),
    )
    parser.feed(bytes, bytes.size)

    assertThat(events).containsExactly(ThinkGearData.Battery(50))
  }

  @Test
  fun rawSample_isDecodedAsSigned16Bit() {
    val parser = ThinkGearParser()
    val events = mutableListOf<ThinkGearData>()
    parser.onData = { events += it }

    // CODE 0x80, VLEN 2, value 0xFF 0x38 = -200. Checksum = 255 - ((0x80 + 0x02 + 0xFF + 0x38) & 0xFF).
    val sum = (0x80 + 0x02 + 0xFF + 0x38) and 0xFF
    val bytes = byteArrayOf(
      0xAA.toByte(), 0xAA.toByte(), 0x04, 0x80.toByte(), 0x02, 0xFF.toByte(), 0x38, (255 - sum).toByte(),
    )
    parser.feed(bytes, bytes.size)

    assertThat(events).containsExactly(ThinkGearData.RawSample(-200))
  }
}
