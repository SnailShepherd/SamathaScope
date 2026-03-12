# Samatha in Theravada Context

## Practical framing

In Theravada usage, `samatha` refers to calming and stabilizing the mind. It is cultivated through steadiness, reduced agitation, and repeated settling.

## How this app uses the term

SamathaScope uses the term in a practical training sense:

- It rewards a frontal pattern interpreted as more settled, alert, and controlled.
- It explicitly tries not to confuse drowsiness with useful meditation feedback.
- It treats the computed metrics as training proxies, not direct measures of canonical attainment.

## Why the app separates drowsiness and artefact

Traditional practice does not treat dullness or torpor as the same thing as collectedness, and it does not treat mechanical stillness as proof of clarity.

The app follows that distinction by:

- fading reward down when the frontal pattern looks sleepy
- suppressing reward when the signal looks contaminated
- keeping the clean meditation baseline separate from the optional eye/jaw/frown artefact capture

## Practical caveat

The feedback model is a personalised frontal-state classifier built from one dry FP1 electrode with an ear reference. It can support training consistency, but it is not a spiritual certification, a medical tool, or a whole-brain measurement.

## Metric language in plain English

- `Meditation Proxy (MP)`: the app's main reward proxy for relaxed, alert, clean settling
- `Settledness (S)`: calm-but-organized frontal settling
- `Control (C)`: lower drift toward mind-wandering relative to baseline
- `Alertness (A)`: inverse of drowsiness evidence
- `Drowsiness (D)`: frontal slowing evidence, not proof of sleep
- `Quality Confidence (QC)`: how usable the signal looks

## Sources

- [AN 11.2 (Access to Insight)](https://www.accesstoinsight.org/tipitaka/an/an11/an11.002.than.html)
- [AN 4.41 (SuttaCentral)](https://suttacentral.net/an4.41/en/sujato)
- [With Each and Every Breath (Thanissaro Bhikkhu)](https://www.dhammatalks.org/books/WithEachAndEveryBreath/Section0005.html)
