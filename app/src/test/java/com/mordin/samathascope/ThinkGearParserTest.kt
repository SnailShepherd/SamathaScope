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

  @Test
  fun droppedBytesBetweenPackets_reportLostSamplesBeforeNextSample() {
    val parser = ThinkGearParser()
    val events = mutableListOf<ThinkGearData>()
    parser.onData = { events += it }

    // Valid sample, then 13 bytes of a mangled stream (two packets lost), then a valid sample.
    val bytes = rawPacket(10) + ByteArray(13) { 0x11 } + rawPacket(20)
    parser.feed(bytes, bytes.size)

    assertThat(events).containsExactly(
      ThinkGearData.RawSample(10),
      ThinkGearData.PacketLoss(2),
      ThinkGearData.RawSample(20),
    ).inOrder()
  }

  @Test
  fun corruptRawPacket_reportsOneLostSample() {
    val parser = ThinkGearParser()
    val events = mutableListOf<ThinkGearData>()
    parser.onData = { events += it }

    val corrupt = rawPacket(30).also { it[it.lastIndex] = (it[it.lastIndex] + 1).toByte() }
    val bytes = rawPacket(10) + corrupt + rawPacket(20)
    parser.feed(bytes, bytes.size)

    assertThat(events).containsExactly(
      ThinkGearData.RawSample(10),
      ThinkGearData.PacketLoss(1),
      ThinkGearData.RawSample(20),
    ).inOrder()
  }

  @Test
  fun corruptLengthByte_countsEverySwallowedPacket() {
    val parser = ThinkGearParser()
    val events = mutableListOf<ThinkGearData>()
    parser.onData = { events += it }

    // A packet whose length byte was corrupted to 36 swallows 36 payload bytes plus the checksum
    // (the next five raw packets), then fails the checksum: 40 bytes = 5 raw packets lost.
    val swallowed = rawPacket(1) + rawPacket(2) + rawPacket(3) + rawPacket(4) + rawPacket(5)
    val corrupt = byteArrayOf(0xAA.toByte(), 0xAA.toByte(), 36) + swallowed.copyOfRange(0, 37)
    val bytes = rawPacket(10) + corrupt + swallowed.copyOfRange(37, swallowed.size) + rawPacket(20)
    parser.feed(bytes, bytes.size)

    val lost = events.filterIsInstance<ThinkGearData.PacketLoss>().sumOf { it.lostSamples }
    assertThat(events.first()).isEqualTo(ThinkGearData.RawSample(10))
    assertThat(events.last()).isEqualTo(ThinkGearData.RawSample(20))
    assertThat(lost).isAtLeast(5)
  }

  @Test
  fun corruptESensePacket_losesNoRawSamples() {
    val parser = ThinkGearParser()
    val events = mutableListOf<ThinkGearData>()
    parser.onData = { events += it }

    val esense = byteArrayOf(0xAA.toByte(), 0xAA.toByte(), 32) + ByteArray(32) { 0x02 } + byteArrayOf(0x00)
    val bytes = rawPacket(10) + esense + rawPacket(20)
    parser.feed(bytes, bytes.size)

    assertThat(events).containsExactly(ThinkGearData.RawSample(10), ThinkGearData.RawSample(20)).inOrder()
  }

  @Test
  fun garbageBeforeFirstPacket_isNotCountedAsLoss() {
    val parser = ThinkGearParser()
    val events = mutableListOf<ThinkGearData>()
    parser.onData = { events += it }

    // Connecting mid-stream: bytes before the first valid packet are not loss.
    val bytes = ByteArray(5) { 0x11 } + rawPacket(10)
    parser.feed(bytes, bytes.size)

    assertThat(events).containsExactly(ThinkGearData.RawSample(10))
  }

  private fun rawPacket(value: Int): ByteArray {
    val hi = (value shr 8) and 0xFF
    val lo = value and 0xFF
    val sum = (0x80 + 0x02 + hi + lo) and 0xFF
    return byteArrayOf(
      0xAA.toByte(), 0xAA.toByte(), 0x04, 0x80.toByte(), 0x02, hi.toByte(), lo.toByte(), (255 - sum).toByte(),
    )
  }
}
