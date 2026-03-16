package com.mordin.samathascope.scene.tower

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class SkyTowerSettings(
  val baseWidthScale: Float = DEFAULT_BASE_WIDTH_SCALE,
  val carrierSpeedMultiplier: Float = DEFAULT_CARRIER_SPEED_MULTIPLIER,
  val irregularity: Float = DEFAULT_IRREGULARITY,
  val collapseEnabled: Boolean = DEFAULT_COLLAPSE_ENABLED,
) {
  fun clamped(): SkyTowerSettings {
    return copy(
      baseWidthScale = baseWidthScale.coerceIn(BASE_WIDTH_SCALE_RANGE.start, BASE_WIDTH_SCALE_RANGE.endInclusive),
      carrierSpeedMultiplier = carrierSpeedMultiplier.coerceIn(CARRIER_SPEED_RANGE.start, CARRIER_SPEED_RANGE.endInclusive),
      irregularity = irregularity.coerceIn(IRREGULARITY_RANGE.start, IRREGULARITY_RANGE.endInclusive),
    )
  }

  companion object {
    val BASE_WIDTH_SCALE_RANGE = 0.62f..1.12f
    val CARRIER_SPEED_RANGE = 0.90f..1.85f
    val IRREGULARITY_RANGE = 0.00f..1.00f

    const val DEFAULT_BASE_WIDTH_SCALE = 0.79f
    const val DEFAULT_CARRIER_SPEED_MULTIPLIER = 1.28f
    const val DEFAULT_IRREGULARITY = 0.58f
    const val DEFAULT_COLLAPSE_ENABLED = true
  }
}

data class TowerPoint(
  val x: Float,
  val y: Float,
)

data class TowerStoneLobe(
  val centerX: Float,
  val centerY: Float,
  val radiusX: Float,
  val radiusY: Float,
  val fixtureVertices: List<TowerPoint>,
)

enum class TowerBlockShapeKind {
  RECTANGLE,
  RIVER_STONE,
  PEBBLE_LEFT,
  PEBBLE_RIGHT,
  ROUNDED_SLAB,
  ROUNDED_BOULDER,
}

data class TowerBlockShape(
  val kind: TowerBlockShapeKind,
  val width: Float,
  val height: Float,
  val localVertices: List<TowerPoint>,
  val lobes: List<TowerStoneLobe> = emptyList(),
  val topSupportWidth: Float = width,
  val bottomSupportWidth: Float = width,
  val topSupportOffsetX: Float = 0f,
  val bottomSupportOffsetX: Float = 0f,
)

data class TowerResolvedSettings(
  val foundationWidth: Float,
  val carrierSpeedMultiplier: Float,
  val irregularity: Float,
)

private data class StoneCompositeProfile(
  val kind: TowerBlockShapeKind,
  val preferredLobes: Int,
  val asymmetryBias: Float,
  val ridgeLift: Float,
  val endDrop: Float,
  val flattening: Float,
)

private data class TowerBounds(
  val minX: Float,
  val maxX: Float,
  val minY: Float,
  val maxY: Float,
) {
  val width: Float get() = maxX - minX
  val height: Float get() = maxY - minY
  val centerX: Float get() = (minX + maxX) * 0.5f
  val centerY: Float get() = (minY + maxY) * 0.5f
}

private data class TowerSupportSpan(
  val centerX: Float,
  val width: Float,
)

internal fun resolveSkyTowerSettings(settings: SkyTowerSettings): TowerResolvedSettings {
  val clamped = settings.clamped()
  return TowerResolvedSettings(
    foundationWidth = BASE_FOUNDATION_WIDTH * clamped.baseWidthScale,
    carrierSpeedMultiplier = clamped.carrierSpeedMultiplier,
    irregularity = clamped.irregularity,
  )
}

internal fun createFoundationShape(settings: SkyTowerSettings): TowerBlockShape {
  val resolved = resolveSkyTowerSettings(settings)
  return rectangleShape(
    width = resolved.foundationWidth,
    height = FOUNDATION_HEIGHT,
  )
}

