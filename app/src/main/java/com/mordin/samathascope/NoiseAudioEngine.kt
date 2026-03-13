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
  private var track: AudioTrack? = null
  private var thread: Thread? = null

  @Volatile private var running = false
  @Volatile private var muted = true

  @Volatile private var targetFeedback: Float = 0f
  @Volatile private var invertReward: Boolean = false
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
    gamma: Float,
    gMinDb: Int,
    gMaxDb: Int,
  ) {
    targetFeedback = feedbackValue.coerceIn(0f, 1f)
    this.invertReward = invertReward
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
        val left = (Random.nextFloat() * 2f - 1f) * baseAmp * master
        val right = (Random.nextFloat() * 2f - 1f) * baseAmp * master
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
}
