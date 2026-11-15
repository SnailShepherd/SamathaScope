package com.mordin.samathascope

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log

/**
 * Holds audio focus while the feedback noise plays, and reports when another app takes it (a call,
 * a video) so the session can pause. Short "can duck" interruptions such as notifications are ducked
 * by the system and not reported.
 */
class FeedbackAudioFocus(
  context: Context,
  private val onFocusLost: () -> Unit,
) {
  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
    .setAudioAttributes(
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    )
    .setWillPauseWhenDucked(false)
    .setOnAudioFocusChangeListener { change ->
      when (change) {
        AudioManager.AUDIOFOCUS_LOSS,
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
          held = false
          onFocusLost()
        }
        AudioManager.AUDIOFOCUS_GAIN -> held = true
      }
    }
    .build()

  @Volatile private var held = false

  fun request() {
    if (held) return
    val result = audioManager.requestAudioFocus(request)
    held = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    if (!held) Log.w(TAG, "Audio focus not granted ($result)")
  }

  fun abandon() {
    audioManager.abandonAudioFocusRequest(request)
    held = false
  }

  private companion object {
    const val TAG = "FeedbackAudioFocus"
  }
}
