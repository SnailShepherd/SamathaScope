# Frontal-State Feedback Methodology

This document describes the EEG analysis and feedback pipeline in SamathaScope `v0.81`. It replaced the retired RAI/Samatha-score pipeline in `v0.4`. Every number here matches the code; the source file for each part is named so it can be checked.

## System intent

The app does not try to infer whole-brain meditation depth from one dry frontal electrode. It is a personalised frontal-state classifier for live feedback on the NeuroSky MindWave Mobile 2.

Design goals:

- reward relaxed alertness rather than mere quietness
- separate drowsiness from settled practice
- suppress reward when the signal is contaminated or incomplete
- keep formulas transparent and deterministic

## Abbreviations

- `MP`: Meditation Proxy
- `QC`: Quality Confidence
- `TBR`: theta/beta ratio
- `TAR`: theta/alpha ratio
- `ABR`: alpha/beta ratio
- `EMG`: high-frequency muscle contamination proxy
- `HF`: high-frequency band used for artefact diagnostics (`20-40 Hz`)

## App workflow

- `Dashboard`: headset connection, session control (eyes mode, feedback source), raw EEG, metric explorer, diagnostics
- `Settings`: audio, plot window, recording and sharing, mains notch, developer options (debug replay), appearance
- `Learn`: glossary, formulas, caveats

The audio feedback source is chosen in the `Session` card from `Meditation Proxy`, `Settledness` or `Alertness`. The metric explorer only toggles plot visibility (tap) and opens a metric's explainer (long-press).

## Signal intake (`ThinkGearParser.kt`, `EegStream.kt`)

Raw samples arrive at `512 Hz` as signed 16-bit values in ThinkGear packets over Bluetooth RFCOMM.

**Lost samples.** Once the parser has seen a valid packet, any byte skipped while re-synchronising, and any packet that fails its checksum, means data was lost:

- skipped bytes count as `ceil(bytes / 8)` lost samples (a raw-sample packet is 8 bytes)
- a corrupt packet counts `ceil((payload length + 4) / 8)` lost samples, because a corrupted length byte can make the parser swallow several packets as one payload
- a corrupt packet of the once-per-second eSense size (32-byte payload) loses no raw samples

Each lost sample is replaced by the last value held, so analysis windows keep real time, and is counted as missing signal (see signal gap below).

**Stream readiness.** A session can start only when the stream is healthy:

- the rate is measured over `2 s` windows, so Bluetooth delivery bursts average out
- the stream is `LIVE` (ready) only when a full window measured `512 Hz ± 10%`; otherwise it shows `CONFIRMING` (no full window yet) or `WRONG_RATE`
- no sample for `900 ms` marks it `STALLED`; the rate is measured afresh when samples resume

## Pipeline summary (`EegProcessor.kt`)

1. Keep the last `5 s` of samples (`2560`); compute features once per second.
2. Optionally remove mains hum with a streaming notch (below).
3. Detrend each window by removing its mean.
4. Estimate the power spectrum with Welch's method: `2 s` Hann segments, `50%` overlap, so `4` segments per window and `0.5 Hz` resolution.
5. Analyse `1-35 Hz` for the classifier; use `20-40 Hz` for EMG diagnostics.
6. Extract features, normalise them against the calibration baseline, then run the quality gate and the drowsiness-first classifier.
7. Smooth the outputs and drive audio feedback, plots and recording.

**Mains.** The app compares raw power at `50 Hz` and `60 Hz` (each `±0.75 Hz`) and switches the detected mains frequency only when one is more than twice the other. The optional notch is an IIR biquad (`Q = 10`) at that frequency. Both notches run continuously, so toggling needs no warm-up. The notched signal feeds blink detection and both spectral branches. Line noise and clipping are always measured on the raw signal.

## Extracted features

Band powers. Bands are half-open `[low, high)`, so they share no bin and miss none: theta + alpha + beta covers `4-30 Hz` exactly.

- `P_theta`: `4-8 Hz`
- `P_alpha`: `8-13 Hz`
- `P_beta`: `13-30 Hz`
- `P_hf`: `20-40 Hz`
- `P_4_13`, `P_4_30`

