package com.mordin.samathascope

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.exp
import kotlin.math.pow
import kotlin.random.Random

/**
 * White-noise neurofeedback audio engine (v0.1).
 *
 * Output:
 * - Stereo base white noise (gain driven by training score S)
 * - Independent "crackle" overlay (intensity driven by artefact score A)
 *
 * Design choices (why this way):
 * - We use `USAGE_MEDIA` so the sound is controlled by the device media volume.
 *   (Using SONIFICATION can end up on a muted system stream on some phones.)
 * - We generate PCM 16-bit, because it is supported everywhere; float PCM can fail on some devices.
 * - Attack/Release smoothing prevents flutter when the EEG-derived score jitters.
 */
class NoiseAudioEngine(
  private val sampleRate: Int = 48000,
  private val frameSize: Int = 480, // 10 ms
) {

  private var track: AudioTrack? = null
  private var thread: Thread? = null

  @Volatile private var running = false
  @Volatile private var muted = true

  // Target params (updated from UI thread)
  @Volatile private var targetS: Float = 0f
  @Volatile private var targetA: Float = 0f
  @Volatile private var invertReward: Boolean = false
  @Volatile private var gamma: Float = 1.6f
  @Volatile private var gMinDb: Int = -30
  @Volatile private var gMaxDb: Int = -3
  @Volatile private var crackleEnabled: Boolean = true
  @Volatile private var crackleIntensity: Float = 0.6f

  // Debug (shown in UI)
  @Volatile var debugBaseDb: Float = -120f
    private set

  // State
  private var baseAmp: Float = 0f
  private var fadeIn: Float = 0f
  private var fading = false

  // Crackle envelopes per channel
  private var crackleEnvL = 0f
  private var crackleEnvR = 0f

  // time constants
  private val attackMs = 300f   // louder quickly when you get worse
  private val releaseMs = 1500f // quieter slowly when you improve

  fun start() {
    if (running) return
    running = true

    val minBuf = AudioTrack.getMinBufferSize(
      sampleRate,
      AudioFormat.CHANNEL_OUT_STEREO,
      AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(frameSize * 2 * 2 * 4) // frames * channels * bytes * safety

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
      .setBufferSizeInBytes(minBuf)
      .build()

    track?.play()
    track?.setVolume(1.0f)

    thread = Thread { audioLoop() }.apply { isDaemon = true; start() }
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

  fun setMuted(m: Boolean) {
    muted = m
  }

  fun update(
    scoreS: Float,
    artefactA: Float,
    invertReward: Boolean,
    gamma: Float,
    gMinDb: Int,
    gMaxDb: Int,
    crackleEnabled: Boolean,
    crackleIntensity: Float
  ) {
    this.targetS = scoreS.coerceIn(0f, 1f)
    this.targetA = artefactA.coerceIn(0f, 1f)
    this.invertReward = invertReward
    this.gamma = gamma.coerceIn(0.6f, 3.0f)
    this.gMinDb = gMinDb
    this.gMaxDb = gMaxDb
    this.crackleEnabled = crackleEnabled
    this.crackleIntensity = crackleIntensity.coerceIn(0f, 1f)
  }

  private fun audioLoop() {
    val t = track ?: return
    val buffer = ShortArray(frameSize * 2) // interleaved stereo

    val dt = frameSize.toFloat() / sampleRate.toFloat()

    while (running) {
      // Base gain target from score S (0..1) → dB.
      val s = targetS
      val mapped = if (!invertReward) (1f - s).coerceIn(0f, 1f) else s
      val shaped = mapped.pow(gamma)
      val gDb = gMinDb + shaped * (gMaxDb - gMinDb)
      debugBaseDb = gDb

      val targetAmp = dbToAmp(gDb)

      // Attack/release smoothing on amplitude
      val tau = if (targetAmp > baseAmp) attackMs / 1000f else releaseMs / 1000f
      val a = 1f - exp(-dt / tau)
      baseAmp += a * (targetAmp - baseAmp)

      // Fade-in after calibration (2 s)
      if (fading) {
        fadeIn += dt / 2.0f
        if (fadeIn >= 1f) { fadeIn = 1f; fading = false }
      }

      val master = if (muted) 0f else (if (fading) fadeIn else 1f)

      val A = targetA

      // Crackle event rate: sparse when clean, busy when ugly.
      val rMin = 0.3f
      val rMax = 12.0f
      val beta = 1.8f
      val rate = rMin + (A.pow(beta)) * (rMax - rMin)

      val p = rate / sampleRate.toFloat()

      // Crackle gain.
      val gMax = 0.7f * crackleIntensity
      val eta = 1.2f
      val crackAmp = (A.pow(eta)) * gMax

      // Envelope decay: ~60 ms.
      val envDecay = exp(-dt / 0.06f)

      var idx = 0
      for (i in 0 until frameSize) {
        val nL = (Random.nextFloat() * 2f - 1f) * baseAmp * master
        val nR = (Random.nextFloat() * 2f - 1f) * baseAmp * master

        var cL = 0f
        var cR = 0f
        if (crackleEnabled && master > 0f) {
          if (Random.nextFloat() < p) crackleEnvL = 1f
          if (Random.nextFloat() < p) crackleEnvR = 1f

          cL = (Random.nextFloat() * 2f - 1f) * crackAmp * crackleEnvL * master
          cR = (Random.nextFloat() * 2f - 1f) * crackAmp * crackleEnvR * master

          crackleEnvL *= envDecay
          crackleEnvR *= envDecay
        }

        buffer[idx++] = floatToPcm16(nL + cL)
        buffer[idx++] = floatToPcm16(nR + cR)
      }

      t.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
    }
  }

  private fun dbToAmp(db: Float): Float {
    // amp = 10^(db/20)
    return 10.0.pow((db / 20.0).toDouble()).toFloat()
  }

  private fun floatToPcm16(x: Float): Short {
    val v = x.coerceIn(-1f, 1f)
    return (v * 32767f).toInt().toShort()
  }
}
