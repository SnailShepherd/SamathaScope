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
    val safeDt = dtSeconds.coerceIn(0f, 0.1f)
    val acceleration = stiffness * (clampedTarget - state.altitude) - damping * state.velocity
    val velocity = state.velocity + acceleration * safeDt
    val altitude = (state.altitude + velocity * safeDt).coerceIn(0f, 1f)
    return LevitationState(altitude = altitude, velocity = velocity)
  }

  fun metricToTargetHeight(metricValue: Float): Float = metricValue.coerceIn(0f, 1f)
}

fun shouldShowBatteryRow(batteryPercent: Int?): Boolean = batteryPercent != null
