package com.mordin.samathascope

sealed class ThinkGearData {
  data class Battery(val value: Int) : ThinkGearData()
  data class PoorSignal(val value: Int) : ThinkGearData()
  data class Attention(val value: Int) : ThinkGearData()
  data class Meditation(val value: Int) : ThinkGearData()
  data class RawSample(val value: Int) : ThinkGearData()
  data class AsicEegPower(val bands: IntArray) : ThinkGearData() // 8 bands, 3-byte big-endian each
  /** Estimated raw samples lost to corrupt or dropped bytes, reported before the next valid packet. */
  data class PacketLoss(val lostSamples: Int) : ThinkGearData()
  data class Unknown(val code: Int, val payload: ByteArray) : ThinkGearData()
}
