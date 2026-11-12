# SamathaScope

SamathaScope is an Android neurofeedback app for the NeuroSky MindWave Mobile 2 headset. It reads the raw single-channel frontal EEG over Bluetooth, builds a personal baseline, and classifies each second of signal with a frontal-state model that rewards relaxed alertness (settled, awake attention) and treats drowsiness separately. Feedback is delivered as calm background noise whose volume follows the metric you choose.

Current version: `0.80` (`versionCode 9`)

## Features

### Dashboard
- **Headset card**
  - Lists your paired Bluetooth devices.
  - Remembers the headset you last selected or connected to, across app restarts.
  - Shows the Bluetooth link and raw-EEG stream state: waiting, confirming, live or stalled.
  - Shows battery level, poor-signal value, samples per second and stream stall time.
- **Raw EEG card:** a live scrolling plot of the raw signal.
- **Session card**
  - Start, pause, resume and stop a session.
  - Shows elapsed time, the displayed state, the average Meditation Proxy, and the share of time with Meditation Proxy at or above 80%.
  - Lets you choose which metric drives the audio feedback: `Meditation Proxy`, `Settledness` or `Alertness`.
  - The Start button unlocks once a live raw-EEG stream has been confirmed.
- **Metric explorer**
  - Overlays any number of metric traces, each with a stable colour and a live legend.
  - Tap a chip to show or hide its trace; hold a chip to open that metric's explanation.
  - While a session is paused or stopped, drag the raw and metric plots back through older history.
- **Diagnostics card**
  - Current state label and signal reliability (high, moderate or low confidence).
  - Settledness, Control, Alertness, Drowsy score and Quality confidence.
  - The individual drowsiness evidence terms.
  - Artefact breakdown: contact quality, EMG proxy, blinks and transients, clipping, packet stalls and 50 Hz line noise.
  - The headset's own eSense Attention and Meditation values, for reference.

### Calibration
- **Clean baseline:** 60 seconds, 30 with eyes open, then 30 with eyes closed. A bell sounds when it finishes.
  - The baseline uses robust statistics (median and MAD) of the per-window features.
  - It keeps adapting during the session from a rolling 10-minute window of clean signal.
- **Skipping:** you can skip calibration at any time.
  - If at least 20 analysis windows have been collected, the baseline is built from them.
  - Otherwise scores stay neutral and the dashboard says so. A baseline builds automatically once about 20 seconds of clean signal arrive.
- **Optional artefact calibration:** a short guided capture of eye movements, jaw clench and frown. It personalises the blink and muscle (EMG) thresholds and doesn't touch the clean baseline.

### Audio feedback
- Continuous noise in white, pink, brown or blue.
- By default, better feedback makes it quieter. "Invert reward" makes better feedback louder instead.
- Adjustable sensitivity curve (gamma) and minimum/maximum noise level in dB.
- A test beep checks the output.

### Settings
- **Audio:** the feedback options above.
- **Plots:** shared metric history window of 1, 3, 5 or 10 minutes.
- **Data and filters**
  - Local session recording.
  - Optional 50 Hz notch filter.
- **Debug raw replay**
  - Capture 60 seconds of live raw EEG once.
  - Loop it as the signal source to test the whole app without wearing the headset.
  - A sample loop ships with the app, so replay works on a fresh install.

### Learn
- **How metrics work:** a deep dive into each metric, with explainers, interpretation notes and the assumptions behind single-channel frontal EEG.
- **Foundations:** samatha and relaxed alertness, the frontal-state feedback proxy, why "settled" is not "drowsy", how calibration works, interpretation limits and a wellbeing note.

## Quick start

1. Pair the MindWave Mobile 2 in Android's Bluetooth settings.
2. Open SamathaScope. On the first launch, tap **Grant permissions**.
3. On the **Dashboard**, pick the headset (it stays selected next time) and tap **Connect**.
4. Wait for the stream state to show **Live raw EEG**, then tap **Start session**.
5. Follow the calibration prompts: eyes open, then eyes closed.
6. Optionally run the artefact calibration when it's offered.
7. In the **Session** card, choose the feedback metric. Then meditate and let the audio guide you.
8. Pause or stop to review the plots. Turn on recording in **Settings** to keep the data.

