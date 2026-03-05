# Samatha Score Methodology

## Pipeline summary
1. Raw EEG is processed in short windows.
2. Features are extracted (including alpha and high-frequency power).
3. Relaxed Alertness Index is normalized using calibration percentiles.
4. Artefact score is estimated from contact, line noise, EMG proxy, blink proxy, and stalls.
5. Samatha Score is derived from normalized index and optional artefact penalty.

## Key formulas
- `RAI = ln((alpha + eps) / (high_freq + eps))`
- `RAI_norm = clamp((RAI - P10) / (P90 - P10), 0, 1)`
- `A = weighted(contact, line, emg, blink, stall)`
- `SamathaScore = RAI_norm * penalty(A)` when artefact penalty is enabled

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
