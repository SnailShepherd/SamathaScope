# SamathaScope

SamathaScope is an Android neurofeedback app for the NeuroSky MindWave Mobile 2 headset. It reads the raw single-channel frontal EEG over Bluetooth, builds a personal baseline, and classifies each second of signal with a frontal-state model that rewards relaxed alertness (settled, awake attention) and treats drowsiness separately. Feedback is delivered as calm background noise whose volume follows the metric you choose.

Current version: `0.81` (`versionCode 10`)

## Features

### Dashboard
- **Headset card**
  - Lists your paired Bluetooth devices.
  - Remembers the headset you last selected or connected to, across app restarts.
  - Shows the Bluetooth link and raw-EEG stream state: waiting, confirming, live, stalled, or an unexpected sample rate.
  - Shows battery level, poor-signal value, samples per second and stream stall time.
  - Explains connection problems: Bluetooth off, connection failed, or connection lost.
- **Raw EEG card:** a live scrolling plot of the raw signal.
- **Session card**
  - Start, pause, resume and stop a session.
  - Shows elapsed time, the displayed state, the average Meditation Proxy, and the share of time with Meditation Proxy at or above 80%.
  - Lets you choose which metric drives the audio feedback: `Meditation Proxy`, `Settledness` or `Alertness`.
  - Choose whether your eyes stay closed or open during the session; it picks the matching calibration baseline.
  - The Start button unlocks once a live raw-EEG stream has been confirmed: about 2 s of samples measured at 512 per second (±10%).
  - A running session keeps going with the screen off; an ongoing notification shows while it runs.
- **Metric explorer**
  - Overlays any number of metric traces, each with a stable colour and a live legend.
  - Tap a chip to show or hide its trace; hold a chip to open that metric's explanation.
  - While a session is paused or stopped, drag the raw and metric plots back through older history.
- **Diagnostics card**
  - Current state label and signal reliability (high, moderate or low confidence).
  - Settledness, Control, Alertness, Drowsy score and Quality confidence.
  - The individual drowsiness evidence terms.
  - Artefact breakdown: contact quality, EMG proxy, blinks and transients, clipping, signal gaps (lost or missing samples) and 50/60 Hz line noise.
  - The headset's own eSense Attention and Meditation values, for reference.

### Calibration
- **Clean baseline:** 60 seconds, 30 with eyes open, then 30 with eyes closed. A bell sounds when it finishes.
  - Each half gives its own baseline, from robust statistics (median and MAD) of the windows that lie fully inside it. The session is scored against the half matching its eyes setting.
  - The timer stops while the session is paused.
  - During the session the baseline adapts slowly: the calibration baseline is blended with a rolling 30-minute window of clean signal, whose weight grows to 50% after 10 minutes and at most 75%. Slow drift is followed without erasing a sustained good state.
- **Skipping:** you can skip calibration at any time.
  - If the half matching the session already has at least 20 analysis windows, the baseline is built from them.
  - Otherwise scores stay neutral and the dashboard says so. A baseline builds automatically once about 20 seconds of clean signal arrive.
  - The same applies if the timer runs out with too little data in that half (for example after a stream stall): calibration still ends on time.
- **Optional artefact calibration:** a short guided capture of eye movements, jaw clench and frown. It personalises the blink and muscle (EMG) thresholds and doesn't touch the clean baseline.

### Audio feedback
- Continuous noise in white, pink, brown or blue.
- By default, better feedback makes it quieter. "Invert reward" makes better feedback louder instead.
- Adjustable sensitivity curve (gamma) and minimum/maximum noise level in dB.
- A test beep checks the output.
- If another app takes the audio (a call, a video), the session pauses; tap Resume when ready. Notification sounds only duck it briefly.
- Stopping a session fades the noise out instead of cutting it.

### Settings

All settings are saved and restored on the next launch.

