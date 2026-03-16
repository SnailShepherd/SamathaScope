## Build toolchain (current)
- Android Gradle Plugin (AGP): 8.13.2
- Gradle: 8.13
- Kotlin Gradle plugin: 2.3.10
- Compose compiler: via `org.jetbrains.kotlin.plugin.compose` (version 2.3.10)
- Compose BOM: 2026.02.01

---
# SamathaScope - LLM Context Pack (v0.72)

Use this file as the primary context when answering questions about the SamathaScope Android project.
It describes the current frontal-state classifier, dashboard/source UX, plot model, and the retuned multi-game runtime.

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
- Keep the Bluetooth/session/recording architecture intact unless a small refactor is needed.
- Dashboard source selection only affects dashboard audio. Games use fixed multi-channel mappings.

---

## 2) Files that matter most

- `MainViewModel.kt`
  - session lifecycle, calibration phases, optional artefact capture, smoothing, plotting history, dashboard source selection, audio arbitration
- `scene/SceneState.kt`
  - shared scene inputs, including calmness, stability, drift, focus, and explicit artefact intensity
- `scene/SceneStateProducer.kt`
  - smoothing and mapping from score-model output into scene-ready state values
- `App.kt`
  - dashboard/settings/game/learn UI wiring
- `GameDomain.kt`
  - game picker metadata, user-facing descriptions, and guide copy
- `scene/GameSceneHost.kt`
  - routes game selection into the correct scene host
- `scene/tower/SkyTowerHost.kt`
  - frame loop and scene snapshot handoff for Sky Tower
- `scene/tower/SkyTowerSettings.kt`
  - composite stone generation, support spans, and visual outline data
- `scene/tower/TowerPhysics.kt`
  - custom deterministic stack simulator, swept landings, friction-style support resistance, spring wobble, and contained walls
- `scene/tower/TowerSceneController.kt`
  - carrier motion, drop cadence, summary state, and camera wobble
- `scene/tower/TowerRenderer.kt`
  - painterly composite-stone rendering and in-scene guidance text
- `EegProcessor.kt`
  - 8-second windowing, Welch PSD, feature extraction, raw preview, decimated raw history
- `ScoreModel.kt`
  - quality gate, feature z-scores, drowsiness-first classifier, meditation proxy
- `MetricGlossary.kt`
  - plain-language metric explanations, pretty formulas, and term breakdowns
- `SessionRecorder.kt`
  - raw + features recording with classifier, dashboard source, and mapped game channels
- `NoiseAudioEngine.kt`
  - dashboard reward bed with no crackle overlay
- `GameSoundEngine.kt`
  - scene-specific procedural audio

---

## 3) Current UX model (v0.7)

Tabs:
- `Dashboard`
- `Settings`
- `Game`
- `Learn`

Dashboard order:
1. headset card
2. session card
3. raw EEG strip
4. multi-line metric explorer
5. diagnostics

Interaction model:
- source selection lives in the `Session` card dropdown
- allowed source metrics:
  - `MeditationProxy`
  - `Settledness`
  - `Alertness`
- metric explorer chips:
  - tap toggles line visibility
  - long-press focuses the explainer
- paused or stopped raw and metric plots can be dragged to older history
- a compact `Latest` control returns the plot to live-follow mode

Settings contains:
- audio enable/disable
- invert reward
- gamma and base-noise range
- metric-history window
- recording toggle
- optional 50 Hz notch toggle
- saved debug raw replay controls
- Sky Tower tuning controls for base width, carrier speed, and stone irregularity

Game:
- scene picker
- explicit `Start` / `Restart`
- fixed EEG mappings
- four scenes:
  - `Sky Tower`
  - `Ink Garden`
  - `Fire Keeper`
  - `Scriptorium`

Sky Tower in v0.72:
- stones are flat, convex, horizontally-elongated superellipse river-stones with organic wobble and flatter bases
- landings use friction-based slide-to-rest at the contact point — no snap-to-center or teleport
- the stack sways as a bottom-up chain: foundation anchored, each successive stone flexes more
- camera shake and ambient wobble removed entirely — instability is felt only through chain sway
- collapse mechanic (3-miss cascade) removed — play is now endless
- viewport scrolls up when tower reaches 60% height; foundation sinks off-screen
- carrier hovers dynamically above the tower top so it never overlaps tall stacks
- off-screen stones stay in the physics sim but are culled from rendering
- overlay status/detail text removed from the game canvas
- artefact score directly increases chain sway amplitude and recovery time
- invisible side walls keep stones inside the playfield

Learn:
- calibration explanation
- optional artefact-capture explanation
- metric glossary with prettier formulas plus plain-language TAR/TBR/ABR/Entropy term notes

---

## 4) Signal processing pipeline

Raw sample stream (`512 Hz`) -> ring buffer -> every `1 s`:

1. Take the last `4096` raw samples (`8 s`).
2. Remove DC offset by mean subtraction.
3. Compute Welch PSD with `2 s` segments and `50%` overlap.
4. Apply two frequency branches:
   - main analysis branch: optional `50 Hz` notch, then `1-35 Hz` features
   - HF/diagnostic branch: optional `50 Hz` notch, used for `20-40 Hz` and line-noise diagnostics
5. Extract band powers and classifier features.

