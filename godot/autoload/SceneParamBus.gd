extends Node

var scene_id: String = "ink_garden"
var running: bool = false
var paused: bool = true
var version: int = 0
var composition_seed: int = 0
var _bridge = null
var _visual_ready_scene_id := ""
var _visual_ready_version := -1
var _pending_scene_ready := ""
var _pending_visual_ready := ""
var _pending_visual_ready_version := -1
var _pending_error := ""
var _pending_ink_garden_telemetry := {}
var ink_settings := {
	"enhanced_fx_enabled": false,
	"ghost_trails_enabled": true,
	"gold_dust_enabled": true,
	"effect_trigger_threshold": 0.62,
	"effect_strength": 0.48,
	"brush_preset_engine_enabled": true,
	"magical_fx_enabled": true,
}
var scene_state := {
	"calmness": 0.0,
	"focus": 0.0,
	"stability": 0.0,
	"intensity": 0.0,
	"drift": 0.0,
	"progress": 0.0,
	"calmness_rate": 0.0,
	"focus_rate": 0.0,
	"intensity_rate": 0.0,
}

func _ready() -> void:
	_bridge = _resolve_bridge()
	set_process(true)

func _process(_delta: float) -> void:
	_flush_pending_bridge_messages()

func apply_payload(payload: Dictionary) -> void:
	var previous_scene_id := scene_id
	var previous_version := version
	scene_id = payload.get("scene_id", scene_id)
	running = payload.get("running", running)
	paused = payload.get("paused", paused)
	version = payload.get("version", version)
	composition_seed = int(payload.get("composition_seed", composition_seed))
	if scene_id != previous_scene_id or version != previous_version:
		_visual_ready_scene_id = ""
		_visual_ready_version = -1
		_pending_scene_ready = ""
		_pending_visual_ready = ""
		_pending_visual_ready_version = -1
		_pending_ink_garden_telemetry = {}
	scene_state["calmness"] = payload.get("calmness", scene_state["calmness"])
	scene_state["focus"] = payload.get("focus", scene_state["focus"])
	scene_state["stability"] = payload.get("stability", scene_state["stability"])
	scene_state["intensity"] = payload.get("intensity", scene_state["intensity"])
	scene_state["drift"] = payload.get("drift", scene_state["drift"])
	scene_state["progress"] = payload.get("progress", scene_state["progress"])
	scene_state["calmness_rate"] = payload.get("calmness_rate", scene_state["calmness_rate"])
	scene_state["focus_rate"] = payload.get("focus_rate", scene_state["focus_rate"])
	scene_state["intensity_rate"] = payload.get("intensity_rate", scene_state["intensity_rate"])
	ink_settings["enhanced_fx_enabled"] = payload.get("ink_enhanced_fx_enabled", ink_settings["enhanced_fx_enabled"])
	ink_settings["ghost_trails_enabled"] = payload.get("ink_ghost_trails_enabled", ink_settings["ghost_trails_enabled"])
	ink_settings["gold_dust_enabled"] = payload.get("ink_gold_dust_enabled", ink_settings["gold_dust_enabled"])
	ink_settings["effect_trigger_threshold"] = payload.get("ink_effect_trigger_threshold", ink_settings["effect_trigger_threshold"])
	ink_settings["effect_strength"] = payload.get("ink_effect_strength", ink_settings["effect_strength"])
	ink_settings["brush_preset_engine_enabled"] = payload.get("ink_brush_preset_engine_enabled", ink_settings["brush_preset_engine_enabled"])
	ink_settings["magical_fx_enabled"] = payload.get("ink_magical_fx_enabled", ink_settings["magical_fx_enabled"])

func notify_scene_ready(scene_name: String) -> void:
	var bridge = _resolve_bridge()
	if bridge != null and bridge.has_method("notifySceneReady"):
		bridge.notifySceneReady(scene_name)
		_pending_scene_ready = ""
	else:
		_pending_scene_ready = scene_name

