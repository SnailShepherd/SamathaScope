extends Node2D

const InkMotifRef = preload("res://scenes/ink_garden/InkMotif.gd")
const PRESET_SWITCH_MIN_DWELL_SECONDS := 0.90
const PRESET_SWITCH_COOLDOWN_SECONDS := 1.35
const PRESET_SWITCH_SCORE_MARGIN := 0.10
const BRUSH_PRESETS := {
	"breath_line": {
		"preset_name": "breath_line",
		"taper": 1.00,
		"bleed": 1.00,
		"pooling": 1.00,
		"jitter": 0.30,
		"core_break": 0.00,
		"opacity": 1.00,
		"stroke_cap": 22,
		"reactive_accent_cap": 2,
	},
	"still_pool": {
		"preset_name": "still_pool",
		"taper": 0.88,
		"bleed": 1.18,
		"pooling": 1.32,
		"jitter": 0.16,
		"core_break": -0.06,
		"opacity": 0.96,
		"stroke_cap": 20,
		"reactive_accent_cap": 1,
	},
	"calligrapher": {
		"preset_name": "calligrapher",
		"taper": 1.16,
		"bleed": 0.88,
		"pooling": 0.92,
		"jitter": 0.10,
		"core_break": -0.04,
		"opacity": 1.08,
		"stroke_cap": 18,
		"reactive_accent_cap": 1,
	},
	"wander_dry": {
		"preset_name": "wander_dry",
		"taper": 1.08,
		"bleed": 0.78,
		"pooling": 0.72,
		"jitter": 0.44,
		"core_break": 0.10,
		"opacity": 0.92,
		"stroke_cap": 17,
		"reactive_accent_cap": 1,
	},
	"storm_reed": {
		"preset_name": "storm_reed",
		"taper": 1.22,
		"bleed": 0.92,
		"pooling": 0.86,
		"jitter": 0.52,
		"core_break": 0.08,
		"opacity": 1.04,
		"stroke_cap": 16,
		"reactive_accent_cap": 1,
	},
}

@onready var stem_system := $StemSystem
@onready var bloom_system := $BloomSystem
@onready var post_fx_layer := $PostFXLayer

var _composition := {}
var _runtime := {}
var _viewport_size := Vector2.ZERO
var _composition_version := -1
var _composition_seed := 0
var _wash_phase := 0.0
var _visual_ready_sent := false
var _telemetry_timer := 0.0

func _ready() -> void:
	set_process(true)
	_ensure_runtime()
	queue_redraw()

func _process(delta: float) -> void:
	_wash_phase += delta
	_telemetry_timer += delta
	_ensure_runtime()
	if _composition.is_empty():
		return

	var state: Dictionary = SceneParamBus.scene_state
	var active: bool = SceneParamBus.running and not SceneParamBus.paused
	_update_runtime(delta, state, active)

	stem_system.apply_state(_composition, state, float(_runtime.get("richness", 0.0)))
	bloom_system.apply_state(_composition, state, float(_runtime.get("richness", 0.0)), stem_system.tip_positions(), delta)
	post_fx_layer.apply_state(_composition, state, float(_runtime.get("richness", 0.0)), delta)

	if not _visual_ready_sent and _should_signal_visual_ready():
		_visual_ready_sent = true
		call_deferred("_notify_visual_ready")

	if _telemetry_timer >= 0.20:
		_telemetry_timer = 0.0
		_report_telemetry()

	queue_redraw()

func _draw() -> void:
	var size := get_viewport_rect().size
	if _composition.is_empty() or size.x <= 1.0 or size.y <= 1.0:
		return

	for wash in _composition.get("background_washes", []):
		var reveal := float(wash.get("reveal", 1.0))
		if reveal <= 0.01:
			continue
		var offset := Vector2(
			sin(_wash_phase * float(wash["drift_speed"])) * float(wash["drift_x"]),
			cos(_wash_phase * float(wash["drift_speed"]) * 0.8) * float(wash["drift_y"])
		)
		_draw_soft_cloud(
			wash["center"] + offset,
			float(wash["radius"]) * (0.92 + reveal * 0.10),
			Color(wash["color"].r, wash["color"].g, wash["color"].b, wash["color"].a * reveal),
			int(wash["rings"])
		)

	var horizon_alpha := 0.018 + float(_runtime.get("richness", 0.0)) * 0.035
	draw_line(
		Vector2(size.x * 0.08, size.y * 0.86),
		Vector2(size.x * 0.92, size.y * 0.86),
		Color(0.24, 0.21, 0.18, horizon_alpha),
		1.6
	)

func _notify_visual_ready() -> void:
	SceneParamBus.notify_visual_ready("ink_garden")

func _ensure_runtime() -> void:
	var viewport_size := get_viewport_rect().size
	if viewport_size.x <= 1.0 or viewport_size.y <= 1.0:
		return
	var payload_seed := int(SceneParamBus.composition_seed)
	if payload_seed <= 0:
		payload_seed = 1
	if SceneParamBus.version != _composition_version or viewport_size != _viewport_size or payload_seed != _composition_seed:
		_viewport_size = viewport_size
		_composition_version = SceneParamBus.version
		_composition_seed = payload_seed
		_visual_ready_sent = false
		_telemetry_timer = 0.0
		_runtime = _build_runtime(viewport_size, payload_seed)
		_composition = _runtime["composition"]
		stem_system.reset_composition()
		bloom_system.reset_composition()
		post_fx_layer.reset_composition()
		_report_telemetry()

func _build_runtime(size: Vector2, seed: int) -> Dictionary:
	var motif := _motif_for_seed(seed)
	var layout := _build_layout(motif, size, seed)
	var initial_preset_name := _desired_brush_preset_name(SceneParamBus.scene_state)
	var initial_brush_profile := _finish_brush_profile(BRUSH_PRESETS[initial_preset_name].duplicate(true), SceneParamBus.scene_state)
	var composition := {
		"motif": motif,
		"name": _motif_name(motif),
		"strokes": [],
		"background_washes": _background_washes_for(motif, layout, size, seed),
		"washes": [],
		"blossoms": [],
		"mist_bands": [],
		"seal": {},
		"brush_profile": initial_brush_profile,
		"ink_effect_settings": SceneParamBus.ink_settings.duplicate(true),
	}
	var queue := _plan_actions(motif, layout, seed)
	var target_weight := 0.0
	for action in queue:
		target_weight += float(action.get("weight", 0.08))
	return {
		"seed": seed,
		"motif": motif,
		"motif_name": _motif_name(motif),
		"layout": layout,
		"composition": composition,
		"action_queue": queue,
		"target_weight": max(target_weight, 0.1),
		"richness": 0.0,
		"growth_active": false,
		"spawn_cooldown": 0.10,
		"accent_cooldown": 0.0,
		"next_element_id": 1,
		"reactive_accents_spawned": 0,
		"active_preset_name": initial_preset_name,
		"pending_preset_name": "",
		"pending_preset_seconds": 0.0,
		"preset_switch_cooldown": 0.0,
	}

