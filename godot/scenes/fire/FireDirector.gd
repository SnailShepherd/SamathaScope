extends Node2D

@onready var flame_core := $FlameCore
@onready var ember_system := $EmberSystem
@onready var smoke_system := $SmokeSystem
@onready var heat_fx_layer := $HeatFxLayer

var _visual_ready_sent := false
var _scene_version := -1

func _process(delta: float) -> void:
	if SceneParamBus.version != _scene_version:
		_scene_version = SceneParamBus.version
		_visual_ready_sent = false

	var state := SceneParamBus.scene_state
	var active := SceneParamBus.running and not SceneParamBus.paused
	flame_core.apply_state(state, active, delta)
	ember_system.apply_state(state, active, delta, flame_core.core_anchor())
	smoke_system.apply_state(state, active, delta, flame_core.core_anchor())
	heat_fx_layer.apply_state(state, delta)
	if active and not _visual_ready_sent:
		_visual_ready_sent = true
		call_deferred("_notify_visual_ready")
	queue_redraw()

func _draw() -> void:
	var size := get_viewport_rect().size
	var calmness: float = SceneParamBus.scene_state["calmness"]
	var progress: float = SceneParamBus.scene_state["progress"]
	draw_rect(Rect2(Vector2.ZERO, size), Color(0.04, 0.05, 0.09), true)
	draw_circle(size * Vector2(0.50, 0.78), size.y * (0.18 + progress * 0.05), Color(0.38, 0.18 + calmness * 0.12, 0.07, 0.22))
	draw_rect(Rect2(0.0, size.y * 0.84, size.x, size.y * 0.18), Color(0.08, 0.06, 0.05), true)

func _notify_visual_ready() -> void:
	SceneParamBus.notify_visual_ready("fire")
