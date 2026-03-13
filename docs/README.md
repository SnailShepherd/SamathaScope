# Docs

- `SamathaScope_LLM_Context_v0.6.md` - current context pack for external LLM use, including the frontal-state classifier, session-card source model, plot navigation, multi-game runtime, and audio lifecycle.
- `samatha_score_methodology.md` - technical explanation of the current frontal-state feedback pipeline, clean calibration, optional artefact capture, explorer behavior, audio routing, and recording semantics.
- `samatha_theravada_context.md` - practical Theravada framing, terminology caveats, and source links.
- `SamathaScope_Design_Documentation_v0.1.docx` - design/pipeline notes updated to the current v0.6 product shape.

Historical note:

- `v0.3` and earlier docs described the retired RAI/Samatha-score pipeline.
- `v0.4` introduced the frontal-state classifier.
- `v0.5` added the dashboard-first UI, shared-source model, and optional artefact capture.
- `v0.6` keeps the classifier, moves dashboard source selection into the Session card, adds pannable paused plots, removes crackle overlay, and expands the Game tab into four scene-specific neurofeedback experiences.

Toolchain note (2026): this project targets AGP 8.13.2 + Gradle 8.13 + Kotlin 2.3.10 + Compose Compiler Gradle plugin.
