# Frontal-State Feedback Methodology

This document describes the current `v0.5` EEG analysis and feedback pipeline used by SamathaScope. It replaces the retired RAI/Samatha-score pipeline.

## System intent

The app is not trying to infer whole-brain meditation depth from one dry frontal electrode. It is a personalised frontal-state classifier for live feedback on MindWave Mobile 2.

Design goals:

- reward relaxed alertness rather than mere quietness
- separate drowsiness from settled practice
- suppress reward during contaminated signal
- keep formulas transparent and deterministic

## Abbreviations

- `MP`: Meditation Proxy
- `QC`: Quality Confidence
- `TBR`: theta/beta ratio
- `TAR`: theta/alpha ratio
- `ABR`: alpha/beta ratio
- `EMG`: high-frequency muscle contamination proxy, not a literal medical EMG channel
- `HF`: high-frequency power band used here for artefact diagnostics (`20-40 Hz`)

## Dashboard and feedback model

The app now uses a dashboard-first workflow:

- `Dashboard`: connection, session control, raw EEG, metric explorer, diagnostics
- `Settings`: audio, recording, notch, and plot-window controls
- `Game`: shared-source lantern scene
- `Learn`: glossary, formulas, caveats

One shared `feedbackMetric` now drives both audio and game. The default remains `MeditationProxy`.

## Pipeline summary

1. Use raw EEG at `512 Hz`.
2. Analyze the last `8 s` of raw data.
3. Update once per second.
4. Detrend each window by removing DC offset.
5. Estimate PSD with Welch method using `2 s` segments and `50%` overlap.
6. Use a main `1-35 Hz` analysis branch plus a parallel HF/diagnostic branch for `20-40 Hz` and line-noise checks.
7. Extract classifier features.
8. Normalize key features with robust per-user baselines from calibration.
9. Run a two-stage model:
   - quality gate
   - drowsiness-first state classification
10. Smooth outputs and drive audio/game feedback from the shared source.

## Extracted features

Band powers:

- `P_theta` for `4-7 Hz`
- `P_alpha` for `8-12 Hz`
- `P_beta` for `13-30 Hz`
- `P_hf` for `20-40 Hz`
- `P_4_13`
- `P_4_30`

Derived features:

- `ln(P + eps)` log powers
- relative powers
- `TBR = ln((P_theta + eps)/(P_beta + eps))`
- `TAR = ln((P_theta + eps)/(P_alpha + eps))`
- `ABR = ln((P_alpha + eps)/(P_beta + eps))`
- theta peak frequency
- alpha peak frequency
- spectral entropy over `4-30 Hz`
- `EMG = ln((P_20_40 + eps)/(P_4_13 + eps))`
- blink/transient rate from robust outlier peaks in raw data
- clip fraction
- line-noise ratio near `50 Hz`
- max gap / stall statistic

## Calibration flow

### 1. Clean baseline calibration

The clean baseline lasts 60 seconds:

- `0-30s`: sit still with eyes open
- `30-60s`: relax face and close eyes

The clean baseline stores robust medians and MADs for:

- `logBeta`
- `TBR`
- `TAR`
- `ABR`
- `Entropy`
- `EMG`

Normalization formula:

```text
z = 0.6745 * (x - median) / max(MAD, floor)
```

Rules:

- z-scores are clamped to `[-4, 4]`
- calibration prefers clean windows
- if fewer than 20 clean windows are available, the least-contaminated windows are used as backfill
- adaptive baseline updates use only clean windows from a rolling 10-minute deque

### 2. Optional artefact calibration

After the clean baseline, the app can run a separate 25-second artefact capture:

- `5s` look left/right
- `5s` look up/down
- `5s` jaw clench
- `5s` frown / forehead tension
- `5s` relax / neutral

These examples are used only to personalize blink/transient and EMG-related normalization thresholds. They do not enter the clean classifier baseline.

## Stage 1: quality gate

Component scores:

```text
contact = clamp01(poorSignal / 50)
emg = clamp01((hfRatio - 0.10) / personalizedEmgUpper)
blink = clamp01(blinkRateHz / personalizedBlinkUpper)
clip = clamp01(clipFraction / 0.01)
stall = clamp01(maxGapMs / 500)
```