func _build_layout(motif: int, size: Vector2, seed: int) -> Dictionary:
	match motif:
		InkMotifRef.InkMotif.ORCHID:
			var base := _p(size, 0.14 + (_seed_unit(seed, 1) * 0.12), 0.92 - (_seed_unit(seed, 2) * 0.04))
			return {
				"base": base,
				"lean": -0.18 + (_seed_unit(seed, 3) * 0.36),
				"flower_center": _p(size, 0.52 + (_seed_unit(seed, 4) * 0.18), 0.34 + (_seed_unit(seed, 5) * 0.18)),
				"arc_bias": -0.10 + (_seed_unit(seed, 6) * 0.20),
			}
		InkMotifRef.InkMotif.PLUM_BRANCH:
			var start := _p(size, 0.16 + (_seed_unit(seed, 11) * 0.08), 0.78 + (_seed_unit(seed, 12) * 0.08))
			var end := _p(size, 0.76 + (_seed_unit(seed, 13) * 0.14), 0.12 + (_seed_unit(seed, 14) * 0.12))
			return {
				"start": start,
				"mid": start.lerp(end, 0.46),
				"end": end,
				"branch_span": 0.22 + (_seed_unit(seed, 15) * 0.16),
				"lift": -0.16 + (_seed_unit(seed, 16) * 0.22),
			}
		_:
			var rock_center := _p(size, 0.66 + (_seed_unit(seed, 21) * 0.10), 0.72 + (_seed_unit(seed, 22) * 0.06))
			return {
				"rock_center": rock_center,
				"rock_size": Vector2(size.x * (0.14 + (_seed_unit(seed, 23) * 0.08)), size.y * (0.10 + (_seed_unit(seed, 24) * 0.08))),
				"trunk_base": _p(size, 0.66 + (_seed_unit(seed, 25) * 0.08), 0.68 + (_seed_unit(seed, 26) * 0.05)),
				"trunk_top": _p(size, 0.56 + (_seed_unit(seed, 27) * 0.10), 0.16 + (_seed_unit(seed, 28) * 0.10)),
				"canopy_width": size.x * (0.22 + (_seed_unit(seed, 29) * 0.18)),
			}

func _plan_actions(motif: int, layout: Dictionary, seed: int) -> Array:
	var actions: Array = []
	match motif:
		InkMotifRef.InkMotif.ORCHID:
			var leaf_count := 4 + int(floor(_seed_unit(seed, 41) * 3.0))
			for leaf_index in range(leaf_count):
				actions.append({ "kind": "leaf", "slot": leaf_index, "weight": 0.08, "primary": leaf_index < 3 })
			var stem_count := 2 + int(floor(_seed_unit(seed, 42) * 2.0))
			for stem_index in range(stem_count):
				actions.append({ "kind": "orchid_stem", "slot": stem_index, "weight": 0.07 })
			actions.append({ "kind": "wash", "slot": 0, "weight": 0.06 })
			for blossom_index in range(stem_count + 1):
				actions.append({ "kind": "orchid_blossom", "slot": blossom_index, "weight": 0.06 })
			actions.append({ "kind": "mist", "slot": 0, "weight": 0.03 })
			actions.append({ "kind": "seal", "slot": 0, "weight": 0.02 })
		InkMotifRef.InkMotif.PLUM_BRANCH:
			actions.append({ "kind": "plum_trunk", "slot": 0, "weight": 0.16, "primary": true })
			var branch_count := 3 + int(floor(_seed_unit(seed, 51) * 2.0))
			for branch_index in range(branch_count):
				actions.append({ "kind": "plum_branch", "slot": branch_index, "weight": 0.09, "primary": branch_index == 0 })
			var twig_count := 3 + int(floor(_seed_unit(seed, 52) * 3.0))
			for twig_index in range(twig_count):
				actions.append({ "kind": "plum_twig", "slot": twig_index, "weight": 0.05 })
			for wash_index in range(2):
				actions.append({ "kind": "wash", "slot": wash_index, "weight": 0.05 })
			var blossom_count := 4 + int(floor(_seed_unit(seed, 53) * 4.0))
			for blossom_index in range(blossom_count):
				actions.append({ "kind": "plum_blossom", "slot": blossom_index, "weight": 0.05 })
			actions.append({ "kind": "mist", "slot": 0, "weight": 0.03 })
			actions.append({ "kind": "seal", "slot": 0, "weight": 0.02 })
		_:
			actions.append({ "kind": "pine_rock_wash", "slot": 0, "weight": 0.08 })
			actions.append({ "kind": "pine_rock_outline", "slot": 0, "weight": 0.10, "primary": true })
			actions.append({ "kind": "pine_trunk", "slot": 0, "weight": 0.12, "primary": true })
			var branch_count := 3 + int(floor(_seed_unit(seed, 61) * 3.0))
			for branch_index in range(branch_count):
				actions.append({ "kind": "pine_branch", "slot": branch_index, "weight": 0.08 })
			var canopy_count := 3 + int(floor(_seed_unit(seed, 62) * 3.0))
			for canopy_index in range(canopy_count):
				actions.append({ "kind": "pine_canopy", "slot": canopy_index, "weight": 0.06 })
			actions.append({ "kind": "mist", "slot": 0, "weight": 0.04 })
			actions.append({ "kind": "seal", "slot": 0, "weight": 0.02 })
	return actions

