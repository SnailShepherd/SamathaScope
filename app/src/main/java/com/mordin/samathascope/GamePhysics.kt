package com.mordin.samathascope

data class LevitationState(
  val altitude: Float,
  val velocity: Float,
)

object GamePhysics {
  fun step(
    state: LevitationState,
    target: Float,
    dtSeconds: Float,
    stiffness: Float = 7.0f,
    damping: Float = 4.5f,
  ): LevitationState {
    val clampedTarget = target.coerceIn(0f, 1f)
    var remaining = dtSeconds.coerceIn(0f, 1.0f)
    var altitude = state.altitude
    var velocity = state.velocity

    while (remaining > 0f) {
      val dt = remaining.coerceAtMost(0.1f)
      val acceleration = stiffness * (clampedTarget - altitude) - damping * velocity
      velocity += acceleration * dt
      altitude = (altitude + velocity * dt).coerceIn(0f, 1f)
      remaining -= dt
    }

    return LevitationState(altitude = altitude, velocity = velocity)
  }

  fun metricToTargetHeight(metricValue: Float): Float = metricValue.coerceIn(0f, 1f)
}

fun shouldShowBatteryRow(batteryPercent: Int?): Boolean = batteryPercent != null
