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
}
