# SamathaScope

SamathaScope is an Android biofeedback app for MindWave Mobile 2, focused on Samatha-style training support.

Current app version: `0.2` (`versionCode 2`)

## What v0.2 includes

- 4-tab UI: `Dashboard`, `Signals`, `Game`, `Learn`.
- Oldschool/utilitarian visual style with full labels (no shortened metric names in controls).
- Live plot rework to stroke-only traces (no filled area artifacts).
- Per-plot settings: window length + Y min/max + reset.
- Plot settings persistence across launches.
- Levitation feedback game with fixed scene landmarks and telemetry HUD.
- Learn tab with Theravada context, score caveats, and optional source links.
- Added docs in `docs/samatha_theravada_context.md` and `docs/samatha_score_methodology.md`.

## Quick use

1. Pair MindWave Mobile 2 in Android Bluetooth settings.
2. Open `Dashboard`, grant Bluetooth permissions, select bonded device, connect.
3. Start session and let calibration finish.
4. Use `Signals` for feedback source + plot controls.
5. Use `Game` for visual feedback and `Learn` for context.

Note: the app uses bonded-device selection (no in-app Bluetooth scan flow yet).

## Tabs and behavior

- `Dashboard`: headset status, session controls, diagnostics, and raw EEG plot.
- Gear button opens settings panel (recording/app-level controls).
- `Signals`: feedback metric selection, audio feedback controls, live plot selection, per-plot settings editor.
- `Game`: endless levitation scene with horizon/grid reference and full HUD.
- `Learn`: offline summary cards, optional external references, explicit interpretation caveat.

## Plot defaults (v0.2)

- Raw EEG default window: `5s`.
- Metric plots default window: `300s` (5 minutes).
- Raw EEG default Y-range starts conservative, then updates after calibration from raw signal statistics unless user-lock overrides it.
- Metric plots default range: `0..100`.

## Recording output

Enable recording in `Dashboard` -> `Settings`.

Session files are written under:

`Android/data/com.mordin.samathascope/files/sessions/<timestamp>/`

- `raw.raw16le` (signed int16 little-endian samples)
- `features.csv`
- `meta.txt`

## Build and toolchain

- `compileSdk 36`
- `targetSdk 35`
- `minSdk 26`
- AGP `8.13.2`
- Gradle `8.13`
- Kotlin `2.3.10`
- Compose BOM `2026.02.01`
- Java/Kotlin target `17`

If Android Studio reports missing `gradle/wrapper/gradle-wrapper.jar`, copy it from a fresh empty Android Studio project and sync again.

## Repo hygiene (Android Studio noise)

This repo ignores common generated files:

- `.idea/`
- `.gradle/`
- `.kotlin/`
- `build/` and `*/build/`
- `local.properties`
- `captures/`
- `.externalNativeBuild/`
- `.cxx/`

Useful cleanup commands:

- Check working tree: `git status --short`
- Remove ignored generated files only: `git clean -fdX`

Recommended workflow: keep a clean source clone for commits and a separate Android Studio clone for running/building.
