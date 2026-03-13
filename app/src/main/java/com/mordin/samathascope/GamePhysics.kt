package com.mordin.samathascope

fun clamp01(value: Float): Float = value.coerceIn(0f, 1f)

fun approach(current: Float, target: Float, factor: Float): Float {
  return current + ((target - current) * factor.coerceIn(0f, 1f))
}

fun damp(current: Float, amountPerSecond: Float, dtSeconds: Float): Float {
  return current * (1f - (amountPerSecond * dtSeconds).coerceIn(0f, 0.98f))
}

fun sequenceFloat(index: Int, salt: Int = 0): Float {
  val normalized = ((index * 37) + (salt * 17)).mod(100)
  return normalized / 100f
}

fun shouldShowBatteryRow(batteryPercent: Int?): Boolean = batteryPercent != null