internal fun createTowerBlockShape(
  blockIndex: Int,
  settings: SkyTowerSettings,
): TowerBlockShape {
  val resolved = resolveSkyTowerSettings(settings)
  if (resolved.irregularity <= 0f) {
    return rectangleShape(
      width = BASE_BLOCK_WIDTH,
      height = TowerPhysics.BLOCK_HEIGHT,
    )
  }

  val widthVariance = centeredNoise(blockIndex, salt = 7) * (0.18f * resolved.irregularity)
  val targetWidth = (
    BASE_BLOCK_WIDTH * (1f + widthVariance)
    ).coerceIn(BASE_BLOCK_WIDTH * 0.84f, BASE_BLOCK_WIDTH * 1.24f)
  val heightVariance = stoneNoise(blockIndex, salt = 19) * (0.16f * resolved.irregularity)
  val targetHeight = (
    TowerPhysics.BLOCK_HEIGHT * (0.94f + heightVariance)
    ).coerceIn(TowerPhysics.BLOCK_HEIGHT * 0.92f, TowerPhysics.BLOCK_HEIGHT * 1.18f)
  val profile = STONE_PROFILES[blockIndex.mod(STONE_PROFILES.size)]
  return createCompositeStoneShape(
    blockIndex = blockIndex,
    profile = profile,
    targetWidth = targetWidth,
    targetHeight = targetHeight,
    irregularity = resolved.irregularity,
  )
}

private fun createCompositeStoneShape(
  blockIndex: Int,
  profile: StoneCompositeProfile,
  targetWidth: Float,
  targetHeight: Float,
  irregularity: Float,
): TowerBlockShape {
  val lobeCountOffset = (stoneNoise(blockIndex, salt = 41) * 3f).toInt() - 1
  val lobeCount = (profile.preferredLobes + lobeCountOffset).coerceIn(2, 4)
  val rawLobes = buildList {
    for (lobeIndex in 0 until lobeCount) {
      add(
        createRawStoneLobe(
          blockIndex = blockIndex,
          lobeIndex = lobeIndex,
          lobeCount = lobeCount,
          profile = profile,
          irregularity = irregularity,
        )
      )
    }
  }
  val rawBounds = boundsForLobes(rawLobes)
  val scaleX = targetWidth / rawBounds.width.coerceAtLeast(0.001f)
  val scaleY = targetHeight / rawBounds.height.coerceAtLeast(0.001f)
  val centeredLobes = rawLobes.map { raw ->
    val centerX = (raw.centerX - rawBounds.centerX) * scaleX
    val centerY = (raw.centerY - rawBounds.centerY) * scaleY
    val radiusX = raw.radiusX * scaleX
    val radiusY = raw.radiusY * scaleY
    TowerStoneLobe(
      centerX = centerX,
      centerY = centerY,
      radiusX = radiusX,
      radiusY = radiusY,
      fixtureVertices = ellipseVertices(
        centerX = centerX,
        centerY = centerY,
        radiusX = radiusX,
        radiusY = radiusY,
      ),
    )
  }
  val centeredBounds = boundsForLobes(centeredLobes)
  val outline = sampleUnionOutline(centeredLobes, centeredBounds)
  val topProbeY = centeredBounds.minY + (centeredBounds.height * 0.24f)
  val bottomProbeY = centeredBounds.maxY - (centeredBounds.height * 0.22f)
  val topSpan = supportSpanAtY(centeredLobes, topProbeY, centeredBounds.width)
  val bottomSpan = supportSpanAtY(centeredLobes, bottomProbeY, centeredBounds.width)

  return TowerBlockShape(
    kind = profile.kind,
    width = centeredBounds.width,
    height = centeredBounds.height,
    localVertices = outline,
    lobes = centeredLobes,
    topSupportWidth = topSpan.width,
    bottomSupportWidth = bottomSpan.width,
    topSupportOffsetX = topSpan.centerX,
    bottomSupportOffsetX = bottomSpan.centerX,
  )
}

private fun createRawStoneLobe(
  blockIndex: Int,
  lobeIndex: Int,
  lobeCount: Int,
  profile: StoneCompositeProfile,
  irregularity: Float,
): TowerStoneLobe {
  val progress = if (lobeCount == 1) {
    0.5f
  } else {
    lobeIndex / (lobeCount - 1f)
  }
  val arc = progress - 0.5f
  val spread = when (lobeCount) {
    2 -> 0.44f
    3 -> 0.60f
    else -> 0.72f
  }
  val radiusXBase = when (lobeCount) {
    2 -> 0.34f
    3 -> 0.27f
    else -> 0.22f
  }
  val widthNoise = stoneNoise(blockIndex + lobeIndex, salt = 73)
  val heightNoise = stoneNoise(blockIndex + lobeIndex, salt = 89)
  val centerJitter = centeredNoise(blockIndex + lobeIndex, salt = 97) * (0.03f + (irregularity * 0.05f))
  val centerX = (
    arc * spread +
      centerJitter +
      (profile.asymmetryBias * 0.08f)
    ).coerceIn(-0.52f, 0.52f)
  val centerY = (
    (abs(arc) * profile.endDrop * 0.12f) -
      (profile.ridgeLift * 0.05f) +
      centeredNoise(blockIndex + lobeIndex, salt = 113) * (0.02f + irregularity * 0.05f)
    ).coerceIn(-0.28f, 0.28f)
  val radiusX = (
    radiusXBase * (0.94f + widthNoise * 0.30f)
    ).coerceIn(0.16f, 0.40f)
  val radiusY = (
    (0.34f - (profile.flattening * 0.04f) + (heightNoise * 0.08f)) *
      (0.90f + (profile.ridgeLift * 0.05f) - (abs(arc) * profile.endDrop * 0.08f))
    ).coerceIn(0.22f, 0.42f)
  return TowerStoneLobe(
    centerX = centerX,
    centerY = centerY,
    radiusX = radiusX,
    radiusY = radiusY,
    fixtureVertices = emptyList(),
  )
}

