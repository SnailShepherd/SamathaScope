extends Node2D

var _composition := {}
var _state := {}
var _progress := 0.0
var _grain_points: Array = []
var _fiber_lines: Array = []
var _size := Vector2.ZERO
var _pulse := 0.0

func reset_composition() -> void:
	_progress = 0.0
	_pulse = 0.0
	queue_redraw()

func apply_state(composition: Dictionary, state: Dictionary, composition_progress: float, delta: float) -> void:
	_composition = composition
	_state = state.duplicate()
	_progress = composition_progress
	_pulse = max(_pulse - delta * 0.9, 0.0)
	if state.get("intensity_rate", 0.0) > 0.08 or state.get("focus_rate", 0.0) > 0.08:
		_pulse = 1.0
	var current_size := get_viewport_rect().size
	if current_size != _size:
		_size = current_size
		_build_paper_noise()
	queue_redraw()

func _draw() -> void:
	var size := get_viewport_rect().size
	if size.x <= 1.0 or size.y <= 1.0:
		return

	for point in _grain_points:
		draw_circle(point, 0.9, Color(0.16, 0.15, 0.13, 0.018))

	for fiber in _fiber_lines:
		var fiber_from: Vector2 = fiber["from"]
		var fiber_to: Vector2 = fiber["to"]
		var fiber_width: float = float(fiber["width"])
		draw_line(fiber_from, fiber_to, Color(0.18, 0.16, 0.14, 0.022), fiber_width)

	for band in _composition.get("mist_bands", []):
		var reveal := float(band.get("reveal", 0.0))
		if reveal <= 0.01:
			continue
		var points: Array = band["points"]
		for index in range(points.size() - 1):
			draw_line(points[index], points[index + 1], Color(band["color"].r, band["color"].g, band["color"].b, band["color"].a * reveal), band["width"])

	var vignette_alpha: float = 0.05 - float(_state.get("calmness", 0.0)) * 0.02
	draw_rect(Rect2(Vector2.ZERO, size), Color(0.10, 0.09, 0.08, vignette_alpha), false, 22.0)

	var pulse_alpha: float = _pulse * 0.018
	if pulse_alpha > 0.001:
		draw_circle(size * Vector2(0.56, 0.40), size.y * 0.28, Color(0.18, 0.16, 0.14, pulse_alpha))

func _build_paper_noise() -> void:
	_grain_points.clear()
	_fiber_lines.clear()
	if _size.x <= 1.0 or _size.y <= 1.0:
		return
	for index in range(120):
		var x := _size.x * (0.04 + fmod(float(index) * 0.173, 0.92))
		var y := _size.y * (0.06 + fmod(float(index) * 0.117, 0.88))
		_grain_points.append(Vector2(x, y))
	for fiber_index in range(16):
		var start := Vector2(
			_size.x * (0.10 + fmod(float(fiber_index) * 0.201, 0.72)),
			_size.y * (0.12 + fmod(float(fiber_index) * 0.153, 0.74))
		)
		var end := start + Vector2(_size.x * 0.05, _size.y * (0.004 + fmod(float(fiber_index) * 0.017, 0.018)))
		_fiber_lines.append({
			"from": start,
			"to": end,
			"width": 0.8 + float(fiber_index % 3) * 0.2,
		})