- **Audio:** the feedback options above.
- **Plots:** shared metric history window of 1, 3, 5 or 10 minutes.
- **Data and filters**
  - Local session recording, starting with the next session.
  - **Share last session:** zips the most recent recording and opens Android's share sheet.
  - Optional mains notch filter; 50 or 60 Hz is detected from the signal.
- **Developer options:** off by default. Turning them on shows debug raw replay:
  - Capture 60 seconds of live raw EEG once.
  - Loop it as the signal source to test the whole app without wearing the headset.
  - A sample loop ships with the app, so replay works on a fresh install.
- **Appearance:** System, Light or Dark theme.

### Learn
- **How metrics work:** a deep dive into each metric, with explainers, interpretation notes and the assumptions behind single-channel frontal EEG.
- **Foundations:** samatha and relaxed alertness, the frontal-state feedback proxy, why "settled" is not "drowsy", how calibration works, and interpretation limits.

## Quick start

1. Pair the MindWave Mobile 2 in Android's Bluetooth settings.
2. Open SamathaScope. On the first launch, tap **Grant permissions** (Bluetooth).
3. On the **Dashboard**, pick the headset (it stays selected next time) and tap **Connect**.
4. Wait for the stream state to show **Live raw EEG**, choose **Eyes closed** or **Eyes open**, then tap **Start session**. On Android 13+ the first start asks to show notifications, for the "Session running" notification; the session starts either way.
5. Follow the calibration prompts: eyes open, then eyes closed.
6. Optionally run the artefact calibration when it's offered.
7. In the **Session** card, choose the feedback metric. Then meditate and let the audio guide you.
8. Pause or stop to review the plots. Turn on recording in **Settings** to keep the data.

## EEG pipeline

- **Raw input:** 512 Hz, signed 16-bit samples, parsed from the ThinkGear packet protocol.
- **Analysis window:** the last 5 s, updated every 1 s.
- **Mains:** 50 vs 60 Hz is detected from the raw spectrum (with hysteresis).
- **Optional mains notch:** a streaming IIR notch (Q = 10) at the detected frequency, applied to the samples before blink detection and both spectral branches. Line-noise and clipping checks always use the unfiltered signal.
- **Main branch:** detrend, then analysis over 1–35 Hz.
- **High-frequency branch:** detrend, for 20–40 Hz EMG diagnostics. Line noise is measured on the unfiltered spectrum.
- **Power spectrum:** Welch method with 2 s segments and 50% overlap (4 segments per window, 0.5 Hz resolution).
- **Signal gaps:** samples the packet parser reports lost (corrupt or dropped bytes) are replaced by a held value, so the window keeps real time, and counted as missing signal. A window whose arrival times span more than 300 ms beyond what its samples explain also counts the excess as a gap. Bursty but complete Bluetooth delivery is not a gap.

Features computed for each window:

| Feature | Definition |
|---|---|
| `P_theta` | power 4–8 Hz |
| `P_alpha` | power 8–13 Hz |
| `P_beta` | power 13–30 Hz |
| `P_hf` | power 20–40 Hz |
| `TBR` | theta / beta |
| `TAR` | theta / alpha |
| `ABR` | alpha / beta |
| entropy | spectral entropy over 4–30 Hz |
| quality | blink/transient rate, clipping, signal gaps, line noise, poor-signal value |

Bands are half-open (each includes its lower edge, not its upper one), so they are contiguous: theta + alpha + beta = 4–30 Hz.

## Feedback model

1. **Quality gate:**
   - Inputs: poor contact, blinks and transients, EMG-like high-frequency power, clipping, and signal gaps.
   - Contaminated windows are labelled `SIGNAL_CONTAMINATED`, and reward is faded down.
2. **Drowsiness first:** `DROWSY` is estimated before any meditation-style reward is given.
3. **State classification:** clean, awake windows are labelled `SETTLED`, `EFFORTFUL_FOCUS`, `MIND_WANDERING` or `UNCERTAIN`.

Continuous dimensions (each `z(...)` is a z-score against your baseline):