func _update_runtime(delta: float, state: Dictionary, active: bool) -> void:
	_runtime["preset_switch_cooldown"] = max(float(_runtime.get("preset_switch_cooldown", 0.0)) - delta, 0.0)
	var brush_profile := _compose_brush_profile(state, delta)
	_runtime["brush_profile"] = brush_profile
	_composition["brush_profile"] = brush_profile
	_composition["ink_effect_settings"] = SceneParamBus.ink_settings.duplicate(true)
	var accent_cooldown := max(float(_runtime.get("accent_cooldown", 0.0)) - delta, 0.0)
	_runtime["accent_cooldown"] = accent_cooldown
	if active:
		_runtime["growth_active"] = true
		var spawn_cooldown := float(_runtime.get("spawn_cooldown", 0.0)) - (delta * _spawn_rate(state))
		if spawn_cooldown <= 0.0 and not _runtime["action_queue"].is_empty():
			_spawn_next_action(state)
			spawn_cooldown = _spawn_delay(state)
		_runtime["spawn_cooldown"] = spawn_cooldown
		_maybe_queue_reactive_accent(state)
	else:
		_runtime["growth_active"] = false
	_update_reveals(delta, state)
	_runtime["richness"] = _compute_richness()
	_runtime["growth_active"] = _has_pending_growth()

func _spawn_next_action(state: Dictionary) -> void:
	if _runtime["action_queue"].is_empty():
		return
	var action: Dictionary = _runtime["action_queue"].pop_front()
	match int(_runtime["motif"]):
		InkMotifRef.InkMotif.ORCHID:
			_spawn_orchid_action(action, state)
		InkMotifRef.InkMotif.PLUM_BRANCH:
			_spawn_plum_action(action, state)
		_:
			_spawn_pine_action(action, state)

func _spawn_orchid_action(action: Dictionary, state: Dictionary) -> void:
	var slot := int(action.get("slot", 0))
	var layout: Dictionary = _runtime["layout"]
	var base: Vector2 = layout["base"]
	var lean: float = float(layout["lean"])
	var flower_center: Vector2 = layout["flower_center"]
	var arc_bias: float = float(layout["arc_bias"])
	var drift := float(state.get("drift", 0.0))
	var focus := float(state.get("focus", 0.0))
	var stability := float(state.get("stability", 0.0))
	var calmness := float(state.get("calmness", 0.0))
	match String(action.get("kind", "")):
		"leaf":
			var spread := lerp(-0.34, 0.88, float(slot) / float(max(3, int(action.get("slot", 0)) + 3)))
			var leaf_length := _viewport_size.y * (0.30 + (_seed_unit(_composition_seed, 110 + slot) * 0.38))
			var end: Vector2 = base + Vector2(
				(_viewport_size.x * (spread + drift * 0.16 + lean * 0.22)) * (0.34 + calmness * 0.18),
				-leaf_length
			)
			var control_a: Vector2 = base + Vector2(_viewport_size.x * (0.02 + spread * 0.18), -leaf_length * 0.28)
			var control_b: Vector2 = end + Vector2(_viewport_size.x * (-0.06 + drift * 0.08), leaf_length * (0.18 - focus * 0.08))
			_add_stroke(
				"leaf",
				_cubic_points(base, control_a, control_b, end, 28),
				18.0 + (_seed_unit(_composition_seed, 120 + slot) * 10.0),
				1.6 + focus * 0.8,
				Color(0.06, 0.06, 0.07, 0.94),
				0.08 + (1.0 - calmness) * 0.14,
				0.10 + float(slot % 2) * 0.08,
				0.74 + focus * 0.16,
				float(action.get("weight", 0.08)),
				bool(action.get("primary", false))
			)
		"orchid_stem":
			var blossom_anchor: Vector2 = flower_center + Vector2(
				(_viewport_size.x * 0.06) * (float(slot) - 0.8 + drift * 0.6),
				(_viewport_size.y * 0.05) * (float(slot) - 0.4)
			)
			var stem_start: Vector2 = base + Vector2(_viewport_size.x * (0.06 + float(slot) * 0.03), -_viewport_size.y * (0.08 + float(slot) * 0.03))
			var control_a_stem: Vector2 = stem_start + Vector2(_viewport_size.x * (0.10 + arc_bias * 0.10), -_viewport_size.y * 0.14)
			var control_b_stem: Vector2 = blossom_anchor + Vector2(-_viewport_size.x * (0.08 - drift * 0.04), _viewport_size.y * 0.10)
			_add_stroke(
				"stem",
				_cubic_points(stem_start, control_a_stem, control_b_stem, blossom_anchor, 20),
				4.6 + stability * 1.2,
				0.9 + focus * 0.4,
				Color(0.14, 0.14, 0.14, 0.76),
				0.18 - focus * 0.08,
				0.08 + calmness * 0.06,
				0.86 + float(state.get("intensity", 0.0)) * 0.20,
				float(action.get("weight", 0.07))
			)
		"orchid_blossom":
			var blossom_center: Vector2 = flower_center + Vector2(
				(_viewport_size.x * 0.09) * (-0.4 + _seed_unit(_composition_seed, 140 + slot) + drift * 0.25),
				(_viewport_size.y * 0.07) * (-0.3 + _seed_unit(_composition_seed, 150 + slot))
			)
			_add_blossom(
				blossom_center,
				_viewport_size.y * (0.013 + _seed_unit(_composition_seed, 160 + slot) * 0.008),
				4 + int(floor(_seed_unit(_composition_seed, 170 + slot) * 2.0)),
				slot % 2 == 0 and float(state.get("intensity_rate", 0.0)) > -0.20,
				0.96,
				float(action.get("weight", 0.06))
			)
		"wash":
			_add_wash(
				flower_center + Vector2(-_viewport_size.x * 0.06, _viewport_size.y * 0.08),
				_viewport_size.y * (0.08 + calmness * 0.05),
				Color(0.18, 0.18, 0.18, 0.07 + calmness * 0.03),
				4,
				0.58,
				float(action.get("weight", 0.06))
			)
		"mist":
			_add_mist_band(
				[
					_p(_viewport_size, 0.10, 0.84),
					_p(_viewport_size, 0.26, 0.82),
					_p(_viewport_size, 0.46, 0.83),
					_p(_viewport_size, 0.72, 0.85),
				],
				4.0 + calmness * 2.2,
				Color(0.20, 0.18, 0.18, 0.05),
				0.42,
				float(action.get("weight", 0.03))
			)
		"seal":
			_set_seal(
				_p(_viewport_size, 0.85, 0.84),
				_viewport_size.y * 0.036,
				0.68,
				float(action.get("weight", 0.02))
			)

