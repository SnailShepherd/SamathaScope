# SamathaScope (v0.1)

This is a **Gradle/Compose project** zip (not only an `app/` module).

## If you hit a Gradle sync error
Most common culprits:

1) **Wrong JDK**
   - Android Studio → Settings → Build Tools → Gradle → **Gradle JDK = 17**

2) **Missing gradle-wrapper.jar**
   - This zip does **not** include `gradle/wrapper/gradle-wrapper.jar`.
   - Easiest fix:
     - Create a new empty Android project in the same Android Studio
     - Copy its `gradle/wrapper/gradle-wrapper.jar` into this project
     - Sync again

3) **Caches got weird**
   - File → Invalidate Caches / Restart

## Pairing
Pair MindWave Mobile 2 in Android Bluetooth settings first.
v0.1 only lists **bonded** devices (no scanning).

## Recording output
`Android/data/com.mordin.samathascope/files/sessions/<timestamp>/`
- `raw.raw16le` (signed int16 LE)
- `features.csv`
- `meta.txt`


Toolchain: AGP 8.13.2 / Gradle 8.13 / Kotlin 2.3.10 / Compose plugin.

## SDK requirement
This project compiles with **compileSdk 36**. Install `Android SDK Platform 36` in SDK Manager if Android Studio prompts.
