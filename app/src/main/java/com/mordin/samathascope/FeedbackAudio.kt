package com.mordin.samathascope

import android.content.Context

/** The user's audio-feedback settings that shape the noise. */
internal data class FeedbackAudioSettings(
  val invertReward: Boolean,
  val noiseColor: NoiseColor,
  val gamma: Float,
  val gMinDb: Int,
  val gMaxDb: Int,
)

internal fun UiState.feedbackAudioSettings() = FeedbackAudioSettings(
  invertReward = invertReward,
  noiseColor = noiseColor,
  gamma = gamma,
  gMinDb = gMinDb,
  gMaxDb = gMaxDb,
)

/**
 * The session's feedback noise: the [NoiseAudioEngine] and the audio focus it holds, created and
 * released together. Not thread-safe; [MainViewModel] calls it with its lock held, so the engine can
 * never be created twice or outlive the session.
 */
internal class FeedbackAudio(context: Context, onFocusLost: () -> Unit) {
  private val focus = FeedbackAudioFocus(context, onFocusLost)
  private var engine: NoiseAudioEngine? = null

  val isActive: Boolean get() = engine != null

  /** The engine's current base level, for diagnostics; null when no engine runs. */
  val baseDb: Float? get() = engine?.debugBaseDb

  /** Starts the engine muted if it isn't running; optionally restarts its fade-in. */
  fun ensureStarted(settings: FeedbackAudioSettings, fadeIn: Boolean) {
    if (engine == null) {
      focus.request()
      engine = NoiseAudioEngine().apply {
        start()
        setMuted(true)
        apply(feedbackValue = 0f, settings = settings)
      }
    }
    if (fadeIn) engine?.beginFadeIn()
  }

  fun update(feedbackValue: Float, settings: FeedbackAudioSettings) {
    engine?.apply(feedbackValue, settings)
  }

  fun setMuted(muted: Boolean) {
    engine?.setMuted(muted)
  }

  /** Takes audio focus back after another app had it, e.g. when a paused session resumes. */
  fun reacquireFocus() {
    if (engine != null) focus.request()
  }

  /** Fades the noise out, releases the engine and gives up audio focus. */
  fun shutdown() {
    val running = engine ?: return
    focus.abandon()
    running.setMuted(true)
    running.stop()
    engine = null
  }

  private fun NoiseAudioEngine.apply(feedbackValue: Float, settings: FeedbackAudioSettings) {
    update(
      feedbackValue = feedbackValue,
      invertReward = settings.invertReward,
      noiseColor = settings.noiseColor,
      gamma = settings.gamma,
      gMinDb = settings.gMinDb,
      gMaxDb = settings.gMaxDb,
    )
  }
}