func _spawn_plum_action(action: Dictionary, state: Dictionary) -> void:
	var slot := int(action.get("slot", 0))
	var layout: Dictionary = _runtime["layout"]
	var start: Vector2 = layout["start"]
	var end: Vector2 = layout["end"]
	var mid: Vector2 = layout["mid"]
	var lift: float = float(layout["lift"])
	var drift := float(state.get("drift", 0.0))
	var focus := float(state.get("focus", 0.0))
	var stability := float(state.get("stability", 0.0))
	var intensity := float(state.get("intensity", 0.0))
	match String(action.get("kind", "")):
		"plum_trunk":
			var trunk_mid: Vector2 = mid + Vector2(_viewport_size.x * drift * 0.08, -_viewport_size.y * 0.02)
			var control_a: Vector2 = start.lerp(trunk_mid, 0.42) + Vector2(_viewport_size.x * 0.06, -_viewport_size.y * (0.05 + lift))
			var control_b: Vector2 = trunk_mid.lerp(end, 0.52) + Vector2(-_viewport_size.x * 0.04, -_viewport_size.y * (0.04 + drift * 0.06))
			var points := _concat_points([
				_cubic_points(start, control_a, trunk_mid + Vector2(-_viewport_size.x * 0.04, _viewport_size.y * 0.03), trunk_mid, 18),
				_cubic_points(trunk_mid, trunk_mid + Vector2(_viewport_size.x * 0.06, -_viewport_size.y * 0.04), control_b, end, 18),
			])
			_add_stroke(
				"trunk",
				points,
				28.0 + intensity * 8.0,
				4.6 + focus * 1.2,
				Color(0.05, 0.05, 0.06, 0.96),
				0.10 + (1.0 - stability) * 0.10,
				0.18 + intensity * 0.08,
				0.68 + intensity * 0.14,
				float(action.get("weight", 0.16)),
				true
			)
		"plum_branch":
			var branch_origin: Vector2 = start.lerp(end, 0.32 + float(slot) * 0.14)
			var branch_length: float = _viewport_size.x * (0.14 + _seed_unit(_composition_seed, 210 + slot) * 0.18)
			var branch_dir := -1.0 if slot % 2 == 0 else 1.0
			var branch_end: Vector2 = branch_origin + Vector2(
				branch_length * branch_dir,
				-_viewport_size.y * (0.10 + _seed_unit(_composition_seed, 220 + slot) * 0.12)
			)
			var control_a_branch: Vector2 = branch_origin + Vector2(branch_length * 0.26 * branch_dir, -_viewport_size.y * 0.06)
			var control_b_branch: Vector2 = branch_end + Vector2(-branch_length * 0.18 * branch_dir, _viewport_size.y * (0.05 - focus * 0.03))
			_add_stroke(
				"branch",
				_cubic_points(branch_origin, control_a_branch, control_b_branch, branch_end, 18),
				11.0 + intensity * 2.5,
				1.4 + focus * 0.6,
				Color(0.07, 0.07, 0.07, 0.92),
				0.16 + (1.0 - stability) * 0.08,
				0.12 + intensity * 0.06,
				0.78 + intensity * 0.18,
				float(action.get("weight", 0.09)),
				bool(action.get("primary", false))
			)
		"plum_twig":
			var twig_origin: Vector2 = start.lerp(end, 0.24 + float(slot) * 0.10) + Vector2(
				_viewport_size.x * (-0.03 + _seed_unit(_composition_seed, 230 + slot) * 0.06),
				-_viewport_size.y * (0.02 + _seed_unit(_composition_seed, 240 + slot) * 0.04)
			)
			var twig_dir := -1.0 if _seed_unit(_composition_seed, 250 + slot) < 0.5 else 1.0
			var twig_end: Vector2 = twig_origin + Vector2(
				_viewport_size.x * (0.08 + _seed_unit(_composition_seed, 260 + slot) * 0.08) * twig_dir,
				-_viewport_size.y * (0.06 + _seed_unit(_composition_seed, 270 + slot) * 0.07)
			)
			_add_stroke(
				"twig",
				_cubic_points(
					twig_origin,
					twig_origin + Vector2(_viewport_size.x * 0.03 * twig_dir, -_viewport_size.y * 0.03),
					twig_end + Vector2(-_viewport_size.x * 0.02 * twig_dir, _viewport_size.y * 0.02),
					twig_end,
					14
				),
				3.8 + stability * 0.8,
				0.8 + focus * 0.2,
				Color(0.08, 0.08, 0.08, 0.82),
				0.22 - focus * 0.06,
				0.08,
				0.90,
				float(action.get("weight", 0.05))
			)
		"plum_blossom":
			var blossom_base: Vector2 = start.lerp(end, 0.24 + float(slot) * 0.10)
			var blossom_center: Vector2 = blossom_base + Vector2(
				_viewport_size.x * (-0.08 + _seed_unit(_composition_seed, 280 + slot) * 0.16 + drift * 0.04),
				-_viewport_size.y * (0.04 + _seed_unit(_composition_seed, 290 + slot) * 0.10)
			)
			_add_blossom(
				blossom_center,
				_viewport_size.y * (0.012 + _seed_unit(_composition_seed, 300 + slot) * 0.008),
				5,
				true,
				1.02,
				float(action.get("weight", 0.05))
			)
		"wash":
			var wash_center: Vector2 = start.lerp(end, 0.32 + float(slot) * 0.20) + Vector2(0.0, _viewport_size.y * 0.08)
			_add_wash(
				wash_center,
				_viewport_size.y * (0.07 + _seed_unit(_composition_seed, 310 + slot) * 0.04),
				Color(0.16, 0.16, 0.16, 0.07 + intensity * 0.04),
				4,
				0.52,
				float(action.get("weight", 0.05))
			)
		"mist":
			_add_mist_band(
				[
					_p(_viewport_size, 0.12, 0.82),
					_p(_viewport_size, 0.36, 0.81),
					_p(_viewport_size, 0.58, 0.82),
					_p(_viewport_size, 0.84, 0.84),
				],
				5.0,
				Color(0.20, 0.18, 0.18, 0.04),
				0.42,
				float(action.get("weight", 0.03))
			)
		"seal":
			_set_seal(
				_p(_viewport_size, 0.15, 0.86),
				_viewport_size.y * 0.036,
				0.68,
				float(action.get("weight", 0.02))
			)