- `Alertness = 1 - DrowsyScore`
- `Control = sigmoid(-z(TBR))`
- `Settledness = sigmoid(z(ABR) - 0.5 * z(TAR))`
- `QualityConfidence = 1 - ArtefactScore`
- `MeditationProxy = sqrt(Settledness * Alertness) * QualityConfidence` (about 50% × quality at your baseline)

The methodology in full is in [docs/samatha_score_methodology.md](docs/samatha_score_methodology.md).

## Recording

When recording is on, each session is written to:

`Android/data/com.mordin.samathascope/files/sessions/<timestamp>/`

- `raw.raw16le`: raw samples as signed int16, little-endian; samples lost in transmission are written as the last value, so the file keeps real time
- `features.csv`: one row per analysis window, with:
  - wall-clock timestamps
  - signal gap (ms) and detected mains frequency
  - band powers and ratios, and their z-scores
  - artefact terms and drowsiness evidence terms
  - classifier probabilities and state
  - the selected feedback source and its value
- `meta.txt`: `key=value` lines: sample rate and format, app version, start time, window length, eyes mode, notch, feedback source, then the baseline (median and MAD per feature, or `none_provisional`) when calibration ends, and the end time and duration when the session stops

## Building

| Component | Version |
|---|---|
| Android Gradle Plugin | 9.5.1, with built-in Kotlin |
| Gradle | 9.9.1 (wrapper included) |
| Kotlin | 2.4.21 |
| Compose BOM | 2026.09.00 |
| compileSdk / targetSdk / minSdk | 37 / 36 / 26 |
| Java target | 17 |

Versions are kept in [`gradle/libs.versions.toml`](gradle/libs.versions.toml). You need JDK 17 or newer; Android Studio's bundled JBR works.

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

The last command runs the instrumented tests on a connected device, including a full session on the bundled debug loop (no headset needed). Gradle installs over the existing app and **uninstalls it when it finishes**, which deletes its recordings and settings.

## Permissions

| Permission | Why |
|---|---|
| `BLUETOOTH_CONNECT` (Android 12+), `BLUETOOTH` / `BLUETOOTH_ADMIN` (Android 11 and lower) | Connect to the paired headset. No scanning: only already-paired devices are used. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK` | Keep a running session alive with the screen off. |
| `POST_NOTIFICATIONS` (Android 13+) | Show the "Session running" notification; asked for at the first session start. |

Recordings stay in the app's own storage; nothing leaves the phone unless you use **Share last session**.

## Project layout

- `app/src/main/java/com/mordin/samathascope/`
  - `BluetoothMindWaveClient.kt`, `ThinkGearParser.kt`: RFCOMM link and packet parsing
  - `EegProcessor.kt`: filtering, Welch PSD and feature extraction
  - `CalibrationManager.kt`, `ScoreModel.kt`: baseline and frontal-state classifier
  - `EegStream.kt`: stream health (stalls, sample-rate check)
  - `MainViewModel.kt`: session lifecycle, signal routing and state
  - `OutputSmoothing.kt`: smoothing and the displayed state label
  - `WindowScore.kt`: per-window session stats, plot history, feedback value and recorded row
  - `FeedbackAudio.kt`, `FeedbackAudioFocus.kt`, `NoiseAudioEngine.kt`, `BellSoundPlayer.kt`: audio feedback
  - `SessionRecorder.kt`, `SessionExport.kt`: recording, metadata and sharing
  - `DebugRawLoopStore.kt`, `DebugRawLoopCapture.kt`: debug replay
  - `SessionKeepAliveService.kt`: keeps a session running with the screen off
  - `SettingsStores.kt`: saved settings
  - `MetricGlossary.kt`: metric explanations (text in `res/values/strings.xml`)
  - `App.kt`, `DashboardTab.kt`, `SettingsTab.kt`, `LearnTab.kt`, `Plots.kt`, `UiComponents.kt`: Compose UI
- `gradle/libs.versions.toml`: dependency and plugin versions
- `docs/`: methodology, Theravada context, and design notes

## License

MIT. See [LICENSE](LICENSE).
