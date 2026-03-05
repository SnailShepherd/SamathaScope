package com.mordin.samathascope

sealed class ThinkGearData {
  data class PoorSignal(val value: Int) : ThinkGearData()
  data class Attention(val value: Int) : ThinkGearData()
  data class Meditation(val value: Int) : ThinkGearData()
  data class RawSample(val value: Int) : ThinkGearData()
  data class AsicEegPower(val bands: IntArray) : ThinkGearData() // 8 bands, 3-byte big-endian each
  data class Unknown(val code: Int, val payload: ByteArray) : ThinkGearData()
}
