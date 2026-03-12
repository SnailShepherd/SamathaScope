## Build toolchain (current)
- Android Gradle Plugin (AGP): 8.13.2
- Gradle: 8.13
- Kotlin Gradle plugin: 2.3.10
- Compose compiler: via `org.jetbrains.kotlin.plugin.compose` (version 2.3.10)
- Compose BOM: 2026.02.01

If Android Studio suggests an AGP upgrade, the project is intended to stay on AGP 8.13.x unless the whole toolchain is moved together.

---
# SamathaScope - LLM Context Pack (v0.4)

Use this file as the primary context when answering questions about the SamathaScope Android project.
It describes the current frontal-state classifier pipeline, not the retired RAI/Samatha-score design.

---

## 0) One-line goal

Personal neurofeedback for NeuroSky MindWave Mobile 2: reward relaxed, alert frontal settling while explicitly avoiding misleading reward during drowsiness or signal contamination.

---

## 1) Non-negotiables

- Android app for MindWave Mobile 2.
- Keep Bluetooth connection and ThinkGear parsing intact.
- Use raw EEG as the primary source, not eSense Meditation/Attention as ground truth.
- Work within the limits of one dry frontal electrode at FP1 with ear reference.
- Do not claim whole-brain state, connectivity, source localisation, or meditative attainment.
- Keep the existing session flow, tabs, recording lifecycle, and general app architecture unless a small refactor is needed.

---

## 2) Files that matter most

- `MainViewModel.kt`
  - session lifecycle, calibration timer, smoothing, plotting history, audio/game driving
- `ThinkGearParser.kt` + `BluetoothMindWaveClient.kt`
  - ThinkGear packet parsing and Bluetooth SPP connection
- `EegProcessor.kt`
  - 8-second windowing, Welch PSD, feature extraction, raw preview
- `CalibrationManager.kt`
  - robust feature baselines from calibration and adaptive clean-window updates
- `ScoreModel.kt`
  - quality gate, feature z-scores, drowsiness-first classifier, meditation proxy
- `MetricHistory.kt`
  - fixed-size 1 Hz history for metric plots
- `SessionRecorder.kt`
  - raw + features recording
- `NoiseAudioEngine.kt`
  - white-noise reward plus crackle overlay
- `App.kt`
  - current UI and settings wiring

---

## 3) Current signal processing pipeline (v0.4)

Raw sample stream (512 Hz) -> ring buffer -> every 1 second:

1. Take the last 4096 raw samples (8 seconds).
2. Remove DC offset by mean subtraction.
3. Compute Welch PSD with 2-second segments and 50 percent overlap.
4. Apply two frequency branches:
   - main analysis branch: optional 50 Hz notch, then 1-35 Hz features
   - HF/diagnostic branch: optional 50 Hz notch, used for 20-40 Hz and line-noise diagnostics
5. Extract band powers and classifier features.

Bands:
- theta: 4-7 Hz
- alpha: 8-12 Hz
- beta: 13-30 Hz
- hf: 20-40 Hz
- entropy range: 4-30 Hz

Feature outputs:
- absolute powers: `P_theta`, `P_alpha`, `P_beta`, `P_hf`, `P_4_13`, `P_4_30`
- log powers: `ln(P + eps)`
- relative powers
- `TBR = ln((P_theta + eps)/(P_beta + eps))`
- `TAR = ln((P_theta + eps)/(P_alpha + eps))`
- `ABR = ln((P_alpha + eps)/(P_beta + eps))`
- theta peak frequency
- alpha peak frequency
- spectral entropy over 4-30 Hz
- `EMG = ln((P_20_40 + eps)/(P_4_13 + eps))`
- blink/transient rate from robust outlier peaks in raw data
- clip fraction
- line-noise ratio near 50 Hz
- max packet gap / stall statistic

---

## 4) Calibration and normalization

Calibration stays at 60 seconds for UI simplicity, but it no longer stores percentiles of one scalar score.

Per-user baseline stats are stored for:
- `logBeta`
- `TBR`
- `TAR`
- `ABR`
- `Entropy`
- `EMG`

Robust z-score:
```text
z = 0.6745 * (x - median) / max(MAD, floor)
```
with z clamped to `[-4, 4]`.

Adaptive baselines:
- only clean windows are added
- a rolling 10-minute clean deque is maintained
- adaptive recalibration recomputes robust stats from that deque only

