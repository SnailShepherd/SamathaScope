# Frontal-State Feedback Methodology

This document describes the current `v0.6` EEG analysis and feedback pipeline used by SamathaScope. It replaces the retired RAI/Samatha-score pipeline.

## System intent

The app is not trying to infer whole-brain meditation depth from one dry frontal electrode. It is a personalised frontal-state classifier for live feedback on MindWave Mobile 2.

Design goals:

- reward relaxed alertness rather than mere quietness
- separate drowsiness from settled practice
- suppress reward during contaminated signal
- keep formulas transparent and deterministic
- let the dashboard and games use the same classifier without forcing the same control mapping

## Abbreviations

- `MP`: Meditation Proxy
- `QC`: Quality Confidence
- `TBR`: theta/beta ratio
- `TAR`: theta/alpha ratio
- `ABR`: alpha/beta ratio
- `EMG`: high-frequency muscle contamination proxy
- `HF`: high-frequency power band used here for artefact diagnostics (`20-40 Hz`)

## Dashboard and feedback model

The app uses a dashboard-first workflow:

- `Dashboard`: connection, session control, source dropdown, raw EEG, metric explorer, diagnostics
- `Settings`: audio, recording, notch, and plot-window controls
- `Game`: four neurofeedback scenes with their own fixed EEG mappings
- `Learn`: glossary, formulas, caveats

Dashboard audio source selection now lives in the `Session` card and is limited to:

- `Meditation Proxy`
- `Settledness`
- `Alertness`

The metric explorer no longer changes the source. Tap toggles visibility. Long-press focuses the explainer.

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
10. Smooth outputs and drive dashboard audio, plots, and game mappings.

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
  - computed from the raw spectrum so quality checks stay notch-independent
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
lineNoise = clamp01(lineNoiseRatio * 5)
emg = clamp01((hfRatio - 0.10) / personalizedEmgSpan)
blink = clamp01(blinkRateHz / personalizedBlinkUpper)
clip = clamp01(clipFraction / 0.01)
stall = clamp01(maxGapMs / 500)
```

Personalized upper bounds:

```text
personalizedBlinkUpper = max(1.0, artefactProfile.blinkNormalizationHz)
personalizedEmgUpper = max(0.35, artefactProfile.emgNormalizationHfRatio)
personalizedEmgSpan = max(0.15, personalizedEmgUpper - 0.10)
```

Total artefact score:

```text
ArtefactScore = 0.30*contact + 0.10*lineNoise + 0.25*emg + 0.16*blink + 0.12*clip + 0.10*stall
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

## Game signal mapping

The games do not use the dashboard source dropdown. They consume a fixed mapping:

```text
stability = Settledness * QualityConfidence
drift = MindWandering * QualityConfidence
noise = ArtefactScore
fatigue = slow_gate(DisplayedDrowsyScore)
precision = Control * QualityConfidence
correctionPulse = transient(EffortfulFocus rise)
```

Interpretation:

- `stability`: coherence, damping, completion
- `drift`: wandering, sway, tangling, broken continuity
- `noise`: visible glitch, tremor, splatter
- `fatigue`: slow dimming or stalling only
- `precision`: used only for `Sky Tower`
- `correctionPulse`: short rescue window, not a sustained reward lane

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
- is additionally re-used by the games only through a slower fatigue gate

Displayed state logic:

- normal switching still uses the hold smoother
- `DROWSY` is stricter than the raw classifier:
  - 5 consecutive clean updates above threshold
  - or 1 clean update above `0.80`

## Plots, audio, and recording

Dashboard plot model:

- raw EEG strip stays visible
- normalized metric explorer uses `0..100`
- every remaining explorer metric can be shown at once
- per-metric colors are stable across chips, lines, legend, and explainer
- paused or stopped plots can be dragged to older history
- raw plotting keeps a separate decimated history buffer for older browsing

Audio model:

- dashboard noise audio and game audio both exist for the session
- tab switches crossfade by muting/unmuting persistent engines instead of recreating them
- pause silences both without tearing them down
- stop, disconnect, audio-off, and `onCleared` hard-stop both engines so no audio leaks remain
- artefact crackle overlay has been removed

`features.csv` includes:

- timestamps
- raw feature values
- z-scored features
- artefact components
- artefact-calibration profile values
- drowsiness contribution terms
- raw and smoothed state probabilities
- raw and displayed state labels
- selected dashboard feedback metric names and values
- selected game id
- mapped game signal channels and a compact game runtime summary

## Limitations

- Single dry frontal electrode recordings are fragile.
- EMG, blinks, and contact changes can dominate the signal.
- The classifier is personalised, heuristic, and session-oriented.
- This is a non-medical training aid.
