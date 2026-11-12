# Docs

- `SamathaScope_LLM_Context_v0.7.md` - current context pack for external LLM use, including the frontal-state classifier, session-card source model, plot navigation, and audio lifecycle. Its game sections describe code removed in v0.80.
- `samatha_score_methodology.md` - technical explanation of the current frontal-state feedback pipeline, clean calibration, optional artefact capture, explorer behavior, audio routing, and recording semantics.
- `samatha_theravada_context.md` - practical Theravada framing, terminology caveats, and source links.
- `SamathaScope_Design_Documentation_v0.1.docx` - design/pipeline notes updated to the current v0.7 product shape.

Historical note:

- `v0.3` and earlier docs described the retired RAI/Samatha-score pipeline.
- `v0.4` introduced the frontal-state classifier.
- `v0.5` added the dashboard-first UI, shared-source model, and optional artefact capture.
- `v0.6` keeps the classifier, moves dashboard source selection into the Session card, adds pannable paused plots, removes crackle overlay, and expands the Game tab into four scene-specific neurofeedback experiences.
- `v0.7` hardens Sky Tower with composite convex stones, a custom contained stack simulator, spring-style wobble, frictional grip, faster fall timing, and refreshed release-facing docs.
- `v0.80` removes the Game tab, all four scenes, the Godot runtime and the physics engine; fixes the early-calibration-skip baseline, Bluetooth threading and disconnect handling; and remembers the last selected headset.

Toolchain note (2026-11): this project targets AGP 9.5.1 (built-in Kotlin) + Gradle 9.9.1 + Kotlin 2.4.21 + Compose Compiler Gradle plugin.
