package com.mordin.samathascope

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.exp
import kotlin.math.pow
import kotlin.random.Random

class NoiseAudioEngine(
  private val sampleRate: Int = 48_000,
  private val frameSize: Int = 480,
) {
  private data class NoiseState(
    var brown: Float = 0f,
    var lastWhite: Float = 0f,
    var blueMemory: Float = 0f,
    var pink0: Float = 0f,
    var pink1: Float = 0f,
    var pink2: Float = 0f,
    var pink3: Float = 0f,
    var pink4: Float = 0f,
    var pink5: Float = 0f,
    var pink6: Float = 0f,
  )

  private var track: AudioTrack? = null
  private var thread: Thread? = null
  private val leftNoise = NoiseState()
  private val rightNoise = NoiseState()

  @Volatile private var running = false
  @Volatile private var muted = true

  @Volatile private var targetFeedback: Float = 0f
  @Volatile private var invertReward: Boolean = false
  @Volatile private var noiseColor: NoiseColor = NoiseColor.WHITE
  @Volatile private var gamma: Float = 1.6f
  @Volatile private var gMinDb: Int = -30
  @Volatile private var gMaxDb: Int = -3

  @Volatile var debugBaseDb: Float = -120f
    private set

  private var baseAmp: Float = 0f
  private var fadeIn: Float = 0f
  private var fading = false
  private var masterAudibility: Float = 0f

  private val attackMs = 300f
  private val releaseMs = 1500f

  fun start() {
    if (running) return
    running = true
    masterAudibility = 0f
    fadeIn = 0f
    fading = false

    val minBuffer = AudioTrack.getMinBufferSize(
      sampleRate,
      AudioFormat.CHANNEL_OUT_STEREO,
      AudioFormat.ENCODING_PCM_16BIT,
    ).coerceAtLeast(frameSize * 2 * 2 * 4)

    track = AudioTrack.Builder()
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
          .build()
      )
      .setAudioFormat(
        AudioFormat.Builder()
          .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
          .setSampleRate(sampleRate)
          .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
          .build()
      )
      .setTransferMode(AudioTrack.MODE_STREAM)
      .setBufferSizeInBytes(minBuffer)
      .build()

    track?.play()
    track?.setVolume(1f)
    thread = Thread { audioLoop() }.apply {
      isDaemon = true
      start()
    }
  }

  fun stop() {
    running = false
    try { thread?.join(300) } catch (_: Throwable) {}
    thread = null
    try { track?.stop() } catch (_: Throwable) {}
    try { track?.release() } catch (_: Throwable) {}
    track = null
  }

  fun beginFadeIn() {
    fading = true
    fadeIn = 0f
  }

  fun setMuted(value: Boolean) {
    muted = value
  }

  fun update(
    feedbackValue: Float,
    invertReward: Boolean,
    noiseColor: NoiseColor,
    gamma: Float,
    gMinDb: Int,
    gMaxDb: Int,
  ) {
    targetFeedback = feedbackValue.coerceIn(0f, 1f)
    this.invertReward = invertReward
    this.noiseColor = noiseColor
    this.gamma = gamma.coerceIn(0.6f, 3.0f)
    this.gMinDb = gMinDb
    this.gMaxDb = gMaxDb
  }

  private fun audioLoop() {
    val track = track ?: return
    val buffer = ShortArray(frameSize * 2)
    val dt = frameSize.toFloat() / sampleRate.toFloat()

    while (running) {
      val signal = targetFeedback
      val mapped = if (!invertReward) (1f - signal).coerceIn(0f, 1f) else signal
      val shaped = mapped.pow(gamma)
      val baseDb = gMinDb + shaped * (gMaxDb - gMinDb)
      debugBaseDb = baseDb

      val targetAmp = dbToAmp(baseDb)
      val tau = if (targetAmp > baseAmp) attackMs / 1000f else releaseMs / 1000f
      val smoothing = 1f - exp(-dt / tau)
      baseAmp += smoothing * (targetAmp - baseAmp)

      if (fading) {
        fadeIn += dt / 0.8f
        if (fadeIn >= 1f) {
          fadeIn = 1f
          fading = false
        }
      }

      val masterTarget = if (muted) 0f else 1f
      val masterTau = if (masterTarget < masterAudibility) 0.45f else 0.12f
      val masterAlpha = 1f - exp(-dt / masterTau)
      masterAudibility += masterAlpha * (masterTarget - masterAudibility)
      val master = masterAudibility * if (fading) fadeIn else 1f

      var cursor = 0
      repeat(frameSize) {
        val left = sampleNoise(leftNoise, noiseColor) * baseAmp * master
        val right = sampleNoise(rightNoise, noiseColor) * baseAmp * master
        buffer[cursor++] = floatToPcm16(left)
        buffer[cursor++] = floatToPcm16(right)
      }

      track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
    }
  }

  private fun dbToAmp(db: Float): Float {
    return 10.0.pow((db / 20.0).toDouble()).toFloat()
  }

  private fun floatToPcm16(value: Float): Short {
    return (value.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
  }

  private fun sampleNoise(state: NoiseState, color: NoiseColor): Float {
    val white = (Random.nextFloat() * 2f) - 1f
    return when (color) {
      NoiseColor.WHITE -> white
      NoiseColor.PINK -> samplePink(state, white)
      NoiseColor.BROWN -> sampleBrown(state, white)
      NoiseColor.BLUE -> sampleBlue(state, white)
    }.coerceIn(-1f, 1f)
  }

  private fun samplePink(state: NoiseState, white: Float): Float {
    state.pink0 = (0.99886f * state.pink0) + (white * 0.0555179f)
    state.pink1 = (0.99332f * state.pink1) + (white * 0.0750759f)
    state.pink2 = (0.96900f * state.pink2) + (white * 0.1538520f)
    state.pink3 = (0.86650f * state.pink3) + (white * 0.3104856f)
    state.pink4 = (0.55000f * state.pink4) + (white * 0.5329522f)
    state.pink5 = (-0.7616f * state.pink5) - (white * 0.0168980f)
    val pink = (
      state.pink0 +
        state.pink1 +
        state.pink2 +
        state.pink3 +
        state.pink4 +
        state.pink5 +
        state.pink6 +
        (white * 0.5362f)
      ) * 0.18f
    state.pink6 = white * 0.115926f
    return pink
  }

  private fun sampleBrown(state: NoiseState, white: Float): Float {
    state.brown = ((state.brown + (white * 0.08f)) * 0.985f).coerceIn(-1.2f, 1.2f)
    return (state.brown * 2.6f).coerceIn(-1f, 1f)
  }

  private fun sampleBlue(state: NoiseState, white: Float): Float {
    val differentiated = white - state.lastWhite
    state.lastWhite = white
    state.blueMemory = (state.blueMemory * 0.22f) + differentiated
    return (state.blueMemory * 1.6f).coerceIn(-1f, 1f)
  }
}