internal fun rectangleShape(
  width: Float,
  height: Float,
): TowerBlockShape {
  return TowerBlockShape(
    kind = TowerBlockShapeKind.RECTANGLE,
    width = width,
    height = height,
    localVertices = listOf(
      TowerPoint(x = -width * 0.5f, y = -height * 0.5f),
      TowerPoint(x = width * 0.5f, y = -height * 0.5f),
      TowerPoint(x = width * 0.5f, y = height * 0.5f),
      TowerPoint(x = -width * 0.5f, y = height * 0.5f),
    ),
  )
}

private fun boundsForLobes(lobes: List<TowerStoneLobe>): TowerBounds {
  val minX = lobes.minOf { it.centerX - it.radiusX }
  val maxX = lobes.maxOf { it.centerX + it.radiusX }
  val minY = lobes.minOf { it.centerY - it.radiusY }
  val maxY = lobes.maxOf { it.centerY + it.radiusY }
  return TowerBounds(minX = minX, maxX = maxX, minY = minY, maxY = maxY)
}

private fun sampleUnionOutline(
  lobes: List<TowerStoneLobe>,
  bounds: TowerBounds,
): List<TowerPoint> {
  val top = buildList {
    for (sampleIndex in 0..OUTLINE_SAMPLE_COUNT) {
      val progress = sampleIndex / OUTLINE_SAMPLE_COUNT.toFloat()
      val x = lerp(bounds.minX, bounds.maxX, progress)
      val extrema = yExtremaAtX(lobes, x) ?: continue
      add(TowerPoint(x = x, y = extrema.first))
    }
  }
  val bottom = buildList {
    for (sampleIndex in OUTLINE_SAMPLE_COUNT downTo 0) {
      val progress = sampleIndex / OUTLINE_SAMPLE_COUNT.toFloat()
      val x = lerp(bounds.minX, bounds.maxX, progress)
      val extrema = yExtremaAtX(lobes, x) ?: continue
      add(TowerPoint(x = x, y = extrema.second))
    }
  }
  val combined = (top + bottom).distinct()
  return ensureCounterClockwise(
    if (combined.size >= 6) combined else {
      listOf(
        TowerPoint(bounds.minX, bounds.minY),
        TowerPoint(bounds.maxX, bounds.minY),
        TowerPoint(bounds.maxX, bounds.maxY),
        TowerPoint(bounds.minX, bounds.maxY),
      )
    }
  )
}

private fun yExtremaAtX(
  lobes: List<TowerStoneLobe>,
  x: Float,
): Pair<Float, Float>? {
  var top = Float.POSITIVE_INFINITY
  var bottom = Float.NEGATIVE_INFINITY
  for (lobe in lobes) {
    val normalizedX = (x - lobe.centerX) / lobe.radiusX
    if (abs(normalizedX) > 1f) continue
    val yFactor = sqrt((1f - (normalizedX * normalizedX)).coerceAtLeast(0f))
    val topY = lobe.centerY - (lobe.radiusY * yFactor)
    val bottomY = lobe.centerY + (lobe.radiusY * yFactor)
    top = min(top, topY)
    bottom = max(bottom, bottomY)
  }
  if (top == Float.POSITIVE_INFINITY || bottom == Float.NEGATIVE_INFINITY) {
    return null
  }
  return top to bottom
}