Personalized upper bounds:

```text
personalizedBlinkUpper = max(1.0, artefactProfile.blinkNormalizationHz)
personalizedEmgUpper = max(0.35, artefactProfile.emgNormalizationHfRatio)
```

Total artefact score:

```text
ArtefactScore = 0.30*contact + 0.25*emg + 0.20*blink + 0.15*clip + 0.10*stall
QualityConfidence = 1 - ArtefactScore
```

Hard contamination rules:

- `poorSignal > 25`
- `clipFraction >= 0.01`
- `maxGapMs >= 150`
- `blinkRateHz >= 0.75`
- `hfRatio >= 0.35`
- `ArtefactScore > 0.45`

If any of those trip, the discrete state becomes `SIGNAL_CONTAMINATED` and the reward path is suppressed.

Note: 50 Hz line noise is still exposed in diagnostics and recording, but it is not included in the total artefact score.

## Stage 2: drowsiness-first classification

Heuristic scores:

```text
D = sigmoid(1.3*z(TAR) + 0.9*z(TBR) - 0.7*z(Entropy) - 0.4*z(ABR))
M = sigmoid(1.0*z(ABR) - 0.6*z(TAR) + 0.4*z(Entropy) - 0.3*z(EMG))
F = sigmoid(-0.9*z(ABR) - 0.7*z(TBR) + 0.4*z(logBeta) - 0.2*z(Entropy))
W = sigmoid(0.9*z(TBR) - 0.4*z(ABR) - 0.2*z(Entropy))
```

Decision rule:

- if contaminated -> `SIGNAL_CONTAMINATED`
- else if `D > 0.65` -> `DROWSY`
- else choose argmax of `M`, `F`, `W` if the top score is `> 0.55`
- otherwise -> `UNCERTAIN`

Interpretation:

- `SETTLED`: relaxed and alert
- `EFFORTFUL_FOCUS`: active control and re-focusing
- `MIND_WANDERING`: reduced control while still awake
- `DROWSY`: sleepy slowing, not a meditation reward state

## Continuous output metrics

```text
Alertness = 1 - D
Control = sigmoid(-z(TBR))
Settledness = sigmoid(z(ABR) - 0.5*z(TAR))
MeditationProxy = Settledness * Alertness * QualityConfidence
```

The default feedback signal for audio and game is `MeditationProxy`.

## Smoothing and display logic

Base EMA smoothing:

```text
smoothed = 0.3*new + 0.7*old
```

Applied to:

- state probabilities
- alertness, control, settledness
- artefact score and quality confidence
- meditation proxy / reward value

Displayed drowsiness:

- uses a slower smoother with `alpha = 0.15`
- is capped at `0.45` during clean, strongly settled, non-suppressed-entropy windows to reduce false sleepy-looking spikes

Displayed state logic:

- normal switching still uses the hold smoother
- `DROWSY` is stricter than the raw classifier:
  - 5 consecutive clean updates above threshold
  - or 1 clean update above `0.80`

## Plots and recording

Dashboard plot model:

- raw EEG strip is always visible and fixed at `5s`
- normalized metric explorer uses `0..100`
- up to 4 simultaneous lines
- default visible metrics:
  - `MeditationProxy`
  - `Alertness`
  - `DrowsyScore`
  - `ArtefactScore`

`features.csv` includes:

- timestamps
- raw feature values
- z-scored features
- artefact components
- artefact-calibration profile values
- drowsiness contribution terms
- raw and smoothed state probabilities
- raw and displayed state labels
- final shared feedback metric names and values sent to audio/game

## Behavioral implications

- Drowsiness is not rewarded.
- Contaminated windows are not rewarded.
- Quiet sleep should not look like good meditation feedback.
- The app encourages relaxed alertness, not stillness at any cost.

## Limitations

- Single dry frontal electrode recordings are fragile.
- EMG, blinks, and contact changes can dominate the signal.
- The classifier is personalised, heuristic, and session-oriented.
- This is a non-medical training aid.
