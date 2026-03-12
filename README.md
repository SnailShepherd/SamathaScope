# SamathaScope

SamathaScope is an Android biofeedback app for MindWave Mobile 2, focused on Samatha-style training support with a personalised frontal-state classifier rather than a single alpha-heavy score.

Current app version: `0.4` (`versionCode 4`)

## What v0.4 includes

- 4-tab UI: `Dashboard`, `Signals`, `Game`, `Learn`.
- Replaced the old RAI/Samatha-score pipeline with an 8-second / 1-second-step frontal-state classifier.
- Uses raw EEG at 512 Hz, Welch PSD, robust per-feature calibration, adaptive clean-window baseline updates, and explicit quality gating.
- Distinguishes `SETTLED`, `EFFORTFUL_FOCUS`, `MIND_WANDERING`, `DROWSY`, `SIGNAL_CONTAMINATED`, and `UNCERTAIN`.
- Default feedback metric is `MeditationProxy = Settledness * Alertness * QualityConfidence`.
- Audio and game feedback now fade down during drowsy or contaminated periods instead of rewarding quiet but sleepy data.
- Metric plots and recording were updated for settledness, control, alertness, drowsy score, artefact score, quality confidence, and classifier probabilities.
- Optional 50 Hz notch toggle for cases where mains noise is not already handled upstream.

## Quick use

1. Pair MindWave Mobile 2 in Android Bluetooth settings.
2. Open `Dashboard`, grant Bluetooth permissions, select a bonded device, and connect.
3. Start a session and let the 60-second calibration finish while staying still, relaxed, and awake.
4. Use `Signals` to choose the feedback metric, audio settings, and plot settings.
5. Use `Game` for visual feedback and `Learn` for context and caveats.

Note: the app uses bonded-device selection and preserves the existing Bluetooth/ThinkGear connection flow.

## Analysis pipeline summary

- Windowing: last `8 s` of raw EEG, updated every `1 s`.
- Main branch: detrend, optional 50 Hz notch, then 1-35 Hz analysis band.
- Parallel HF branch: detrend plus optional notch, used for `20-40 Hz` high-frequency/EMG and line-noise diagnostics.
- PSD: Welch method with `2 s` segments and `50%` overlap.
- Bands: `theta 4-7`, `alpha 8-12`, `beta 13-30`, `hf 20-40`.
- Extra features: log powers, relative powers, `TBR`, `TAR`, `ABR`, theta/alpha peak frequency, spectral entropy, EMG proxy, blink/transient rate, clipping, line noise, and stall statistics.
- Calibration: robust median/MAD baselines per user for the classifier input features, with adaptive updates from clean windows only.

## Feedback model summary

Stage 1 quality gate:
- contact, EMG proxy, blink/transient, clipping, and packet-stall contamination are combined into an artefact score.
- Poor windows are labeled `SIGNAL_CONTAMINATED` and reward is suppressed.

Stage 2 classifier on clean windows:
- Drowsiness is estimated before meditation-style feedback so the app does not reward sleepy slowing.
- Clean, alert windows are classified as `SETTLED`, `EFFORTFUL_FOCUS`, `MIND_WANDERING`, or `UNCERTAIN`.
- The continuous UI metrics are `Settledness`, `Control`, `Alertness`, `DrowsyScore`, and `QualityConfidence`.

## Plot defaults (v0.4)

- Raw EEG default window: `5s`.
- Metric plots default window: `300s`.
- Raw EEG default Y-range starts conservative, then updates after calibration from raw signal statistics unless user-lock overrides it.
- Metric plots default range: `0..100`.
- Metric history is recorded at `1 point/sec` to match the classifier update cadence.

## Recording output

Enable recording in `Dashboard -> Settings`.

Session files are written under:

`Android/data/com.mordin.samathascope/files/sessions/<timestamp>/`

- `raw.raw16le` - signed int16 little-endian raw samples
- `features.csv` - timestamps, raw features, z-scored classifier inputs, artefact components, probabilities, displayed state, and final feedback values
- `meta.txt` - basic session metadata

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

## Repo hygiene

This repo ignores common generated files:

- `.idea/`
- `.gradle/`
- `.kotlin/`
- `build/` and `*/build/`
- `local.properties`
- `captures/`
- `.externalNativeBuild/`
- `.cxx/`
