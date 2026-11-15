plugins {
  // AGP 9 compiles Kotlin itself (built-in Kotlin), so org.jetbrains.kotlin.android is no longer applied.
  // Versions live in gradle/libs.versions.toml.
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
}
