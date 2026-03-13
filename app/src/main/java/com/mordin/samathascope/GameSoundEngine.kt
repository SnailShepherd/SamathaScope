package com.mordin.samathascope

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class GameSoundEngine(
  private val sampleRate: Int = 48_000,
  private val frameSize: Int = 480,
) {
  private var track: AudioTrack? = null
  private var thread: Thread? = null

  @Volatile private var running = false
  @Volatile private var muted = true
  @Volatile private var selectedGame: GameId = GameId.SKY_TOWER
  @Volatile private var ambience = 0f
  @Volatile private var motion = 0f
  @Volatile private var glitch = 0f
  @Volatile private var accent = 0f
  @Volatile private var warmth = 0f

  private var phaseMain = 0.0
  private var phaseSub = 0.0
  private var phaseMotion = 0.0
  private var masterAudibility = 0f
  private var fadeIn = 0f
  private var fading = false
  private var accentEnvelope = 0f
  private var lastAccentTarget = 0f

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

  fun update(gameId: GameId, audioState: GameAudioState) {
    selectedGame = gameId
    ambience = clamp01(audioState.ambience)
    motion = clamp01(audioState.motion)
    glitch = clamp01(audioState.glitch)
    accent = clamp01(audioState.accent)
    warmth = clamp01(audioState.warmth)
  }

  private fun audioLoop() {
    val track = track ?: return
    val buffer = ShortArray(frameSize * 2)
    val dt = frameSize.toFloat() / sampleRate.toFloat()

    while (running) {
      val masterTarget = if (muted) 0f else 1f
      val masterTau = if (masterTarget < masterAudibility) 0.5f else 0.12f
      val masterAlpha = 1f - exp(-dt / masterTau)
      masterAudibility += masterAlpha * (masterTarget - masterAudibility)
      if (fading) {
        fadeIn += dt / 0.8f
        if (fadeIn >= 1f) {
          fadeIn = 1f
          fading = false
        }
      }
      val master = masterAudibility * if (fading) fadeIn else 1f

      if (accent > lastAccentTarget + 0.10f) {
        accentEnvelope = 1f
      }
      lastAccentTarget = accent

      val baseFreq = when (selectedGame) {
        GameId.SKY_TOWER -> 110.0
        GameId.INK_GARDEN -> 220.0
        GameId.FIRE_KEEPER -> 96.0
        GameId.SCRIPTORIUM -> 176.0
      }
      val subFreq = when (selectedGame) {
        GameId.SKY_TOWER -> 55.0
        GameId.INK_GARDEN -> 147.0
        GameId.FIRE_KEEPER -> 64.0
        GameId.SCRIPTORIUM -> 132.0
      }
      val motionFreq = 0.6 + (motion * 4.0)

      var idx = 0
      for (sampleIndex in 0 until frameSize) {
        val wobble = sin(phaseMotion).toFloat()
        phaseMain += (2.0 * PI * (baseFreq + (motion * 18f * wobble))) / sampleRate
        phaseSub += (2.0 * PI * (subFreq + (warmth * 10f))) / sampleRate
        phaseMotion += (2.0 * PI * motionFreq) / sampleRate

        val tone = when (selectedGame) {
          GameId.SKY_TOWER -> {
            (0.55f * sin(phaseMain).toFloat()) + (0.25f * sin(phaseSub).toFloat())
          }
          GameId.INK_GARDEN -> {
            (0.45f * sin(phaseMain).toFloat()) + (0.30f * sin(phaseMain * 1.5).toFloat())
          }
          GameId.FIRE_KEEPER -> {
            (0.30f * sin(phaseSub).toFloat()) + (0.24f * sin(phaseMain).toFloat())
          }
          GameId.SCRIPTORIUM -> {
            (0.35f * sin(phaseMain).toFloat()) + (0.22f * sin(phaseMain * 2.0).toFloat())
          }
        }

        val hiss = (Random.nextFloat() * 2f - 1f) * glitch.pow(1.2f) * 0.18f
        val motionLayer = wobble * (0.04f + (motion * 0.12f))
        val accentTone = accentEnvelope * (0.18f + (warmth * 0.08f)) * sin(phaseMain * 2.4).toFloat()
        accentEnvelope *= exp(-1f / (sampleRate * 0.12f))

        val left = master * ((tone * (0.08f + (ambience * 0.14f))) + motionLayer + hiss + accentTone)
        val right = master * ((tone * (0.08f + (ambience * 0.14f))) - motionLayer + hiss - accentTone * 0.5f)

        buffer[idx++] = floatToPcm16(left)
        buffer[idx++] = floatToPcm16(right)
      }

      track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
    }
  }

  private fun floatToPcm16(value: Float): Short {
    return (value.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
  }
}