Bands:
- theta: `4-7 Hz`
- alpha: `8-12 Hz`
- beta: `13-30 Hz`
- hf: `20-40 Hz`

Feature outputs:
- absolute powers: `P_theta`, `P_alpha`, `P_beta`, `P_hf`, `P_4_13`, `P_4_30`
- log powers: `ln(P + eps)`
- `TBR`
- `TAR`
- `ABR`
- theta peak frequency
- alpha peak frequency
- spectral entropy over `4-30 Hz`
- `EMG`
- blink/transient rate
- clip fraction
- line-noise ratio near `50 Hz`
- max packet gap / stall statistic

---

## 5) Calibration and normalization

### 5.1 Clean calibration

Main calibration lasts `60 s`:
- first `30 s`: eyes open, sit still
- next `30 s`: face relaxed, eyes closed

It stores robust baseline stats for:
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
- a rolling `10 min` clean deque is maintained
- adaptive recalibration recomputes robust stats from that deque only

### 5.2 Optional artefact calibration

After the clean baseline, the user may run a separate `25 s` capture:
- look left/right
- look up/down
- clench jaw
- frown / tense forehead
- relax to neutral

This capture:
- does **not** enter the clean classifier baseline
- does personalize blink/transient and EMG-related normalizations
- is stored as a separate `ArtefactCalibrationProfile`

---

## 6) Two-stage state model

### 6.1 Stage 1: quality gate

Artefact components:
```text
contact = clamp01(poorSignal / 50)
emg = clamp01((hfRatio - 0.10) / personalizedEmgUpper)
blink = clamp01(blinkRateHz / personalizedBlinkUpper)
clip = clamp01(clipFraction / 0.01)
stall = clamp01(maxGapMs / 500)
```

Where:
- `personalizedBlinkUpper = max(1.0, artefactProfile.blinkNormalizationHz)`
- `personalizedEmgUpper = max(0.35, artefactProfile.emgNormalizationHfRatio)`

Combined artefact score:
```text
ArtefactScore = 0.30*contact + 0.25*emg + 0.20*blink + 0.15*clip + 0.10*stall
QualityConfidence = 1 - ArtefactScore
```

Hard contamination gate:
- `poorSignal > 25`
- `clipFraction >= 0.01`
- `maxGapMs >= 150`
- `blinkRateHz >= 0.75`
- `hfRatio >= 0.35`
- `ArtefactScore > 0.45`

### 6.2 Stage 2: drowsiness-first classifier

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

Continuous dimensions:
```text
Alertness = 1 - D
Control = sigmoid(-z(TBR))
Settledness = sigmoid(z(ABR) - 0.5*z(TAR))
MeditationProxy = Settledness * Alertness * QualityConfidence
```

---

## 7) Game mappings

Dashboard audio source selection does **not** change game mappings.

Games consume:
- `stability = Settledness * QualityConfidence`
- `drift = MindWandering * QualityConfidence`
- `noise = ArtefactScore`
- `fatigue = slow displayed-drowsy gate`
- `precision = Control * QualityConfidence` (`Sky Tower` only)
- `correctionPulse = transient from EffortfulFocus rise`

`SceneState` also exposes smoothed `artefact` directly for scene-specific behavior.

Scene intentions:

- `Sky Tower`
  - one tap releases one hovering river-stone
  - the next stone appears only after the released one settles or fails
  - artefact score drives chain-of-stones sway amplitude and slower recovery
  - settledness improves damping and frictional grip inside the stack
  - viewport scrolls for endless play; collapse mechanic removed
- `Ink Garden`
  - low-resolution watercolor field
  - pigment spreads, pools, feathers, and splatters over wet paper
- `Fire Keeper`
  - passive flame, ember, and smoke scene
- `Scriptorium`
  - passive self-writing manuscript

---

## 8) Audio model

Dashboard reward audio:
- white-noise bed only
- no crackle overlay

Audio lifecycle:
- keep dashboard and game engines alive during an active session
- mute/unmute them for tab crossfades instead of recreating them
- pause silences both without tearing them down
- stop, disconnect, audio-off, and `onCleared` hard-stop both engines

Behavioral rule:
- drowsy or contaminated windows fade reward down
- the system should never reward quiet sleep as if it were good meditation

---

## 9) Plotting and recording

Plot model:
- raw EEG strip always visible
- normalized metric explorer uses `0..100`
- all remaining explorer metrics can be shown at once
- stable color mapping per `PlotType`
- thicker lines plus legend
- paused/stopped plots are pannable
- raw EEG keeps a separate decimated history buffer for older browsing

Recording output:
- `raw.raw16le` - signed int16 little-endian raw stream
- `features.csv` - timestamps, raw feature values, z-scored features, artefact components, drowsiness evidence terms, probabilities, raw/displayed labels, chosen dashboard source, selected game id, mapped game channels, and compact game summary
- `meta.txt` - session metadata

---

## 10) Practical caveats

- Single dry frontal electrode data is highly sensitive to contact quality, facial muscle activity, and eye blinks.
- This app is a training aid, not a medical or spiritual measurement device.
- eSense metrics are visible as telemetry only and are not treated as ground truth.
- Quietness alone is not the goal; the app aims for stable, relaxed alertness.
