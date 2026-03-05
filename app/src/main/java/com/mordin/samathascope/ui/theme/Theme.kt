package com.mordin.samathascope.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
  primary = Teal500,
  onPrimary = OnTeal,
  primaryContainer = Teal200,
  onPrimaryContainer = Teal900,
  secondary = Teal700,
  onSecondary = OnTeal,
  secondaryContainer = Teal200,
  onSecondaryContainer = Teal900,
  tertiary = Teal900,
  onTertiary = OnTeal,
  background = BackgroundLight,
  onBackground = Color(0xFF0F1B1B),
  surface = SurfaceLight,
  onSurface = Color(0xFF0F1B1B),
  surfaceVariant = Color(0xFFE9F2F2),
  onSurfaceVariant = Color(0xFF203535),
  outline = OutlineLight,
)

private val DarkColors = darkColorScheme(
  primary = Teal200,
  onPrimary = Color(0xFF062222),
  primaryContainer = Teal700,
  onPrimaryContainer = Color(0xFFE9F2F2),
  secondary = Teal200,
  onSecondary = Color(0xFF062222),
  background = Color(0xFF061212),
  onBackground = Color(0xFFE9F2F2),
  surface = Color(0xFF0B1A1A),
  onSurface = Color(0xFFE9F2F2),
  surfaceVariant = Color(0xFF123030),
  onSurfaceVariant = Color(0xFFCFE6E6),
  outline = Color(0xFF3F7A7A),
)

private val AppShapes = Shapes(
  small = RoundedCornerShape(3.dp),
  medium = RoundedCornerShape(5.dp),
  large = RoundedCornerShape(8.dp),
)

@Composable
fun SamathaScopeTheme(
  darkTheme: Boolean = false,
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = if (darkTheme) DarkColors else LightColors,
    shapes = AppShapes,
    typography = MaterialTheme.typography,
    content = content
  )
}