## EEG pipeline

- **Raw input:** 512 Hz, signed 16-bit samples, parsed from the ThinkGear packet protocol.
- **Analysis window:** the last 8 s, updated every 1 s.
- **Main branch:** detrend, optional 50 Hz notch, then analysis over 1–35 Hz.
- **High-frequency branch:** detrend plus optional notch, for 20–40 Hz EMG and line-noise diagnostics.
- **Power spectrum:** Welch method with 2 s segments and 50% overlap.

Features computed for each window:

| Feature | Definition |
|---|---|
| `P_theta` | power 4–7 Hz |
| `P_alpha` | power 8–12 Hz |
| `P_beta` | power 13–30 Hz |
| `P_hf` | power 20–40 Hz |
| `TBR` | theta / beta |
| `TAR` | theta / alpha |
| `ABR` | alpha / beta |
| entropy | spectral entropy over 4–30 Hz |
| quality | blink/transient rate, clipping, packet stalls, poor-signal value |

## Feedback model

1. **Quality gate:**
   - Inputs: poor contact, blinks and transients, EMG-like high-frequency power, clipping, and packet stalls.
   - Contaminated windows are labelled `SIGNAL_CONTAMINATED`, and reward is faded down.
2. **Drowsiness first:** `DROWSY` is estimated before any meditation-style reward is given.
3. **State classification:** clean, awake windows are labelled `SETTLED`, `EFFORTFUL_FOCUS`, `MIND_WANDERING` or `UNCERTAIN`.

Continuous dimensions (each `z(...)` is a z-score against your baseline):

- `Alertness = 1 - DrowsyScore`
- `Control = sigmoid(-z(TBR))`
- `Settledness = sigmoid(z(ABR) - 0.5 * z(TAR))`
- `QualityConfidence = 1 - ArtefactScore`
- `MeditationProxy = Settledness * Alertness * QualityConfidence`

The methodology in full is in [docs/samatha_score_methodology.md](docs/samatha_score_methodology.md).

## Recording

When recording is on, each session is written to:

`Android/data/com.mordin.samathascope/files/sessions/<timestamp>/`

- `raw.raw16le`: raw samples as signed int16, little-endian
- `features.csv`: one row per analysis window, with:
  - timestamps
  - band powers and ratios, and their z-scores
  - artefact terms and drowsiness evidence terms
  - classifier probabilities and state
  - the selected feedback source and its value
- `meta.txt`: session metadata

## Building

| Component | Version |
|---|---|
| Android Gradle Plugin | 9.5.1, with built-in Kotlin |
| Gradle | 9.9.1 (wrapper included) |
| Kotlin | 2.4.21 |
| Compose BOM | 2026.09.00 |
| compileSdk / targetSdk / minSdk | 37 / 36 / 26 |
| Java target | 17 |

You need JDK 17 or newer. Android Studio's bundled JBR works.

Open the project in Android Studio, or build from the command line:

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

```bash
./gradlew :app:connectedDebugAndroidTest
```

The last command runs the instrumented UI tests on a connected device. When it finishes it uninstalls the app, which deletes the app's recordings.

## Project layout

- `app/src/main/java/com/mordin/samathascope/`
  - `BluetoothMindWaveClient.kt`, `ThinkGearParser.kt`: RFCOMM link and packet parsing
  - `EegProcessor.kt`: filtering, Welch PSD and feature extraction
  - `CalibrationManager.kt`, `ScoreModel.kt`: baseline and frontal-state classifier
  - `MainViewModel.kt`: session lifecycle, signal routing and state
  - `NoiseAudioEngine.kt`, `BellSoundPlayer.kt`: audio feedback
  - `SessionRecorder.kt`, `DebugRawLoopStore.kt`: recording and debug replay
  - `App.kt`: Compose UI
- `docs/`: methodology, Theravada context, and design notes