private fun supportSpanAtY(
  lobes: List<TowerStoneLobe>,
  y: Float,
  overallWidth: Float,
): TowerSupportSpan {
  val intervals = lobes.mapNotNull { lobe ->
    val normalizedY = (y - lobe.centerY) / lobe.radiusY
    if (abs(normalizedY) > 1f) return@mapNotNull null
    val xFactor = sqrt((1f - (normalizedY * normalizedY)).coerceAtLeast(0f))
    val halfWidth = lobe.radiusX * xFactor
    (lobe.centerX - halfWidth) to (lobe.centerX + halfWidth)
  }.sortedBy { it.first }
  if (intervals.isEmpty()) {
    return TowerSupportSpan(centerX = 0f, width = overallWidth * 0.38f)
  }
  val merged = mutableListOf<Pair<Float, Float>>()
  for ((left, right) in intervals) {
    val last = merged.lastOrNull()
    if (last == null || left > last.second + 0.01f) {
      merged += left to right
    } else {
      merged[merged.lastIndex] = last.first to max(last.second, right)
    }
  }
  val widest = merged.maxByOrNull { it.second - it.first } ?: merged.first()
  return TowerSupportSpan(
    centerX = (widest.first + widest.second) * 0.5f,
    width = (widest.second - widest.first).coerceIn(overallWidth * 0.24f, overallWidth),
  )
}

private fun ellipseVertices(
  centerX: Float,
  centerY: Float,
  radiusX: Float,
  radiusY: Float,
  segments: Int = 10,
): List<TowerPoint> {
  val vertices = buildList {
    for (segment in 0 until segments) {
      val angle = (segment / segments.toFloat()) * (PI * 2.0)
      add(
        TowerPoint(
          x = centerX + (cos(angle).toFloat() * radiusX),
          y = centerY + (sin(angle).toFloat() * radiusY),
        )
      )
    }
  }
  return ensureCounterClockwise(vertices)
}

private fun ensureCounterClockwise(vertices: List<TowerPoint>): List<TowerPoint> {
  if (signedArea(vertices) >= 0f) {
    return vertices
  }
  return vertices.reversed()
}

private fun signedArea(vertices: List<TowerPoint>): Float {
  var area = 0f
  for (index in vertices.indices) {
    val current = vertices[index]
    val next = vertices[(index + 1) % vertices.size]
    area += (current.x * next.y) - (next.x * current.y)
  }
  return area * 0.5f
}

private fun stoneNoise(seed: Int, salt: Int): Float {
  val mixed = seed xor (salt * 0x45D9F3B)
  val hashed = (mixed * 0x27D4EB2D) xor (mixed ushr 15)
  val normalized = (hashed and 0x7FFFFFFF).toFloat() / Int.MAX_VALUE.toFloat()
  return normalized.coerceIn(0f, 1f)
}

private fun centeredNoise(seed: Int, salt: Int): Float {
  return ((stoneNoise(seed, salt) * 2f) - 1f).coerceIn(-1f, 1f)
}

private fun lerp(from: Float, to: Float, progress: Float): Float {
  return from + ((to - from) * progress.coerceIn(0f, 1f))
}

private val STONE_PROFILES = listOf(
  StoneCompositeProfile(
    kind = TowerBlockShapeKind.ROUNDED_SLAB,
    preferredLobes = 3,
    asymmetryBias = 0.00f,
    ridgeLift = 0.10f,
    endDrop = 0.25f,
    flattening = 0.22f,
  ),
  StoneCompositeProfile(
    kind = TowerBlockShapeKind.RIVER_STONE,
    preferredLobes = 3,
    asymmetryBias = 0.00f,
    ridgeLift = 0.04f,
    endDrop = 0.32f,
    flattening = 0.08f,
  ),
  StoneCompositeProfile(
    kind = TowerBlockShapeKind.PEBBLE_LEFT,
    preferredLobes = 3,
    asymmetryBias = -0.18f,
    ridgeLift = 0.06f,
    endDrop = 0.34f,
    flattening = 0.10f,
  ),
  StoneCompositeProfile(
    kind = TowerBlockShapeKind.PEBBLE_RIGHT,
    preferredLobes = 3,
    asymmetryBias = 0.18f,
    ridgeLift = 0.06f,
    endDrop = 0.34f,
    flattening = 0.10f,
  ),
  StoneCompositeProfile(
    kind = TowerBlockShapeKind.ROUNDED_BOULDER,
    preferredLobes = 4,
    asymmetryBias = 0.05f,
    ridgeLift = 0.14f,
    endDrop = 0.38f,
    flattening = 0.02f,
  ),
)

private const val BASE_FOUNDATION_WIDTH = 3.4f
private const val BASE_BLOCK_WIDTH = 1.55f
private const val FOUNDATION_HEIGHT = 0.62f
private const val OUTLINE_SAMPLE_COUNT = 20
