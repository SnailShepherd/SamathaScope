package com.mordin.samathascope

enum class NoiseColor(val storageValue: String) {
  WHITE("white"),
  PINK("pink"),
  BROWN("brown"),
  BLUE("blue");

  companion object {
    fun fromStorage(value: String?): NoiseColor {
      return entries.firstOrNull { it.storageValue == value } ?: WHITE
    }
  }
}
