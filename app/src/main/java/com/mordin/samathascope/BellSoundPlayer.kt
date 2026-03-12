package com.mordin.samathascope

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object BellSoundPlayer {
  fun playCalibrationComplete() {
    Thread {
      try {
        val sampleRate = 24_000
        val durationSeconds = 0.9
        val sampleCount = (sampleRate * durationSeconds).toInt()
        val pcm = ShortArray(sampleCount)

        for (i in 0 until sampleCount) {
          val time = i / sampleRate.toDouble()
          val envelope = exp(-4.2 * time).toFloat()
          val tone = (
            sin(2.0 * PI * 660.0 * time) +
              (0.45 * sin(2.0 * PI * 990.0 * time)) +
              (0.20 * sin(2.0 * PI * 1320.0 * time))
            ).toFloat()
          pcm[i] = (tone * envelope * 0.18f * 32767f).toInt().toShort()
        }

        val track = AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .build()
          )
          .setAudioFormat(
            AudioFormat.Builder()
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setSampleRate(sampleRate)
              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
              .build()
          )
          .setBufferSizeInBytes(pcm.size * 2)
          .setTransferMode(AudioTrack.MODE_STATIC)
          .build()

        track.write(pcm, 0, pcm.size)
        track.play()
        Thread.sleep((durationSeconds * 1000).toLong() + 150L)
        track.stop()
        track.release()
      } catch (_: Throwable) {
      }
    }.start()
  }
}
