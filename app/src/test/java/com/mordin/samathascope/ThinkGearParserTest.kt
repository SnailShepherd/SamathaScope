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
}
