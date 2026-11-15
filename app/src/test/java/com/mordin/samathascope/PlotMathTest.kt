package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlotMathTest {

  @Test
  fun takeFixedWindow_returnsTail() {
    val values = listOf(1f, 2f, 3f, 4f, 5f)

    val result = PlotMath.takeFixedWindow(values, maxPoints = 3)

    assertThat(result).containsExactly(3f, 4f, 5f).inOrder()
  }

  @Test
  fun toPlotPoints_mapsToBounds() {
    val points = PlotMath.toPlotPoints(
      values = listOf(0f, 50f, 100f),
      yMin = 0f,
      yMax = 100f,
      width = 10f,
      height = 20f,
    )

    assertThat(points).hasSize(3)
    assertThat(points.first().x).isEqualTo(0f)
    assertThat(points.last().x).isEqualTo(10f)
    assertThat(points.first().y).isEqualTo(20f)
    assertThat(points.last().y).isEqualTo(0f)
  }

  @Test
  fun calibratedRawRangeFromSd_staysSymmetricAndBounded() {
    val (min, max) = PlotMath.calibratedRawRangeFromSd(sd = 20.0)

    assertThat(min).isLessThan(0f)
    assertThat(max).isGreaterThan(0f)
    assertThat(max).isEqualTo(-min)
    assertThat(max).isAtLeast(300f)
  }

  @Test
  fun minMaxDecimate_keepsSpikesAndLength() {
    val values = IntArray(10_000) { 0 }.also { it[1_234] = 1_800; it[8_765] = -1_500 }

    val decimated = PlotMath.minMaxDecimate(values, maxPoints = 800)

    assertThat(decimated.size).isEqualTo(800)
    assertThat(decimated.max()).isEqualTo(1_800)
    assertThat(decimated.min()).isEqualTo(-1_500)
  }

  @Test
  fun minMaxDecimate_leavesShortInputUntouched() {
    val values = intArrayOf(1, 2, 3)

    assertThat(PlotMath.minMaxDecimate(values, maxPoints = 800)).isSameInstanceAs(values)
  }

  @Test
  fun dragPanAccumulator_pansProportionallyToDistance() {
    val accumulator = DragPanAccumulator(pixelsPerStep = 18f)

    // Twenty 10 px drag events = 200 px = 11 whole steps; the old bug grew this exponentially.
    val total = (1..20).sumOf { accumulator.add(10f) }

    assertThat(total).isEqualTo(11)
  }

  @Test
  fun dragPanAccumulator_handlesDirectionAndRemainder() {
    val accumulator = DragPanAccumulator(pixelsPerStep = 18f)

    assertThat(accumulator.add(10f)).isEqualTo(0)
    assertThat(accumulator.add(10f)).isEqualTo(1)
    assertThat(accumulator.add(-40f)).isEqualTo(-2)
  }
}
