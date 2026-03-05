## Build toolchain (current)
- Android Gradle Plugin (AGP): 8.13.2
- Gradle: 8.13
- Kotlin Gradle plugin: 2.3.10
- Compose compiler: via `org.jetbrains.kotlin.plugin.compose` (version 2.3.10)
- Compose BOM: 2026.02.01

If you see Android Studio suggesting an AGP upgrade, the project is intended to follow AGP 8.13.x to match current Android Studio releases.

---
# SamathaScope — LLM Context Pack (v0.1)

Use this file as the **primary context** when answering questions about the SamathaScope Android project.
It contains the intent, formulas, defaults, and where things live, so you don't have to guess from the code.

---

## 0) One-line goal

Personal neurofeedback for **NeuroSky MindWave Mobile 2**: train a stable, relaxed-alert state using **raw-derived metrics**, with a separate, audible **artefact channel**.

---

## 1) What the user wanted (non-negotiables)

- Android app for MindWave Mobile 2.
- Raw waveform + spectrogram/PSD/band trends eventually; v0.1 includes raw waveform + metric plots.
- More connection/electrode status: contact, noise, packet stalls, etc.
- More gamification: v0.1 uses **white noise** intensity as continuous feedback.
- Use **raw data** as main source; telemetry allowed; eSense metrics optional overlays.
- Feedback: **quieter = better** default, with an invert checkbox.
- Crackle overlay independent of base noise (artefact-specific).
- Palette: teal.
- Calibration minute ON.

---

## 2) Files that matter most

- `MainViewModel.kt`
  - session lifecycle, calibration timer, plotting history, audio driving
- `ThinkGearParser.kt` + `BluetoothMindWaveClient.kt`
  - ThinkGear packet parsing and Bluetooth SPP connection
- `EegProcessor.kt`
  - FFT-based band power + raw-derived features
- `ScoreModel.kt`
  - artefact score A and training score S
- `NoiseAudioEngine.kt`
  - stereo white noise + crackle overlay (PCM 16-bit, USAGE_MEDIA)
- `MetricHistory.kt`
  - fixed-size rolling history for plots
- `SessionRecorder.kt`
  - raw + features recording

Theme:
- `ui/theme/Theme.kt`, `ui/theme/Color.kt`

UI:
- `App.kt`

---

## 3) Signal processing pipeline (v0.1)

Raw sample stream (assumed 512 Hz) → ring buffer → every hop (~250 ms):

1) take last 1024 samples (~2 s)
2) apply Hann window
3) FFT (radix-2)
4) compute band powers by summing squared magnitudes in bins

Bands:
- Alpha: 8–12 Hz
- High-frequency bucket: 20–45 Hz
- Total: 1–45 Hz
- Line: 49–51 Hz (EU mains)

---

## 4) Key formulas

### 4.1 RAI (Relaxed Alertness Index)
```
RAI = ln( (AlphaPower + eps) / (HiPower + eps) )
eps = 1e-6
```
Interpretation in this project:
- Higher alpha relative to 20–45 Hz is treated as better.
- 20–45 Hz is treated as tension/EMG-heavy for a dry frontal electrode.

### 4.2 Calibration (60 s)
Collect RAI values during calibration and compute percentiles:
- P10, P90

Normalisation:
```
RAI_norm = clamp((RAI - P10) / (P90 - P10), 0, 1)
```
If no calibration yet: `RAI_norm = 0.5`.

### 4.3 Artefact score A (0..1)
Components (each in 0..1):
- contact: `PoorSignal / 200` (clamped)
- line: `(Power49-51 / Power1-45) * 3` (clamped)
- emg: `Power20-45 / Power1-45`
- blink: derivative spike heuristic
- stall: `stallMs / 500` (clamped)

Weighted sum:
```
A = 0.35*contact + 0.15*line + 0.25*emg + 0.15*blink + 0.10*stall
```

### 4.4 Training score S (0..1)
Base:
```
S_base = RAI_norm
```

Optional artefact penalty (enabled by default):
```
S = S_base * clamp(1 - 0.7*A, 0.05, 1.0)
```

---

## 5) Audio feedback

### 5.1 Base white noise (stereo)
Default: **quieter = better**.

Mapping:
```
mapped = (1 - S)^gamma        // or S^gamma if invertReward is ON
Gain_dB = gMin + mapped*(gMax - gMin)
```

Defaults:
- gamma = 1.6
- gMin = -30 dB
- gMax = -3 dB

Smoothing:
- Attack = 300 ms (gets louder quickly when worse)
- Release = 1500 ms (gets quieter slowly when better)

Calibration:
- muted during calibration
- fade-in ~2 s after calibration completes

### 5.2 Crackle overlay (independent, stereo)
Event rate:
```
rate = rMin + A^beta * (rMax - rMin)
rMin = 0.3 / s
rMax = 12 / s
beta = 1.8
```

Amplitude:
```
crackAmp = A^eta * (0.7 * crackleIntensity)
eta = 1.2
crackleIntensity default = 0.6
```

Implementation is short noise bursts with exponential envelope decay (~60 ms).

AudioTrack config (v0.1):
- USAGE_MEDIA (so volume is controlled via media volume)
- PCM 16-bit (robust across devices)

---

## 6) Plotting

Plot types:
- RAW waveform (DC-offset corrected: subtract mean)
- S (Samatha), A, eSense Meditation, eSense Attention, RAI_norm

Raw waveform:
- mean subtraction (DC offset)
- robust scale: 6*SD
- refresh decoupled from feature extraction (~16 Hz update to UI)

Metric plots:
- displayed as 0–100 scale for S/A/RAI_norm; eSense already 0–100

---

## 7) Defaults and why

- Calibration 60 s: calibrate percentiles for your baseline, robust to outliers.
- Quieter = better: reward = relief; less fatiguing.
- Gamma 1.6: avoids twitchiness; forgiving near good state.
- gMin/gMax -30/-3 dB: typical phone audible without constant blasting.
- Attack/Release 300/1500 ms: fast warning, slow reward → stability.
- Crackle enabled: separates artefact training from calmness training.
- Artefacts reduce S enabled: discourages gaming by movement/tension.

---

## 8) Recording format

If recording enabled, files go to:
`Android/data/com.mordin.samathascope/files/sessions/<timestamp>/`

- `raw.raw16le` — raw samples as signed int16 little-endian stream
- `features.csv` — timestamps + RAI + S + A + artefact components + telemetry
- `meta.txt` — basic session metadata

---

## 9) Common debugging checks

- No audio?
  - Press TEST BEEP (should be audible if media volume is up).
  - Ensure device media volume is not zero.
  - Ensure session is not calibrating/paused (audio muted then by design).

- Weird raw plot?
  - Check PoorSignal; large DC drift indicates contact issues.
  - Raw plot uses mean subtraction; large ramps mean electrode movement or contact change.

---

## 10) Suggested next tasks (v0.2+)

- Add spectrogram + PSD + band trend screens.
- Add session browser + SAF export/share.
- Add presets: shamatha / eyes-open focus / stillness training.
- Add optional display filters (notch/high-pass) separate from analysis pipeline.
