plugins {
  // AGP 9 compiles Kotlin itself (built-in Kotlin), so org.jetbrains.kotlin.android is no longer applied.
  // The Compose compiler plugin version also pins the Kotlin Gradle plugin/compiler version.
  id("com.android.application") version "9.5.1" apply false
  id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