Derived features:

- log powers `ln(P + eps)` and relative powers
- `TBR = ln((P_theta + eps)/(P_beta + eps))`
- `TAR = ln((P_theta + eps)/(P_alpha + eps))`
- `ABR = ln((P_alpha + eps)/(P_beta + eps))`
- theta and alpha peak frequencies
- spectral entropy over `4-30 Hz`, normalised to `0-1`
- `EMG = ln((P_20_40 + eps)/(P_4_13 + eps))`, and `hfRatio = P_hf / (P_4_13 + P_hf)`
- blink/transient rate: peaks above `median + 6 × 1.4826 × MAD` of the absolute signal, with a `250 ms` refractory period
- clip fraction: share of samples at or beyond `±1900`
- line-noise ratio: stronger of the `50 Hz` and `60 Hz` lines over `1-70 Hz` raw power
- signal gap (ms): the larger of (a) lost samples in the window and (b) how far the window's arrival-time span exceeds what its samples explain, minus `300 ms` of Bluetooth jitter tolerance. Late but complete delivery is not a gap.

## Calibration (`CalibrationManager.kt`)

### 1. Clean baseline

The clean baseline lasts `60 s`:

- `0-30 s`: sit still with eyes open
- `30-60 s`: relax the face and close the eyes

Each half produces its own baseline from the windows lying fully inside it, so no window straddles the switch or a pause. That gives about 25 windows per half. A session is scored against the half matching its **eyes setting** (`Eyes closed` by default, or `Eyes open`); the other half is never substituted. The calibration timer stops while the session is paused.

The baseline stores robust medians and MADs for `logBeta`, `TBR`, `TAR`, `ABR`, `Entropy` and `EMG`:

```text
z = 0.6745 * (x - median) / max(MAD, floor)
```

Rules:

- z-scores are clamped to `[-4, 4]`
- clean windows are preferred; if fewer than 20 are clean, the least-contaminated others fill up to 20
- if the matching half has fewer than 20 windows (an early skip, or a stall), there is no baseline: z-scores stay neutral and the dashboard marks the baseline as provisional
- calibration always ends when its timer runs out, whether or not a baseline could be built

**Adaptation during the session.** Clean session windows go into a rolling 30-minute window. The baseline used is the calibration baseline blended with that window's statistics, at weight `n / (n + 600)`: 50% after 10 minutes of clean signal, at most 75%. Slow drift (electrode settling, fatigue) is followed, while a sustained good state is not quickly re-centred to neutral. Without a calibration baseline, the session window alone is used once it holds 20 clean windows.

### 2. Optional artefact calibration

After the clean baseline, the app offers a `25 s` artefact capture: `5 s` each of looking left/right, looking up/down, jaw clench, frown, and relaxing. These windows only personalise the blink and EMG normalisation thresholds; they never enter the clean baseline.

## Stage 1: quality gate (`ScoreModel.kt`)

Component scores:

```text
contact   = clamp01(poorSignal / 50)
lineNoise = clamp01(lineNoiseRatio * 5)
emg       = clamp01((hfRatio - 0.10) / personalizedEmgSpan)
blink     = clamp01(blinkRateHz / personalizedBlinkUpper)
clip      = clamp01(clipFraction / 0.01)
stall     = clamp01(signalGapMs / 500)
```

Personalised bounds:

```text
personalizedBlinkUpper = max(1.0, artefactProfile.blinkNormalizationHz)
personalizedEmgUpper   = max(0.35, artefactProfile.emgNormalizationHfRatio)
personalizedEmgSpan    = max(0.15, personalizedEmgUpper - 0.10)
```

Total:

```text
ArtefactScore     = 0.30*contact + 0.10*lineNoise + 0.25*emg + 0.16*blink + 0.12*clip + 0.10*stall
QualityConfidence = 1 - ArtefactScore
```

A window is contaminated if any of these holds:

- `poorSignal > 25`
- `clipFraction >= 0.01`
- `signalGapMs >= 150`
- `blinkRateHz >= 0.75`
- `hfRatio >= 0.35`
- `ArtefactScore > 0.45`

