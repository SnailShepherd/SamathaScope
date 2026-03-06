# Samatha Score Methodology

## Pipeline summary
1. Raw EEG is processed in short windows.
2. Features are extracted (including alpha, high-frequency, and line-noise estimates).
3. Relaxed Alertness Index (RAI) is normalized using calibration percentiles.
4. Artefact score is estimated from contact, EMG proxy, blink proxy, and stalls.
5. Samatha Score is derived from normalized index and optional artefact penalty.

## Samatha Score vs Relaxed Alertness Index
- `RAI_norm` is the normalized EEG-derived relaxed-alertness index.
- `SamathaScore` starts from `RAI_norm` and applies optional artefact penalty.
- If artefact penalty is disabled, Samatha Score and RAI_norm are effectively the same signal.

## Key formulas
- `RAI = ln((alpha + eps) / (high_freq + eps))`
- `RAI_norm = clamp((RAI - P10) / (P90 - P10), 0, 1)`
- `A_total = weighted(contact, emg, blink, stall)`
- `A_line = line noise estimate around 50Hz (diagnostics only)`
- `SamathaScore = RAI_norm * penalty(A_total)` when artefact penalty is enabled

## Artefact weighting (v0.3)
- contact: `0.40`
- emg: `0.30`
- blink/transient: `0.20`
- stall: `0.10`
- 50Hz line noise is shown in diagnostics but excluded from total artefact score.

## Smoothing and feedback (v0.3)
- Computed indices use a 4-second moving average (16 points at 4 Hz) for:
  - audio feedback
  - game metric control
  - live metric plots
- Diagnostics and recorded feature output remain instantaneous.

## Calibration policy (v0.3)
- Initial calibration: 60 seconds to seed baseline percentiles.
- Adaptive calibration: clean rolling window (10 minutes) updates percentiles during session.
- Clean sample gate:
  - `poorSignal <= 50`
  - `totalArtefact <= 0.30`
- Adaptive calibration is seeded from initial calibration samples for continuity.

## Plot behavior
- All plots use line-only stroke rendering (no fill) to avoid tilt/jump artifacts.
- Windows are fixed and right-aligned (newest sample on the right edge).
- Each plot has independent window and Y-range settings persisted across launches.

## Raw EEG Y-range policy
- Before calibration: conservative fixed default range.
- After calibration: if user has not locked a custom range, app computes a fixed range from calibration-era signal spread.

## Game feedback mapping
- Levitation target comes from selected metric value in `[0,1]`.
- Motion uses damped inertia to reduce jitter.
- HUD shows metric value, artefact score, signal quality, elapsed time, and battery only when available.

## Limitations
- Single dry frontal electrode is sensitive to contact and muscle artifacts.
- Score quality depends on fit, stillness, and stable signal quality.
- This is a non-medical training aid.
