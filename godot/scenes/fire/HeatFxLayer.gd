extends Node2D

var _state := {}

func apply_state(state: Dictionary, delta: float) -> void:
	_state = state.duplicate()
	queue_redraw()

func _draw() -> void:
	var size := get_viewport_rect().size
	var intensity: float = _state.get("intensity", 0.0)
	var calmness: float = _state.get("calmness", 0.0)
	draw_circle(size * Vector2(0.50, 0.72), size.y * (0.12 + intensity * 0.04), Color(1.0, 0.58 + calmness * 0.12, 0.24, 0.12 + intensity * 0.12))
	draw_rect(Rect2(Vector2.ZERO, size), Color(0.08, 0.04, 0.01, 0.05), false, 12.0)
