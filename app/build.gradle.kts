plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  // Kotlin 2.0+ uses the Compose compiler Gradle plugin.
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "com.mordin.samathascope"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.mordin.samathascope"
    minSdk = 26
    targetSdk = 35
    versionCode = 1
    versionName = "0.1"
  }

  buildFeatures { compose = true }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }


kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
}

}

dependencies {
  // Compose BOM keeps all Compose artifacts in sync.
  val composeBom = platform("androidx.compose:compose-bom:2026.02.01")
  implementation(composeBom)
  androidTestImplementation(composeBom)

  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-tooling-preview")
  debugImplementation("androidx.compose.ui:ui-tooling")

  implementation("androidx.activity:activity-compose:1.12.4")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
