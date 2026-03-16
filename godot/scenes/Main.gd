extends Node2D

const INK_GARDEN_SCENE_PATH := "res://scenes/ink_garden/InkGarden.tscn"
const FIRE_SCENE_PATH := "res://scenes/fire/Fire.tscn"
const PAYLOAD_APPLY_INTERVAL_SECONDS := 1.0 / 30.0

@onready var scene_container: Node = $SceneContainer

var _bridge = null
var _loaded_scene_id := ""
var _loaded_scene_version := -1
var _reported_missing_bridge := false
var _payload_apply_accumulator := 0.0
var _last_payload := {}

func _ready() -> void:
	if Engine.has_singleton("SamathaBridge"):
		_bridge = Engine.get_singleton("SamathaBridge")
	_last_payload = _read_payload()
	_apply_payload(_last_payload)

func _process(delta: float) -> void:
	if _bridge == null and _allow_preview_driver():
		ScenePreviewDriver.advance(delta)
	_payload_apply_accumulator += delta
	var payload := _read_payload()
	if payload.is_empty():
		return
	var next_scene_id: String = payload.get("scene_id", "ink_garden")
	var next_version: int = int(payload.get("version", 0))
	if next_scene_id != _loaded_scene_id or next_version != _loaded_scene_version:
		_last_payload = payload
		_payload_apply_accumulator = 0.0
		_apply_payload(payload)
		return
	_last_payload = payload
	if _payload_apply_accumulator < PAYLOAD_APPLY_INTERVAL_SECONDS:
		return
	_payload_apply_accumulator = 0.0
	_apply_payload(_last_payload)

func _read_payload() -> Dictionary:
	if _bridge != null:
		var payload_json = _bridge.getPayloadJson()
		if payload_json.is_empty():
			return SceneParamBus.current_payload()
		var parsed = JSON.parse_string(payload_json)
		if typeof(parsed) == TYPE_DICTIONARY:
			return parsed
		if _bridge.has_method("reportSceneError"):
			_bridge.reportSceneError("Invalid Godot payload JSON")
		return SceneParamBus.current_payload()
	if _allow_preview_driver():
		return ScenePreviewDriver.current_payload()
	if not _reported_missing_bridge:
		_reported_missing_bridge = true
		SceneParamBus.report_scene_error("Godot bridge is unavailable in the embedded runtime.")
	return SceneParamBus.current_payload()

func _apply_payload(payload: Dictionary) -> void:
	SceneParamBus.apply_payload(payload)
	var next_scene_id: String = payload.get("scene_id", "ink_garden")
	var next_version: int = int(payload.get("version", 0))
	if next_scene_id != _loaded_scene_id or next_version != _loaded_scene_version:
		_switch_scene(next_scene_id, next_version)

func _switch_scene(scene_id: String, scene_version: int) -> void:
	for child in scene_container.get_children():
		child.queue_free()
	var scene_path := INK_GARDEN_SCENE_PATH
	if scene_id == "fire":
		scene_path = FIRE_SCENE_PATH
	var scene_resource = load(scene_path)
	if scene_resource == null:
		SceneParamBus.report_scene_error("Failed to load %s" % scene_path)
		return
	if not (scene_resource is PackedScene):
		SceneParamBus.report_scene_error("%s is not a PackedScene" % scene_path)
		return
	var instance = scene_resource.instantiate()
	scene_container.add_child(instance)
	_loaded_scene_id = scene_id
	_loaded_scene_version = scene_version
	SceneParamBus.notify_scene_ready(scene_id)
	call_deferred("_notify_visual_ready_deferred", scene_id, scene_version)

func _notify_visual_ready_deferred(scene_id: String, scene_version: int) -> void:
	if _loaded_scene_id != scene_id:
		return
	if _loaded_scene_version != scene_version:
		return
	SceneParamBus.notify_visual_ready(scene_id)

func _allow_preview_driver() -> bool:
	return Engine.is_editor_hint() or OS.has_feature("editor")