func _spawn_pine_action(action: Dictionary, state: Dictionary) -> void:
	var slot := int(action.get("slot", 0))
	var layout: Dictionary = _runtime["layout"]
	var rock_center: Vector2 = layout["rock_center"]
	var rock_size: Vector2 = layout["rock_size"]
	var trunk_base: Vector2 = layout["trunk_base"]
	var trunk_top_base: Vector2 = layout["trunk_top"]
	var canopy_width: float = float(layout["canopy_width"])
	var drift := float(state.get("drift", 0.0))
	var focus := float(state.get("focus", 0.0))
	var calmness := float(state.get("calmness", 0.0))
	var intensity := float(state.get("intensity", 0.0))
	match String(action.get("kind", "")):
		"pine_rock_wash":
			_add_wash(
				rock_center + Vector2(-_viewport_size.x * 0.02, _viewport_size.y * 0.03),
				rock_size.x * (0.70 + calmness * 0.20),
				Color(0.17, 0.17, 0.17, 0.09),
				5,
				0.48,
				float(action.get("weight", 0.08))
			)
		"pine_rock_outline":
			var rock_points := [
				rock_center + Vector2(-rock_size.x * 0.78, rock_size.y * 0.24),
				rock_center + Vector2(-rock_size.x * 0.40, -rock_size.y * 0.22),
				rock_center + Vector2(rock_size.x * 0.12, -rock_size.y * 0.34),
				rock_center + Vector2(rock_size.x * 0.72, -rock_size.y * 0.06),
				rock_center + Vector2(rock_size.x * 0.54, rock_size.y * 0.40),
				rock_center + Vector2(-rock_size.x * 0.22, rock_size.y * 0.46),
			]
			_add_stroke(
				"rock",
				_rounded_polyline(rock_points, 10),
				18.0,
				7.6,
				Color(0.12, 0.12, 0.12, 0.84),
				0.12,
				0.14,
				0.64,
				float(action.get("weight", 0.10)),
				true
			)
		"pine_trunk":
			var trunk_top: Vector2 = trunk_top_base + Vector2(_viewport_size.x * drift * 0.04, 0.0)
			_add_stroke(
				"trunk",
				_concat_points([
					_cubic_points(
						trunk_base,
						trunk_base + Vector2(_viewport_size.x * 0.04, -_viewport_size.y * 0.12),
						trunk_base + Vector2(-_viewport_size.x * 0.06, -_viewport_size.y * 0.24),
						trunk_base.lerp(trunk_top, 0.48),
						18
					),
					_cubic_points(
						trunk_base.lerp(trunk_top, 0.48),
						trunk_base.lerp(trunk_top, 0.48) + Vector2(_viewport_size.x * 0.02, -_viewport_size.y * 0.10),
						trunk_top + Vector2(-_viewport_size.x * 0.04, _viewport_size.y * 0.10),
						trunk_top,
						16
					),
				]),
				20.0 + intensity * 6.0,
				4.0 + focus * 1.2,
				Color(0.05, 0.05, 0.05, 0.96),
				0.10 + (1.0 - calmness) * 0.08,
				0.18,
				0.72,
				float(action.get("weight", 0.12)),
				true
			)
		"pine_branch":
			var branch_origin: Vector2 = trunk_top_base.lerp(trunk_base, 0.18 + float(slot) * 0.12)
			var branch_dir := -1.0 if slot % 2 == 0 else 1.0
			var branch_reach: float = canopy_width * (0.46 + _seed_unit(_composition_seed, 410 + slot) * 0.28)
			var branch_end: Vector2 = branch_origin + Vector2(branch_reach * branch_dir, -_viewport_size.y * (0.02 + _seed_unit(_composition_seed, 420 + slot) * 0.07))
			_add_stroke(
				"branch",
				_cubic_points(
					branch_origin,
					branch_origin + Vector2(branch_reach * 0.24 * branch_dir, -_viewport_size.y * 0.03),
					branch_end + Vector2(-branch_reach * 0.18 * branch_dir, _viewport_size.y * 0.04),
					branch_end,
					18
				),
				7.0 + intensity * 2.0,
				1.4,
				Color(0.07, 0.07, 0.07, 0.90),
				0.14,
				0.10,
				0.82,
				float(action.get("weight", 0.08))
			)
		"pine_canopy":
			var canopy_y := _viewport_size.y * (0.22 + float(slot) * 0.11)
			var canopy_center: Vector2 = Vector2(
				_viewport_size.x * (0.34 + float(slot) * 0.14 + drift * 0.04),
				canopy_y
			)
			var canopy_points := [
				canopy_center + Vector2(-canopy_width * 0.22, _viewport_size.y * 0.01),
				canopy_center + Vector2(-canopy_width * 0.06, -_viewport_size.y * 0.03),
				canopy_center + Vector2(canopy_width * 0.12, -_viewport_size.y * 0.02),
				canopy_center + Vector2(canopy_width * 0.24, _viewport_size.y * 0.01),
			]
			_add_stroke(
				"canopy",
				_rounded_polyline(canopy_points, 12),
				9.2,
				2.4,
				Color(0.06, 0.06, 0.06, 0.92),
				0.22 - focus * 0.08,
				0.08,
				0.90,
				float(action.get("weight", 0.06))
			)
		"mist":
			_add_mist_band(
				[
					_p(_viewport_size, 0.10, 0.64),
					_p(_viewport_size, 0.28, 0.58),
					_p(_viewport_size, 0.48, 0.62),
					_p(_viewport_size, 0.82, 0.76),
				],
				18.0 + calmness * 6.0,
				Color(0.24, 0.24, 0.24, 0.05),
				0.38,
				float(action.get("weight", 0.04))
			)
		"seal":
			_set_seal(
				_p(_viewport_size, 0.17, 0.84),
				_viewport_size.y * 0.034,
				0.66,
				float(action.get("weight", 0.02))
			)