A contaminated window is labelled `SIGNAL_CONTAMINATED` and the reward path is suppressed. Only windows that pass stricter thresholds (e.g. `signalGapMs < 100`, `blinkRateHz < 0.30`) count as clean for calibration and adaptation.

## Stage 2: drowsiness-first classification

```text
D = sigmoid(1.3*z(TAR) + 0.9*z(TBR) - 0.7*z(Entropy) - 0.4*z(ABR))
M = sigmoid(1.0*z(ABR) - 0.6*z(TAR) + 0.4*z(Entropy) - 0.3*z(EMG))
F = sigmoid(-0.9*z(ABR) - 0.7*z(TBR) + 0.4*z(logBeta) - 0.2*z(Entropy))
W = sigmoid(0.9*z(TBR) - 0.4*z(ABR) - 0.2*z(Entropy))
```

Decision rule:

- contaminated → `SIGNAL_CONTAMINATED`
- else `D > 0.65` → `DROWSY`
- else the largest of `M` (`SETTLED`), `F` (`EFFORTFUL_FOCUS`), `W` (`MIND_WANDERING`) if it is `> 0.55`
- otherwise → `UNCERTAIN`

## Continuous output metrics

```text
Alertness       = 1 - D
Control         = sigmoid(-z(TBR))
Settledness     = sigmoid(z(ABR) - 0.5*z(TAR))
MeditationProxy = sqrt(Settledness * Alertness) * QualityConfidence
```

The geometric mean means MP reads about `50% × QualityConfidence` at baseline, and a high reading needs settledness and alertness high together.

## Smoothing and display (`OutputSmoothing.kt`)

All outputs update once per second. They are smoothed with an exponential moving average, `smoothed = 0.3*new + 0.7*old` (time constant about 3 s). That covers state probabilities, alertness, control, settledness, artefact score, quality confidence and MP.

Displayed drowsiness:

- uses a slower smoother (`alpha = 0.15`)
- is capped at `0.45` in clean, strongly settled windows whose entropy is not suppressed, to reduce false sleepy-looking spikes

Displayed state:

- a new label must win 3 windows in a row, unless its smoothed probability is at least `0.70`
- `DROWSY` is stricter still: 5 consecutive drowsy windows, or one with `D > 0.80`

**Lag.** From a change in the signal to the displayed value, expect about 2.5 s from the window (half its length), plus about 3 s of smoothing, plus up to 3 s of state hold for labels.

## Plots, audio, recording

Plots:

- the raw EEG plot shows the live signal, thinned to 800 points with min/max pairs so spikes survive
- the metric explorer uses a `0-100` scale with stable per-metric colours
- paused or stopped plots can be dragged back through history

Audio (`FeedbackAudio.kt`, `NoiseAudioEngine.kt`):

- noise level in dB: `gMinDb + mapped^gamma * (gMaxDb - gMinDb)`, where `mapped = 1 - feedback` (better is quieter) or `feedback` with *Invert reward*
- the engine is created for the session and muted during calibration, artefact capture and pauses
- it holds audio focus; if another app takes it (a call, a video), the session pauses
- stop, disconnect, audio-off and app shutdown fade the noise out over `0.6 s`

Sessions keep running with the screen off through a foreground service with an ongoing notification and a partial wake lock (`SessionKeepAliveService.kt`).

Recording (`SessionRecorder.kt`, `SessionExport.kt`):

- `raw.raw16le`: every sample, lost ones as the held value
- `features.csv`: per window, wall-clock timestamps, raw and z-scored features, `signal_gap_ms`, `mains_hz`, artefact components and calibration profile, drowsiness terms, raw and smoothed probabilities, raw and displayed labels, feedback source and value
- `meta.txt`: app version, start time, window length, eyes mode, notch, feedback source; the baseline (median and MAD per feature, or `none_provisional`) when calibration ends; end time and duration
- *Share last session* zips the newest session folder for Android's share sheet

## Limitations

- Single dry frontal electrode recordings are fragile.
- EMG, blinks and contact changes can dominate the signal.
- The classifier is personalised, heuristic and session-oriented; its weights are hand-set, not fitted to labelled data.
- This is a non-medical training aid.
