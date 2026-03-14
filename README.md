# SamathaScope

SamathaScope is an Android neurofeedback app for MindWave Mobile 2. It uses a personalised frontal-state classifier instead of the retired alpha-heavy score and tries to reward relaxed alertness rather than quiet drowsiness.

Current app version: `0.7` (`versionCode 7`)

## What v0.7 includes

- 4-tab UI: `Dashboard`, `Settings`, `Game`, `Learn`
- Session card source dropdown for dashboard audio: `Meditation Proxy`, `Settledness`, or `Alertness`
- Scrollable raw and metric plots while the session is paused or stopped
- Metric explorer with unlimited simultaneous visible lines, stable per-metric colors, thicker traces, and a live legend
- Long-press on metric chips to focus the explainer instead of changing the source
- Optional artefact calibration after the clean baseline
- Decoupled game audio and dashboard audio with smoother tab crossfades
- Four neurofeedback scenes:
  - `Sky Tower` with composite painterly stones, a custom contained stack simulator, springy wobble tied to artefact score, frictional grip, and faster drops
  - `Ink Garden` with watercolor-style pigment spread and splatter
  - `Fire Keeper`
  - `Scriptorium`

## Quick use

1. Pair MindWave Mobile 2 in Android Bluetooth settings.
2. Open `Dashboard`, grant Bluetooth permissions, select a paired device, and connect.
3. Start a session and complete the 60-second clean calibration.
4. Optionally run the short artefact calibration to personalise blink and muscle thresholds.
5. In the `Session` card, choose the dashboard audio feedback source.
6. In the metric explorer:
   - tap chips to show or hide lines
   - hold a chip to focus its explanation
   - pause or stop the session, then drag plots to older history
7. Use `Game` for the scene picker and `Learn` for glossary and caveats.

## EEG pipeline summary

- Raw EEG: `512 Hz`
- Analysis window: last `8 s`
- Update step: `1 s`
- Main branch: detrend, optional `50 Hz` notch, `1-35 Hz` analysis
- Parallel HF branch: detrend plus optional notch, used for `20-40 Hz` and line-noise diagnostics
- PSD: Welch method with `2 s` segments and `50%` overlap

Extracted features:

- `P_theta`: `4-7 Hz`
- `P_alpha`: `8-12 Hz`
- `P_beta`: `13-30 Hz`
- `P_hf`: `20-40 Hz`
- `TBR`: theta/beta ratio
- `TAR`: theta/alpha ratio
- `ABR`: alpha/beta ratio
- spectral entropy over `4-30 Hz`
- blink/transient rate
- clipping and stall statistics

## Feedback model summary

Stage 1: quality gate

- Uses poor contact, blinks/transients, EMG-like HF contamination, clipping, and packet stalls.
- Contaminated windows become `SIGNAL_CONTAMINATED` and reward is faded down.

Stage 2: drowsiness-first state classifier

- `DROWSY` is estimated before meditation-style reward.
- Clean, awake windows are classified as `SETTLED`, `EFFORTFUL_FOCUS`, `MIND_WANDERING`, or `UNCERTAIN`.

Continuous dimensions:

- `Alertness = 1 - D`
- `Control = sigmoid(-z(TBR))`
- `Settledness = sigmoid(z(ABR) - 0.5*z(TAR))`
- `QualityConfidence = 1 - ArtefactScore`
- `MeditationProxy = Settledness * Alertness * QualityConfidence`

The games map these signals differently from the dashboard reward:

- `Settledness` -> stability/coherence
- `Mind Wandering` -> drift/disorganization
- `Artefact Score` -> glitches, splatter, tremor
- `Effortful Focus` -> short rescue pulse
- `Drowsiness` -> slow fatigue gate only

## Recording output

Enable recording in `Settings`.

Session files are written under:

`Android/data/com.mordin.samathascope/files/sessions/<timestamp>/`

- `raw.raw16le` - signed int16 little-endian raw samples
- `features.csv` - timestamps, feature values, z-scores, artefact terms, drowsiness evidence terms, classifier probabilities, selected dashboard source, selected game, and mapped game signals
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
