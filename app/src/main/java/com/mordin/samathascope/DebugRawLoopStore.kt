package com.mordin.samathascope

import android.content.Context
import java.io.File

internal const val DEBUG_RAW_LOOP_DURATION_SECONDS = 60
internal const val DEBUG_RAW_LOOP_SAMPLE_RATE_HZ = 512
internal const val DEBUG_RAW_LOOP_SAMPLE_COUNT =
  DEBUG_RAW_LOOP_DURATION_SECONDS * DEBUG_RAW_LOOP_SAMPLE_RATE_HZ

internal data class DebugRawLoopMetadata(
  val poorSignal: Int = 0,
  val attention: Int = 50,
  val meditation: Int = 50,
)

internal data class DebugRawLoopRecord(
  val samples: IntArray,
  val metadata: DebugRawLoopMetadata = DebugRawLoopMetadata(),
)

internal class DebugRawLoopStore(private val context: Context) {

  private val rootDir = File(context.filesDir, "debug")
  private val exportDir = context.getExternalFilesDir(null)?.let { File(it, "debug") }

  fun load(sampleCount: Int = DEBUG_RAW_LOOP_SAMPLE_COUNT): DebugRawLoopRecord? {
    return loadFromDirectory(rootDir, sampleCount)
      ?: exportDir?.let { loadFromDirectory(it, sampleCount) }
      ?: loadFromAssets(sampleCount)
  }

  fun save(record: DebugRawLoopRecord) {
    saveToDirectory(rootDir, record)
    exportDir?.let { saveToDirectory(it, record) }
  }

  fun clear() {
    clearDirectory(rootDir)
    exportDir?.let { clearDirectory(it) }
  }

  private fun saveToDirectory(directory: File, record: DebugRawLoopRecord) {
    val rawFile = File(directory, RAW_FILE_NAME)
    val metadataFile = File(directory, METADATA_FILE_NAME)
    rawFile.parentFile?.mkdirs()
    rawFile.writeBytes(encodeDebugRawLoop(record.samples))
    metadataFile.writeText(encodeDebugRawLoopMetadata(record.metadata))
  }

  private fun clearDirectory(directory: File) {
    val rawFile = File(directory, RAW_FILE_NAME)
    val metadataFile = File(directory, METADATA_FILE_NAME)
    if (rawFile.exists()) rawFile.delete()
    if (metadataFile.exists()) metadataFile.delete()
  }

  private fun loadFromDirectory(directory: File, sampleCount: Int): DebugRawLoopRecord? {
    val rawFile = File(directory, RAW_FILE_NAME)
    val metadataFile = File(directory, METADATA_FILE_NAME)
    if (!rawFile.exists()) return null
    val samples = decodeDebugRawLoop(rawFile.readBytes(), sampleCount) ?: return null
    val metadata = if (metadataFile.exists()) {
      decodeDebugRawLoopMetadata(metadataFile.readText())
    } else {
      DebugRawLoopMetadata()
    }
    return DebugRawLoopRecord(samples = samples, metadata = metadata)
  }

  private fun loadFromAssets(sampleCount: Int): DebugRawLoopRecord? {
    return try {
      val samples = context.assets.open(assetPath(RAW_FILE_NAME)).use { input ->
        decodeDebugRawLoop(input.readBytes(), sampleCount)
      } ?: return null
      val metadata = try {
        context.assets.open(assetPath(METADATA_FILE_NAME)).use { input ->
          decodeDebugRawLoopMetadata(input.bufferedReader().readText())
        }
      } catch (_: Throwable) {
        DebugRawLoopMetadata()
      }
      DebugRawLoopRecord(samples = samples, metadata = metadata)
    } catch (_: Throwable) {
      null
    }
  }

  private fun assetPath(fileName: String): String = "debug/$fileName"

  private companion object {
    const val RAW_FILE_NAME = "debug_raw_loop.raw16le"
    const val METADATA_FILE_NAME = "debug_raw_loop.meta"
  }
}

internal fun createDebugRawLoopStore(context: Context): DebugRawLoopStore {
  return DebugRawLoopStore(context)
}

internal fun encodeDebugRawLoop(samples: IntArray): ByteArray {
  val bytes = ByteArray(samples.size * 2)
  var byteIndex = 0
  for (sample in samples) {
    val clamped = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    bytes[byteIndex++] = (clamped.toInt() and 0xFF).toByte()
    bytes[byteIndex++] = ((clamped.toInt() ushr 8) and 0xFF).toByte()
  }
  return bytes
}

internal fun decodeDebugRawLoop(bytes: ByteArray, sampleCount: Int): IntArray? {
  if (bytes.size != sampleCount * 2) return null
  val samples = IntArray(sampleCount)
  var byteIndex = 0
  for (sampleIndex in 0 until sampleCount) {
    val low = bytes[byteIndex++].toInt() and 0xFF
    val high = bytes[byteIndex++].toInt() shl 8
    samples[sampleIndex] = (high or low).toShort().toInt()
  }
  return samples
}

internal fun encodeDebugRawLoopMetadata(metadata: DebugRawLoopMetadata): String {
  return buildString {
    appendLine("poor_signal=${metadata.poorSignal.coerceIn(0, 255)}")
    appendLine("attention=${metadata.attention.coerceIn(0, 100)}")
    appendLine("meditation=${metadata.meditation.coerceIn(0, 100)}")
  }
}

internal fun decodeDebugRawLoopMetadata(text: String): DebugRawLoopMetadata {
  val values = mutableMapOf<String, String>()
  text.lineSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() && it.contains('=') }
    .forEach { line ->
      val (key, value) = line.split('=', limit = 2)
      values[key.trim()] = value.trim()
    }
  return DebugRawLoopMetadata(
    poorSignal = values["poor_signal"]?.toIntOrNull()?.coerceIn(0, 255) ?: 0,
    attention = values["attention"]?.toIntOrNull()?.coerceIn(0, 100) ?: 50,
    meditation = values["meditation"]?.toIntOrNull()?.coerceIn(0, 100) ?: 50,
  )
}
