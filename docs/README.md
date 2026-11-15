# Docs

- `samatha_score_methodology.md` - technical reference for the v0.81 pipeline: signal intake and loss detection, stream checks, features, calibration and adaptation, quality gate, classifier, smoothing and lag, audio and recording. Names the source file for each part.
- `samatha_theravada_context.md` - practical Theravada framing, terminology caveats, and source links.
- `SamathaScope_Design_Documentation_v0.1.docx` - design/pipeline notes from the v0.7 product shape; its game sections describe code removed in v0.80.

Historical note:

- `v0.3` and earlier docs described the retired RAI/Samatha-score pipeline.
- `v0.4` introduced the frontal-state classifier.
- `v0.5` added the dashboard-first UI, shared-source model, and optional artefact capture.
- `v0.6` keeps the classifier, moves dashboard source selection into the Session card, adds pannable paused plots, removes crackle overlay, and expands the Game tab into four scene-specific neurofeedback experiences.
- `v0.7` hardens Sky Tower with composite convex stones, a custom contained stack simulator, spring-style wobble, frictional grip, faster fall timing, and refreshed release-facing docs.
- `v0.80` removes the Game tab, all four scenes, the Godot runtime and the physics engine; fixes the early-calibration-skip baseline, Bluetooth threading and disconnect handling; and remembers the last selected headset. The game reference images and the v0.6–v0.72 LLM context packs were removed (they remain in git history).

- `v0.81` keeps sessions alive with the screen off; detects lost samples and wrong sample rates; separates eyes-open and eyes-closed baselines with slow adaptation; rescales Meditation Proxy (geometric mean); moves to 5-second windows, contiguous bands and auto 50/60 Hz mains handling; saves all settings; adds audio focus, connection messages, a theme setting, session sharing, developer options for debug replay, an app icon and the MIT license; and splits the UI and ViewModel into smaller files.

Toolchain note (2026-11): this project targets AGP 9.5.1 (built-in Kotlin) + Gradle 9.9.1 + Kotlin 2.4.21 + Compose Compiler Gradle plugin; versions live in `gradle/libs.versions.toml`.