If fewer than 20 clean windows are available at calibration timeout, the system backfills with the least-contaminated windows so the session can still start.

---

## 5) Two-stage state model

### 5.1 Stage 1: quality gate

Artefact components:
```text
contact = clamp01(poorSignal / 50)
emg = clamp01((hfRatio - 0.10) / 0.25)
blink = clamp01(blinkRateHz / 1.0)
clip = clamp01(clipFraction / 0.01)
stall = clamp01(maxGapMs / 500)
```

Combined artefact score:
```text
ArtefactScore = 0.30*contact + 0.25*emg + 0.20*blink + 0.15*clip + 0.10*stall
QualityConfidence = 1 - ArtefactScore
```

Hard contamination gate to `SIGNAL_CONTAMINATED` if any of:
- `poorSignal > 25`
- `clipFraction >= 0.01`
- `maxGapMs >= 150`
- `blinkRateHz >= 0.75`
- `hfRatio >= 0.35`
- `ArtefactScore > 0.45`

Line noise is retained as a diagnostic feature but excluded from the total artefact score.

### 5.2 Stage 2: drowsiness-first classifier

Only clean windows proceed to the classifier.

Scores:
```text
D = sigmoid(1.3*z(TAR) + 0.9*z(TBR) - 0.7*z(Entropy) - 0.4*z(ABR))
M = sigmoid(1.0*z(ABR) - 0.6*z(TAR) + 0.4*z(Entropy) - 0.3*z(EMG))
F = sigmoid(-0.9*z(ABR) - 0.7*z(TBR) + 0.4*z(logBeta) - 0.2*z(Entropy))
W = sigmoid(0.9*z(TBR) - 0.4*z(ABR) - 0.2*z(Entropy))
```

Decision rule:
- if quality gate fails -> `SIGNAL_CONTAMINATED`
- else if `D > 0.65` -> `DROWSY`
- else choose argmax of `M`, `F`, `W` if top score `> 0.55`
- otherwise -> `UNCERTAIN`

Interpretation:
- `SETTLED`: relaxed and alert, not merely theta-heavy
- `EFFORTFUL_FOCUS`: active control or re-focusing
- `MIND_WANDERING`: reduced control while still awake
- `DROWSY`: sleepy slowing, not meditation reward

Continuous dimensions:
```text
Alertness = 1 - D
Control = sigmoid(-z(TBR))
Settledness = sigmoid(z(ABR) - 0.5*z(TAR))
MeditationProxy = Settledness * Alertness * QualityConfidence
```

---

## 6) Smoothing and state display

EMA smoothing:
```text
smoothed = 0.3*new + 0.7*old
```

Applied to:
- state probabilities
- alertness, control, settledness
- artefact score and quality confidence
- meditation proxy / reward value

Displayed discrete state:
- switch after 3 consecutive updates with the same winner
- or immediately when one smoothed probability exceeds 0.70

---

## 7) Audio and game feedback

Default reward metric:
```text
MeditationProxy = Settledness * Alertness * QualityConfidence
```

Behavioral rule:
- drowsy or contaminated windows fade the reward down
- the system should never reward quiet sleep as if it were good meditation

Selectable reward metrics in the current UI:
- `MeditationProxy`
- `Settledness`
- `Control`
- `Alertness`
- `QualityConfidence`
- `EffortfulFocusScore`

Telemetry-only overlays:
- eSense Meditation
- eSense Attention

---

## 8) Plotting and recording

Plot types:
- raw EEG
- meditation proxy
- settledness
- control
- alertness
- drowsy score
- artefact score
- quality confidence
- effortful focus score
- mind wandering score
- eSense Meditation
- eSense Attention

Metric history:
- 1 point per second
- default metric plot window: 300 seconds

Recording output:
- `raw.raw16le` - signed int16 little-endian raw stream
- `features.csv` - timestamps, raw feature values, z-scored features, artefact components, raw and smoothed probabilities, raw/displayed state labels, and final feedback values sent to audio/game
- `meta.txt` - session metadata

---

## 9) Practical caveats

- Single dry frontal electrode data is highly sensitive to contact quality, facial muscle activity, and eye blinks.
- This app is a training aid, not a medical or spiritual measurement device.
- eSense metrics are visible as telemetry only and are not treated as ground truth.
- Quietness alone is not the goal; the app aims for stable, relaxed alertness.