func _maybe_queue_reactive_accent(state: Dictionary) -> void:
	var brush_profile: Dictionary = _composition.get("brush_profile", {})
	if int(_runtime.get("reactive_accents_spawned", 0)) >= int(brush_profile.get("reactive_accent_cap", 1)):
		return
	if _composition_mark_count() >= int(brush_profile.get("stroke_cap", 18)):
		return
	if float(_runtime.get("accent_cooldown", 0.0)) > 0.0:
		return
	if float(_runtime.get("richness", 0.0)) >= 0.82:
		return
	var trigger := max(float(state.get("focus_rate", 0.0)), float(state.get("intensity_rate", 0.0)))
	if trigger < 0.18:
		return
	var accent_action := {}
	match int(_runtime["motif"]):
		InkMotifRef.InkMotif.ORCHID:
			accent_action = { "kind": "orchid_blossom", "slot": int(_seed_unit(_composition_seed, 510) * 8.0), "weight": 0.03 }
		InkMotifRef.InkMotif.PLUM_BRANCH:
			accent_action = { "kind": "plum_blossom", "slot": int(_seed_unit(_composition_seed, 520) * 10.0), "weight": 0.03 }
		_:
			accent_action = { "kind": "pine_canopy", "slot": int(_seed_unit(_composition_seed, 530) * 6.0), "weight": 0.03 }
	if accent_action.is_empty():
		return
	_runtime["action_queue"].push_front(accent_action)
	_runtime["target_weight"] = float(_runtime["target_weight"]) + float(accent_action.get("weight", 0.03))
	_runtime["accent_cooldown"] = 1.2
	_runtime["reactive_accents_spawned"] = int(_runtime.get("reactive_accents_spawned", 0)) + 1

func _update_reveals(delta: float, state: Dictionary) -> void:
	_update_element_list_reveal(_composition.get("strokes", []), delta, state, 1.0)
	_update_element_list_reveal(_composition.get("washes", []), delta, state, 0.82)
	_update_element_list_reveal(_composition.get("blossoms", []), delta, state, 1.06)
	_update_element_list_reveal(_composition.get("mist_bands", []), delta, state, 0.62)
	if not _composition.get("seal", {}).is_empty():
		var seal: Dictionary = _composition["seal"]
		var seal_speed := float(seal.get("growth_speed", 0.60)) * _reveal_multiplier(state, 0.84)
		seal["reveal"] = min(1.0, float(seal.get("reveal", 0.0)) + (delta * seal_speed))
		_composition["seal"] = seal

func _update_element_list_reveal(elements: Array, delta: float, state: Dictionary, base_multiplier: float) -> void:
	for index in range(elements.size()):
		var element: Dictionary = elements[index]
		var speed := float(element.get("growth_speed", 0.70)) * _reveal_multiplier(state, base_multiplier)
		element["reveal"] = min(1.0, float(element.get("reveal", 0.0)) + (delta * speed))
		elements[index] = element

func _compute_richness() -> float:
	var complete_weight := 0.0
	var target_weight := max(float(_runtime.get("target_weight", 0.0)), 0.1)
	for stroke in _composition.get("strokes", []):
		complete_weight += float(stroke.get("weight", 0.0)) * float(stroke.get("reveal", 0.0))
	for wash in _composition.get("washes", []):
		complete_weight += float(wash.get("weight", 0.0)) * float(wash.get("reveal", 0.0))
	for blossom in _composition.get("blossoms", []):
		complete_weight += float(blossom.get("weight", 0.0)) * float(blossom.get("reveal", 0.0))
	for band in _composition.get("mist_bands", []):
		complete_weight += float(band.get("weight", 0.0)) * float(band.get("reveal", 0.0))
	var seal: Dictionary = _composition.get("seal", {})
	if not seal.is_empty():
		complete_weight += float(seal.get("weight", 0.0)) * float(seal.get("reveal", 0.0))
	return clamp(complete_weight / target_weight, 0.0, 1.0)

func _has_pending_growth() -> bool:
	if not _runtime["action_queue"].is_empty():
		return true
	for stroke in _composition.get("strokes", []):
		if float(stroke.get("reveal", 0.0)) < 0.995:
			return true
	for wash in _composition.get("washes", []):
		if float(wash.get("reveal", 0.0)) < 0.995:
			return true
	for blossom in _composition.get("blossoms", []):
		if float(blossom.get("reveal", 0.0)) < 0.995:
			return true
	for band in _composition.get("mist_bands", []):
		if float(band.get("reveal", 0.0)) < 0.995:
			return true
	var seal: Dictionary = _composition.get("seal", {})
	if not seal.is_empty() and float(seal.get("reveal", 0.0)) < 0.995:
		return true
	return false

func _report_telemetry() -> void:
	SceneParamBus.report_ink_garden_telemetry(
		float(_runtime.get("richness", 0.0)),
		bool(_runtime.get("growth_active", false)),
		String(_runtime.get("motif_name", "ink_garden")),
		String(_composition.get("brush_profile", {}).get("preset_name", "breath_line"))
	)

func _compose_brush_profile(state: Dictionary, delta: float = 0.0) -> Dictionary:
	if not bool(SceneParamBus.ink_settings.get("brush_preset_engine_enabled", true)):
		return _finish_brush_profile(BRUSH_PRESETS["breath_line"].duplicate(true), state)
	var preset_name := _resolve_brush_preset_name(state, delta)
	var profile: Dictionary = BRUSH_PRESETS[preset_name].duplicate(true)
	return _finish_brush_profile(profile, state)

