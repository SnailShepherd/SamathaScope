package com.mordin.samathascope.scene.tower

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
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
  RIVER_STONE_A,
  RIVER_STONE_B,
  RIVER_STONE_C,
  RIVER_STONE_D,
  RIVER_STONE_E,
  RIVER_STONE_F,
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

private data class RiverStoneProfile(
  val kind: TowerBlockShapeKind,
  /** Superellipse exponent: 2=ellipse, higher=more rectangular/rounded-rect */
  val exponent: Float,
  /** Horizontal stretch factor (>1 means wider/flatter) */
  val aspectStretch: Float,
  /** Base squash: flatten the bottom half slightly (0=none, 1=full squash) */
  val baseSquash: Float,
  /** Left-right asymmetry bias (-1..1) */
  val asymmetryBias: Float,
)

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

  val widthVariance = centeredNoise(blockIndex, salt = 7) * (0.25f * resolved.irregularity)
  val targetWidth = (
    BASE_BLOCK_WIDTH * (1f + widthVariance)
    ).coerceIn(BASE_BLOCK_WIDTH * 0.75f, BASE_BLOCK_WIDTH * 1.30f)
  val heightVariance = stoneNoise(blockIndex, salt = 19) * (0.22f * resolved.irregularity)
  val targetHeight = (
    TowerPhysics.BLOCK_HEIGHT * (0.92f + heightVariance)
    ).coerceIn(TowerPhysics.BLOCK_HEIGHT * 0.82f, TowerPhysics.BLOCK_HEIGHT * 1.26f)
  val profile = STONE_PROFILES[blockIndex.mod(STONE_PROFILES.size)]
  return createRiverStoneShape(
    blockIndex = blockIndex,
    profile = profile,
    targetWidth = targetWidth,
    targetHeight = targetHeight,
    irregularity = resolved.irregularity,
  )
}

private fun createRiverStoneShape(
  blockIndex: Int,
  profile: RiverStoneProfile,
  targetWidth: Float,
  targetHeight: Float,
  irregularity: Float,
): TowerBlockShape {
  val stretchFactor = profile.aspectStretch + centeredNoise(blockIndex, salt = 63) * (0.12f * irregularity)
  val halfW = targetWidth * 0.5f * stretchFactor
  val halfH = targetHeight * 0.5f
  val exponent = (profile.exponent + centeredNoise(blockIndex, salt = 51) * (0.8f * irregularity)).coerceAtLeast(1.8f)

  // Generate superellipse outline with organic wobble
  val vertices = buildList {
    for (i in 0 until SUPERELLIPSE_VERTEX_COUNT) {
      val angle = (i.toFloat() / SUPERELLIPSE_VERTEX_COUNT) * (2.0 * PI).toFloat()
      val cosA = cos(angle)
      val sinA = sin(angle)

      // Superellipse: |x/a|^n + |y/b|^n = 1  →  parametric form
      val absC = abs(cosA)
      val absS = abs(sinA)
      val rx = sign(cosA) * absC.pow(2f / exponent) * halfW
      var ry = sign(sinA) * absS.pow(2f / exponent) * halfH

      // Flatten the bottom (sinA > 0 = bottom half in screen coords)
      if (sinA > 0f) {
        ry *= 1f - (sinA * profile.baseSquash * 0.65f)
      }

      // Asymmetry: shift the horizontal radius slightly left or right
      val asymShift = profile.asymmetryBias * halfW * 0.06f * cosA

      // Per-vertex wobble for organic feel
      val wobbleAmp = irregularity * 0.03f * halfW
      val wobble = centeredNoise(blockIndex * SUPERELLIPSE_VERTEX_COUNT + i, salt = 137) * wobbleAmp

      val radialLen = sqrt(rx * rx + ry * ry).coerceAtLeast(0.001f)
      val nx = rx / radialLen
      val ny = ry / radialLen

      add(TowerPoint(x = rx + asymShift + nx * wobble, y = ry + ny * wobble))
    }
  }

  val outline = ensureCounterClockwise(vertices)

  // Compute support spans by probing the outline at top/bottom bands
  val topProbeY = -halfH * 0.52f
  val bottomProbeY = halfH * 0.48f
  val topSpan = supportSpanFromOutline(outline, topProbeY, halfW * 2f)
  val bottomSpan = supportSpanFromOutline(outline, bottomProbeY, halfW * 2f)

  // Build a single lobe that covers the full stone for painterly rendering
  val lobe = TowerStoneLobe(
    centerX = 0f,
    centerY = 0f,
    radiusX = halfW,
    radiusY = halfH,
    fixtureVertices = outline,
  )

  val adjustedWidth = halfW * 2f
  return TowerBlockShape(
    kind = profile.kind,
    width = adjustedWidth,
    height = targetHeight,
    localVertices = outline,
    lobes = listOf(lobe),
    topSupportWidth = topSpan.width.coerceAtMost(adjustedWidth),
    bottomSupportWidth = bottomSpan.width.coerceAtMost(adjustedWidth),
    topSupportOffsetX = topSpan.centerX,
    bottomSupportOffsetX = bottomSpan.centerX,
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

private fun supportSpanFromOutline(
  outline: List<TowerPoint>,
  probeY: Float,
  fallbackWidth: Float,
): TowerSupportSpan {
  val tolerance = fallbackWidth * 0.15f
  val nearVertices = outline.filter { abs(it.y - probeY) <= tolerance }
  if (nearVertices.size < 2) {
    return TowerSupportSpan(centerX = 0f, width = fallbackWidth * 0.8f)
  }
  val minX = nearVertices.minOf { it.x }
  val maxX = nearVertices.maxOf { it.x }
  return TowerSupportSpan(
    centerX = (minX + maxX) * 0.5f,
    width = (maxX - minX).coerceAtLeast(fallbackWidth * 0.3f),
  )
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

private val STONE_PROFILES = listOf(
  RiverStoneProfile(
    kind = TowerBlockShapeKind.RIVER_STONE_A,
    exponent = 2.3f,
    aspectStretch = 1.05f,
    baseSquash = 0.20f,
    asymmetryBias = 0.05f,
  ),
  RiverStoneProfile(
    kind = TowerBlockShapeKind.RIVER_STONE_B,
    exponent = 2.8f,
    aspectStretch = 0.95f,
    baseSquash = 0.40f,
    asymmetryBias = -0.18f,
  ),
  RiverStoneProfile(
    kind = TowerBlockShapeKind.RIVER_STONE_C,
    exponent = 3.4f,
    aspectStretch = 1.08f,
    baseSquash = 0.55f,
    asymmetryBias = 0.12f,
  ),
  RiverStoneProfile(
    kind = TowerBlockShapeKind.RIVER_STONE_D,
    exponent = 4.0f,
    aspectStretch = 0.92f,
    baseSquash = 0.30f,
    asymmetryBias = -0.22f,
  ),
  RiverStoneProfile(
    kind = TowerBlockShapeKind.RIVER_STONE_E,
    exponent = 4.6f,
    aspectStretch = 1.12f,
    baseSquash = 0.65f,
    asymmetryBias = 0.20f,
  ),
  RiverStoneProfile(
    kind = TowerBlockShapeKind.RIVER_STONE_F,
    exponent = 5.2f,
    aspectStretch = 0.88f,
    baseSquash = 0.45f,
    asymmetryBias = -0.10f,
  ),
)

private const val BASE_FOUNDATION_WIDTH = 3.4f
private const val BASE_BLOCK_WIDTH = 1.55f
private const val FOUNDATION_HEIGHT = 0.62f
private const val SUPERELLIPSE_VERTEX_COUNT = 32
