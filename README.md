# SamathaScope

SamathaScope is an Android biofeedback app for MindWave Mobile 2. It uses a personalised frontal-state classifier instead of the older alpha-heavy RAI/Samatha score, and it tries to reward relaxed alertness rather than quiet drowsiness.

Current app version: `0.5` (`versionCode 5`)

## What v0.5 includes

- 4-tab UI: `Dashboard`, `Settings`, `Game`, `Learn`
- Compact dashboard with headset controls, session controls, an always-on raw EEG strip, a multi-line normalized metric explorer, and diagnostics
- Shared feedback source for both audio and game
- 60-second clean calibration split into `0-30s` eyes open and `30-60s` face relaxed, eyes closed
- Optional 25-second artefact calibration after the clean baseline for eye and face movement examples
- Calibration-complete bell cue
- Softer audio fade-out on stop, disconnect, or audio-off
- Updated recording with classifier features, z-scores, artefact terms, drowsiness evidence terms, displayed state, and final shared feedback value

## Quick use

1. Pair MindWave Mobile 2 in Android Bluetooth settings.
2. Open `Dashboard`, grant Bluetooth permissions, select a paired device, and connect.
3. Start a session and complete the 60-second clean calibration.
4. Optionally run the short artefact calibration to personalise blink and muscle thresholds.
5. On the dashboard metric explorer:
   - tap a metric chip to show or hide its line
   - hold an eligible chip to make it the shared feedback source
6. Use `Settings` for audio, recording, notch, and plot-window controls.
7. Use `Game` for shared visual feedback and `Learn` for the glossary and caveats.

## EEG pipeline summary

- Raw EEG: `512 Hz`
- Analysis window: last `8 s`
- Update step: `1 s`
- Main branch: detrend, optional `50 Hz` notch, `1-35 Hz` band-pass
- Parallel HF branch: detrend plus optional notch, used for `20-40 Hz` and line-noise diagnostics
- PSD: Welch method with `2 s` segments and `50%` overlap

Extracted band powers:

- `P_theta`: `4-7 Hz`
- `P_alpha`: `8-12 Hz`
- `P_beta`: `13-30 Hz`
- `P_hf`: `20-40 Hz`

Derived features:

- `TBR`: theta/beta ratio
- `TAR`: theta/alpha ratio
- `ABR`: alpha/beta ratio
- `EMG`: high-frequency muscle proxy
- spectral entropy over `4-30 Hz`
- theta and alpha peak frequency
- blink/transient rate
- clipping and stall statistics

## Feedback model summary

Stage 1: quality gate

- Uses poor contact, blinks/transients, EMG-like HF contamination, clipping, and packet stalls.
- Contaminated windows become `SIGNAL_CONTAMINATED` and reward is faded down.

Stage 2: drowsiness-first state classifier

- `DROWSY` is estimated before any meditation-style feedback.
- Clean, alert windows are classified as `SETTLED`, `EFFORTFUL_FOCUS`, `MIND_WANDERING`, or `UNCERTAIN`.

Continuous dimensions:

- `Alertness = 1 - D`
- `Control = sigmoid(-z(TBR))`
- `Settledness = sigmoid(z(ABR) - 0.5*z(TAR))`
- `QualityConfidence = 1 - ArtefactScore`
- `MeditationProxy = Settledness * Alertness * QualityConfidence`

The displayed drowsy signal is intentionally slower and stricter than the raw drowsiness evidence, so calm eyes-closed settling is less likely to look falsely sleepy.

## Dashboard metrics glossary

- `MP`: Meditation Proxy, the main reward proxy
- `S`: Settledness, calm-but-organized frontal settling
- `C`: Control, lower theta/beta drift relative to baseline
- `A`: Alertness, inverse of drowsiness evidence
- `D`: Drowsiness, frontal slowing evidence, not a sleep-stage detector
- `QC`: Quality Confidence, how usable the current window is
- `TBR`: theta/beta ratio
- `TAR`: theta/alpha ratio
- `ABR`: alpha/beta ratio
- `EMG`: high-frequency muscle contamination proxy

## Recording output

Enable recording in `Settings`.

Session files are written under:

`Android/data/com.mordin.samathascope/files/sessions/<timestamp>/`

- `raw.raw16le` - signed int16 little-endian raw samples
- `features.csv` - timestamps, raw feature values, z-scores, artefact terms, drowsiness terms, probabilities, displayed state, and final shared feedback values
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

If Android Studio reports a missing `gradle/wrapper/gradle-wrapper.jar`, copy it from a fresh empty Android Studio project and sync again.