func notify_visual_ready(scene_name: String) -> void:
	if _visual_ready_scene_id == scene_name and _visual_ready_version == version:
		return
	_visual_ready_scene_id = scene_name
	_visual_ready_version = version
	var bridge = _resolve_bridge()
	if bridge != null and bridge.has_method("notifySceneVisualReady"):
		bridge.notifySceneVisualReady(scene_name)
		_pending_visual_ready = ""
		_pending_visual_ready_version = -1
	else:
		_pending_visual_ready = scene_name
		_pending_visual_ready_version = version

func report_scene_error(message: String) -> void:
	var bridge = _resolve_bridge()
	if bridge != null and bridge.has_method("reportSceneError"):
		bridge.reportSceneError(message)
		_pending_error = ""
	else:
		_pending_error = message

func report_ink_garden_telemetry(richness: float, growth_active: bool, motif_name: String, brush_preset_name: String) -> void:
	var payload := {
		"richness": clamp(richness, 0.0, 1.0),
		"growth_active": growth_active,
		"motif_name": motif_name,
		"brush_preset_name": brush_preset_name,
		"version": version,
	}
	var bridge = _resolve_bridge()
	if bridge != null and bridge.has_method("reportInkGardenTelemetry"):
		bridge.reportInkGardenTelemetry(
			float(payload["richness"]),
			bool(payload["growth_active"]),
			String(payload["motif_name"]),
			String(payload["brush_preset_name"]),
			int(payload["version"])
		)
		_pending_ink_garden_telemetry = {}
	else:
		_pending_ink_garden_telemetry = payload

func current_payload() -> Dictionary:
	return {
		"scene_id": scene_id,
		"running": running,
		"paused": paused,
		"version": version,
		"composition_seed": composition_seed,
		"calmness": scene_state["calmness"],
		"focus": scene_state["focus"],
		"stability": scene_state["stability"],
		"intensity": scene_state["intensity"],
		"drift": scene_state["drift"],
		"progress": scene_state["progress"],
		"calmness_rate": scene_state["calmness_rate"],
		"focus_rate": scene_state["focus_rate"],
		"intensity_rate": scene_state["intensity_rate"],
		"ink_enhanced_fx_enabled": ink_settings["enhanced_fx_enabled"],
		"ink_ghost_trails_enabled": ink_settings["ghost_trails_enabled"],
		"ink_gold_dust_enabled": ink_settings["gold_dust_enabled"],
		"ink_effect_trigger_threshold": ink_settings["effect_trigger_threshold"],
		"ink_effect_strength": ink_settings["effect_strength"],
		"ink_brush_preset_engine_enabled": ink_settings["brush_preset_engine_enabled"],
		"ink_magical_fx_enabled": ink_settings["magical_fx_enabled"],
	}

func _resolve_bridge():
	if _bridge != null:
		return _bridge
	if Engine.has_singleton("SamathaBridge"):
		_bridge = Engine.get_singleton("SamathaBridge")
	return _bridge

func _flush_pending_bridge_messages() -> void:
	var bridge = _resolve_bridge()
	if bridge == null:
		return
	if _pending_scene_ready != "" and bridge.has_method("notifySceneReady"):
		bridge.notifySceneReady(_pending_scene_ready)
		_pending_scene_ready = ""
	if _pending_visual_ready != "" and _pending_visual_ready_version == version and bridge.has_method("notifySceneVisualReady"):
		bridge.notifySceneVisualReady(_pending_visual_ready)
		_pending_visual_ready = ""
		_pending_visual_ready_version = -1
	if _pending_error != "" and bridge.has_method("reportSceneError"):
		bridge.reportSceneError(_pending_error)
		_pending_error = ""
	if not _pending_ink_garden_telemetry.is_empty() and bridge.has_method("reportInkGardenTelemetry"):
		bridge.reportInkGardenTelemetry(
			float(_pending_ink_garden_telemetry.get("richness", 0.0)),
			bool(_pending_ink_garden_telemetry.get("growth_active", false)),
			String(_pending_ink_garden_telemetry.get("motif_name", "")),
			String(_pending_ink_garden_telemetry.get("brush_preset_name", "")),
			int(_pending_ink_garden_telemetry.get("version", version))
		)
		_pending_ink_garden_telemetry = {}
