package com.mordin.samathascope

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

// Raw-EEG and metric plots, and the stable per-metric colours.


internal fun metricColorFor(type: PlotType): Color {
  return when (type) {
    PlotType.MEDITATION_PROXY -> Color(0xFF0B8F94)
    PlotType.SETTLEDNESS -> Color(0xFF4E9F3D)
    PlotType.CONTROL -> Color(0xFFB56A00)
    PlotType.ALERTNESS -> Color(0xFFE08A1E)
    PlotType.DROWSY_SCORE -> Color(0xFF6E6A8A)
    PlotType.ARTEFACT_SCORE -> Color(0xFFC15F7A)
    PlotType.QUALITY_CONFIDENCE -> Color(0xFF568CA7)
    PlotType.EFFORTFUL_FOCUS_SCORE -> Color(0xFF7A6FF0)
    PlotType.MIND_WANDERING_SCORE -> Color(0xFF8D4CA8)
    PlotType.ESENSE_MEDITATION -> Color(0xFF4F6BED)
    PlotType.ESENSE_ATTENTION -> Color(0xFF1F7C96)
    PlotType.RAW -> Color(0xFF0B8F94)
  }
}

@Composable
internal fun WaveformPlot(
  samples: IntArray,
  yMin: Float,
  yMax: Float,
  heightDp: androidx.compose.ui.unit.Dp = 180.dp,
  pannable: Boolean = false,
  onPan: (Int) -> Unit = {},
) {
  if (samples.size < 8) return

  val safeYMax = if (yMax <= yMin) yMin + 1f else yMax
  val colorScheme = MaterialTheme.colorScheme

  Canvas(
    modifier = Modifier
      .fillMaxWidth()
      .height(heightDp)
      .background(colorScheme.surface)
      .border(1.dp, colorScheme.outline, RoundedCornerShape(4.dp))
      .pointerInput(pannable) {
        if (!pannable) return@pointerInput
        val accumulator = DragPanAccumulator()
        detectHorizontalDragGestures { _, dragAmount ->
          val seconds = accumulator.add(dragAmount)
          if (seconds != 0) onPan(seconds)
        }
      }
      .padding(6.dp)
  ) {
    val w = size.width
    val h = size.height
    val span = safeYMax - yMin
    fun yFor(value: Float): Float = h - ((value.coerceIn(yMin, safeYMax) - yMin) / span) * h

    val zeroY = yFor(0f)
    drawLine(
      color = colorScheme.outline.copy(alpha = 0.5f),
      start = Offset(0f, zeroY),
      end = Offset(w, zeroY),
      strokeWidth = 1f
    )

    val step = w / (samples.size - 1).toFloat()
    val path = Path()
    path.moveTo(0f, yFor(samples[0].toFloat()))
    for (i in 1 until samples.size) {
      path.lineTo(i * step, yFor(samples[i].toFloat()))
    }
    drawPath(path = path, color = colorScheme.primary, style = Stroke(width = 2f))
  }
}

@Composable
internal fun MultiMetricPlot(
  series: Map<PlotType, List<Float>>,
  pannable: Boolean = false,
  onPan: (Int) -> Unit = {},
) {
  if (series.isEmpty()) return

  val colorScheme = MaterialTheme.colorScheme
  Canvas(
    modifier = Modifier
      .fillMaxWidth()
      .height(220.dp)
      .background(colorScheme.surface)
      .border(1.dp, colorScheme.outline, RoundedCornerShape(4.dp))
      .pointerInput(pannable) {
        if (!pannable) return@pointerInput
        val accumulator = DragPanAccumulator()
        detectHorizontalDragGestures { _, dragAmount ->
          val seconds = accumulator.add(dragAmount)
          if (seconds != 0) onPan(seconds)
        }
      }
      .padding(6.dp)
  ) {
    val w = size.width
    val h = size.height

    for (i in 0..4) {
      val y = h * i / 4f
      drawLine(
        color = colorScheme.outline.copy(alpha = 0.25f),
        start = Offset(0f, y),
        end = Offset(w, y),
        strokeWidth = 1f,
      )
    }

    series.entries.forEach { entry ->
      val values = entry.value
      if (values.size < 2) return@forEach
      val points = PlotMath.toPlotPoints(values, 0f, 100f, width = 1f, height = 1f)
      val path = Path()
      points.forEachIndexed { pointIndex, point ->
        val x = point.x * w
        val y = point.y * h
        if (pointIndex == 0) path.moveTo(x, y) else path.lineTo(x, y)
      }
      drawPath(
        path = path,
        color = metricColorFor(entry.key),
        style = Stroke(width = if (entry.key == PlotType.MEDITATION_PROXY) 3.8f else 3.0f),
      )
    }
  }
}
