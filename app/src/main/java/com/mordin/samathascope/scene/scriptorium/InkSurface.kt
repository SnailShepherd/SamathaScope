package com.mordin.samathascope.scene.scriptorium

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer

@Composable
fun InkSurface(
  state: InkRenderState,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  AndroidView(
    modifier = modifier
      .fillMaxWidth()
      .height(320.dp),
    factory = {
      InkSurfaceView(context).apply {
        updateRenderState(state)
      }
    },
    update = { view ->
      view.updateRenderState(state)
    },
  )
}

private class InkSurfaceView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
) : View(context, attrs) {
  private val renderer = CanvasStrokeRenderer.create(false)
  private val strokeMatrix = Matrix()
  private val pagePaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = 1.5f
  }
  private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

  private var state = InkRenderState()

  fun updateRenderState(state: InkRenderState) {
    this.state = state
    invalidate()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val width = width.toFloat()
    val height = height.toFloat()
    val pageRectLeft = width * 0.06f
    val pageRectTop = height * 0.06f
    val pageRectWidth = width * 0.88f
    val pageRectHeight = height * 0.88f

    val parchment = interpolateColor(
      from = Color.parseColor("#F6EBD1"),
      to = Color.parseColor("#E2CF9C"),
      amount = state.paperWarmth,
    )
    pagePaint.color = parchment
    canvas.drawRoundRect(
      pageRectLeft,
      pageRectTop,
      pageRectLeft + pageRectWidth,
      pageRectTop + pageRectHeight,
      26f,
      26f,
      pagePaint,
    )

    glowPaint.color = interpolateColor(
      from = Color.parseColor("#22FFF4D0"),
      to = Color.parseColor("#55F6E5A7"),
      amount = state.ornamentGlow,
    )
    canvas.drawCircle(width * 0.18f, height * 0.14f, height * (0.06f + (state.ornamentGlow * 0.05f)), glowPaint)

    linePaint.color = Color.argb(
      (44 + (state.paperContrast * 30f)).toInt(),
      123,
      98,
      67,
    )
    repeat(5) { line ->
      val y = pageRectTop + (pageRectHeight * (0.26f + (line * 0.16f)))
      canvas.drawLine(
        pageRectLeft + (pageRectWidth * 0.09f),
        y,
        pageRectLeft + (pageRectWidth * 0.88f),
        y,
        linePaint,
      )
    }

    strokeMatrix.reset()
    strokeMatrix.setScale(pageRectWidth, pageRectHeight)
    strokeMatrix.postTranslate(pageRectLeft, pageRectTop)

    state.finishedStrokes.forEach { stroke ->
      renderer.draw(canvas, stroke, strokeMatrix)
    }
    state.activeStroke?.let { inProgress ->
      renderer.draw(canvas, inProgress, strokeMatrix)
    }
  }

  private fun interpolateColor(from: Int, to: Int, amount: Float): Int {
    val clamped = amount.coerceIn(0f, 1f)
    val a = android.graphics.Color.alpha(from) + ((android.graphics.Color.alpha(to) - android.graphics.Color.alpha(from)) * clamped).toInt()
    val r = android.graphics.Color.red(from) + ((android.graphics.Color.red(to) - android.graphics.Color.red(from)) * clamped).toInt()
    val g = android.graphics.Color.green(from) + ((android.graphics.Color.green(to) - android.graphics.Color.green(from)) * clamped).toInt()
    val b = android.graphics.Color.blue(from) + ((android.graphics.Color.blue(to) - android.graphics.Color.blue(from)) * clamped).toInt()
    return android.graphics.Color.argb(a, r, g, b)
  }
}