func _resolve_brush_preset_name(state: Dictionary, delta: float) -> String:
	var desired_preset_name := _desired_brush_preset_name(state)
	var active_preset_name := String(_runtime.get("active_preset_name", desired_preset_name))
	if active_preset_name == "":
		_runtime["active_preset_name"] = desired_preset_name
		_runtime["pending_preset_name"] = ""
		_runtime["pending_preset_seconds"] = 0.0
		return desired_preset_name
	if desired_preset_name == active_preset_name:
		_runtime["pending_preset_name"] = ""
		_runtime["pending_preset_seconds"] = 0.0
		return active_preset_name
	var desired_score := _preset_score(desired_preset_name, state)
	var active_score := _preset_score(active_preset_name, state)
	if desired_score < active_score + PRESET_SWITCH_SCORE_MARGIN:
		_runtime["pending_preset_name"] = ""
		_runtime["pending_preset_seconds"] = 0.0
		return active_preset_name
	if float(_runtime.get("preset_switch_cooldown", 0.0)) > 0.0:
		_runtime["pending_preset_name"] = ""
		_runtime["pending_preset_seconds"] = 0.0
		return active_preset_name
	if String(_runtime.get("pending_preset_name", "")) != desired_preset_name:
		_runtime["pending_preset_name"] = desired_preset_name
		_runtime["pending_preset_seconds"] = 0.0
	_runtime["pending_preset_seconds"] = float(_runtime.get("pending_preset_seconds", 0.0)) + delta
	if float(_runtime.get("pending_preset_seconds", 0.0)) < PRESET_SWITCH_MIN_DWELL_SECONDS:
		return active_preset_name
	_runtime["active_preset_name"] = desired_preset_name
	_runtime["pending_preset_name"] = ""
	_runtime["pending_preset_seconds"] = 0.0
	_runtime["preset_switch_cooldown"] = PRESET_SWITCH_COOLDOWN_SECONDS
	return desired_preset_name

func _desired_brush_preset_name(state: Dictionary) -> String:
	var calmness := float(state.get("calmness", 0.0))
	var focus := float(state.get("focus", 0.0))
	var stability := float(state.get("stability", 0.0))
	var intensity := float(state.get("intensity", 0.0))
	var drift := abs(float(state.get("drift", 0.0)))
	var preset_name := "breath_line"
	if intensity > 0.72:
		preset_name = "storm_reed"
	elif focus > 0.68 and stability > 0.58:
		preset_name = "calligrapher"
	elif calmness > 0.72 and stability > 0.58:
		preset_name = "still_pool"
	elif drift > 0.42:
		preset_name = "wander_dry"
	return preset_name

func _preset_score(preset_name: String, state: Dictionary) -> float:
	var calmness := float(state.get("calmness", 0.0))
	var focus := float(state.get("focus", 0.0))
	var stability := float(state.get("stability", 0.0))
	var intensity := float(state.get("intensity", 0.0))
	var drift := abs(float(state.get("drift", 0.0)))
	match preset_name:
		"storm_reed":
			return intensity * 0.64 + drift * 0.28 + float(state.get("intensity_rate", 0.0)) * 0.12
		"calligrapher":
			return focus * 0.50 + stability * 0.34 + calmness * 0.10 - drift * 0.06
		"still_pool":
			return calmness * 0.52 + stability * 0.28 + (1.0 - drift) * 0.16
		"wander_dry":
			return drift * 0.58 + (1.0 - stability) * 0.18 + intensity * 0.16
		_:
			return calmness * 0.20 + focus * 0.20 + stability * 0.20 + (1.0 - drift) * 0.20 + (1.0 - intensity) * 0.20

func _finish_brush_profile(profile: Dictionary, state: Dictionary) -> Dictionary:
	var calmness := float(state.get("calmness", 0.0))
	var focus := float(state.get("focus", 0.0))
	var stability := float(state.get("stability", 0.0))
	var intensity := float(state.get("intensity", 0.0))
	var drift := abs(float(state.get("drift", 0.0)))
	profile["taper"] = clamp(float(profile["taper"]) + calmness * 0.08 - intensity * 0.05, 0.72, 1.32)
	profile["bleed"] = clamp(float(profile["bleed"]) + calmness * 0.18 - focus * 0.08, 0.68, 1.38)
	profile["pooling"] = clamp(float(profile["pooling"]) + stability * 0.12 + calmness * 0.10 - drift * 0.08, 0.60, 1.48)
	profile["jitter"] = clamp(float(profile["jitter"]) + drift * 0.26 + intensity * 0.12 - stability * 0.10, 0.06, 0.72)
	profile["core_break"] = clamp(float(profile["core_break"]) + drift * 0.06 + intensity * 0.04 - focus * 0.04, -0.10, 0.18)
	profile["opacity"] = clamp(float(profile["opacity"]) + focus * 0.08 + intensity * 0.04 - calmness * 0.03, 0.82, 1.16)
	profile["effect_mix"] = _effect_mix(state)
	return profile

func _effect_mix(state: Dictionary) -> float:
	var settings: Dictionary = SceneParamBus.ink_settings
	if not bool(settings.get("magical_fx_enabled", true)):
		return 0.0
	if not bool(settings.get("enhanced_fx_enabled", false)):
		return 0.0
	var threshold := float(settings.get("effect_trigger_threshold", 0.62))
	var strength := float(settings.get("effect_strength", 0.48))
	var trigger_signal := max(float(state.get("focus_rate", 0.0)), float(state.get("intensity_rate", 0.0))) + (float(state.get("intensity", 0.0)) * 0.18)
	return clamp(((trigger_signal - threshold) / max(1.0 - threshold, 0.001)) * strength, 0.0, 1.0)

func _composition_mark_count() -> int:
	return _composition.get("strokes", []).size() + _composition.get("washes", []).size() + _composition.get("blossoms", []).size() + _composition.get("mist_bands", []).size()

func _reveal_multiplier(state: Dictionary, base_multiplier: float) -> float:
	return base_multiplier + (float(state.get("intensity", 0.0)) * 0.34) + (float(state.get("focus", 0.0)) * 0.10) - (float(state.get("calmness", 0.0)) * 0.08)

func _spawn_rate(state: Dictionary) -> float:
	return 1.0 + (float(state.get("intensity", 0.0)) * 0.90) + (float(state.get("focus", 0.0)) * 0.16) - (float(state.get("calmness", 0.0)) * 0.18)

func _spawn_delay(state: Dictionary) -> float:
	return clamp(0.52 + (float(state.get("calmness", 0.0)) * 0.26) - (float(state.get("intensity", 0.0)) * 0.18), 0.22, 0.84)

func _motif_for_seed(seed: int) -> int:
	match max(seed, 0) % 3:
		0:
			return InkMotifRef.InkMotif.ORCHID
		1:
			return InkMotifRef.InkMotif.PLUM_BRANCH
		_:
			return InkMotifRef.InkMotif.PINE_ON_ROCK

