extends Node2D

var _time := 0.0
var _state := {}

func apply_state(state: Dictionary, active: bool, delta: float) -> void:
	_state = state.duplicate()
	if active:
		_time += delta
	queue_redraw()

func core_anchor() -> Vector2:
	return Vector2(640.0 + _state.get("drift", 0.0) * 90.0, 540.0)

func _draw() -> void:
	var calmness: float = _state.get("calmness", 0.0)
	var focus: float = _state.get("focus", 0.0)
	var stability: float = _state.get("stability", 0.0)
	var intensity: float = _state.get("intensity", 0.0)
	var drift: float = _state.get("drift", 0.0)
	var height: float = lerp(180.0, 320.0, intensity)
	var width: float = lerp(120.0, 82.0, focus)
	var chaos: float = lerp(26.0, 8.0, stability)
	var anchor: Vector2 = core_anchor()
	var points: PackedVector2Array = PackedVector2Array()
	points.append(anchor + Vector2(-width * 0.55, 0.0))
	for index in range(0, 14):
		var t := float(index) / 13.0
		var wave: float = sin((_time * (1.8 + intensity * 2.6)) + t * 7.0) * chaos
		points.append(anchor + Vector2(-width * (0.55 - t * 0.45) + wave + drift * 26.0, -height * t))
	for index in range(13, -1, -1):
		var t := float(index) / 13.0
		var wave: float = sin((_time * (1.5 + intensity * 2.1)) + t * 6.1 + 1.4) * chaos
		points.append(anchor + Vector2(width * (0.55 - t * 0.45) + wave + drift * 26.0, -height * t))
	points.append(anchor + Vector2(width * 0.55, 0.0))
	draw_colored_polygon(points, Color(1.0, 0.54 + calmness * 0.10, 0.20, 0.78))
	draw_circle(anchor + Vector2(drift * 18.0, -height * 0.42), 44.0 + intensity * 32.0, Color(1.0, 0.86, 0.46, 0.24 + intensity * 0.18))