func _motif_name(motif: int) -> String:
	match motif:
		InkMotifRef.InkMotif.ORCHID:
			return "orchid"
		InkMotifRef.InkMotif.PLUM_BRANCH:
			return "plum_branch"
		_:
			return "pine_on_rock"

func _background_washes_for(motif: int, layout: Dictionary, size: Vector2, seed: int) -> Array:
	match motif:
		InkMotifRef.InkMotif.ORCHID:
			return [
				_background_wash(layout["base"] + Vector2(size.x * 0.08, -size.y * 0.12), size.y * 0.12, Color(0.22, 0.20, 0.18, 0.06), 4, 4.0, 3.0, 0.18),
				_background_wash(layout["flower_center"], size.y * 0.10, Color(0.24, 0.24, 0.24, 0.05), 3, 6.0, 4.0, 0.22),
			]
		InkMotifRef.InkMotif.PLUM_BRANCH:
			return [
				_background_wash(layout["start"].lerp(layout["end"], 0.5), size.y * 0.13, Color(0.20, 0.20, 0.20, 0.04), 3, 5.0, 5.0, 0.16),
			]
		_:
			return [
				_background_wash(_p(size, 0.74, 0.56), size.y * 0.20, Color(0.22, 0.22, 0.22, 0.06), 4, 8.0, 8.0, 0.16),
				_background_wash(_p(size, 0.30, 0.58), size.y * 0.24, Color(0.24, 0.24, 0.24, 0.04), 3, 10.0, 6.0, 0.14),
			]

func _background_wash(center: Vector2, radius: float, color: Color, rings: int, drift_x: float, drift_y: float, drift_speed: float) -> Dictionary:
	return {
		"center": center,
		"radius": radius,
		"color": color,
		"rings": rings,
		"drift_x": drift_x,
		"drift_y": drift_y,
		"drift_speed": drift_speed,
		"reveal": 1.0,
	}

func _add_stroke(role: String, points: Array, width_start: float, width_end: float, color: Color, dryness: float, pooling: float, growth_speed: float, weight: float, primary: bool = false) -> void:
	var strokes: Array = _composition["strokes"]
	strokes.append({
		"id": int(_runtime["next_element_id"]),
		"role": role,
		"points": points,
		"width_start": width_start,
		"width_end": width_end,
		"color": color,
		"dryness": dryness,
		"pooling": pooling,
		"reveal": 0.0,
		"growth_speed": growth_speed,
		"weight": weight,
		"primary": primary,
	})
	_runtime["next_element_id"] = int(_runtime["next_element_id"]) + 1

func _add_wash(center: Vector2, radius: float, color: Color, rings: int, growth_speed: float, weight: float) -> void:
	var washes: Array = _composition["washes"]
	washes.append({
		"center": center,
		"radius": radius,
		"color": color,
		"rings": rings,
		"reveal": 0.0,
		"growth_speed": growth_speed,
		"weight": weight,
	})

func _add_blossom(center: Vector2, radius: float, petals: int, red: bool, growth_speed: float, weight: float) -> void:
	var blossoms: Array = _composition["blossoms"]
	blossoms.append({
		"center": center,
		"radius": radius,
		"petals": petals,
		"red": red,
		"reveal": 0.0,
		"growth_speed": growth_speed,
		"weight": weight,
	})

func _add_mist_band(points: Array, width: float, color: Color, growth_speed: float, weight: float) -> void:
	var bands: Array = _composition["mist_bands"]
	bands.append({
		"points": points,
		"width": width,
		"color": color,
		"reveal": 0.0,
		"growth_speed": growth_speed,
		"weight": weight,
	})

func _set_seal(position: Vector2, size: float, growth_speed: float, weight: float) -> void:
	_composition["seal"] = {
		"position": position,
		"size": size,
		"reveal": 0.0,
		"growth_speed": growth_speed,
		"weight": weight,
	}

func _should_signal_visual_ready() -> bool:
	return not _composition.is_empty() and _viewport_size.x > 1.0 and _viewport_size.y > 1.0

func _draw_soft_cloud(center: Vector2, radius: float, color: Color, rings: int) -> void:
	for ring in range(rings):
		var t := float(ring) / float(max(rings - 1, 1))
		var offset := Vector2((t - 0.5) * radius * 0.34, sin((t + 1.0) * 1.6) * radius * 0.10)
		var ring_alpha := color.a * (1.0 - t * 0.72)
		draw_circle(center + offset, radius * (0.56 + t * 0.36), Color(color.r, color.g, color.b, ring_alpha))

func _seed_unit(seed: int, salt: int) -> float:
	var value: int = int(abs((seed * 92821) + (salt * 68917) + 37) % 10000)
	return float(value) / 10000.0

func _p(size: Vector2, x: float, y: float) -> Vector2:
	return Vector2(size.x * x, size.y * y)

func _cubic_points(p0: Vector2, p1: Vector2, p2: Vector2, p3: Vector2, samples: int) -> Array:
	var points: Array = []
	for index in range(samples):
		var t := float(index) / float(max(samples - 1, 1))
		var inv := 1.0 - t
		var position := (inv * inv * inv * p0) + (3.0 * inv * inv * t * p1) + (3.0 * inv * t * t * p2) + (t * t * t * p3)
		points.append(position)
	return points

func _concat_points(parts: Array) -> Array:
	var combined: Array = []
	for part in parts:
		for point_index in range(part.size()):
			if combined.is_empty() or point_index > 0:
				combined.append(part[point_index])
	return combined

func _rounded_polyline(points: Array, samples_per_segment: int) -> Array:
	if points.size() <= 2:
		return points.duplicate()
	var smoothed: Array = []
	for index in range(points.size() - 1):
		var start: Vector2 = points[index]
		var end: Vector2 = points[index + 1]
		var previous: Vector2 = points[max(index - 1, 0)]
		var following: Vector2 = points[min(index + 2, points.size() - 1)]
		var control_a := start.lerp(end, 0.28) + (start - previous) * 0.10
		var control_b := end.lerp(start, 0.28) + (end - following) * 0.10
		var segment := _cubic_points(start, control_a, control_b, end, samples_per_segment)
		for segment_index in range(segment.size()):
			if smoothed.is_empty() or segment_index > 0:
				smoothed.append(segment[segment_index])
	return smoothed
