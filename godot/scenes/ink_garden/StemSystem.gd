extends Node2D

var _composition := {}
var _state := {}
var _progress := 0.0
var _visible_strokes: Array = []
var _tip_positions: Array = []
var _primary_visible := false

func reset_composition() -> void:
	_composition = {}
	_visible_strokes.clear()
	_tip_positions.clear()
	_primary_visible = false
	queue_redraw()

func apply_state(composition: Dictionary, state: Dictionary, composition_progress: float) -> void:
	_composition = composition
	_state = state.duplicate()
	_progress = composition_progress
	_visible_strokes.clear()
	_tip_positions.clear()
	_primary_visible = false

	for stroke in _composition.get("strokes", []):
		var reveal := float(stroke.get("reveal", 0.0))
		if reveal <= 0.01:
			continue
		var visible_points := _slice_points(stroke["points"], reveal)
		if visible_points.size() < 2:
			continue
		_visible_strokes.append({
			"stroke": stroke,
			"reveal": reveal,
			"points": visible_points,
		})
		if bool(stroke.get("primary", false)) and reveal >= 0.24:
			_primary_visible = true
		if reveal >= 0.30:
			_tip_positions.append(visible_points[visible_points.size() - 1])

	queue_redraw()

func has_visible_primary_layer() -> bool:
	return _primary_visible

func tip_positions() -> Array:
	return _tip_positions.duplicate()

func _draw() -> void:
	if _visible_strokes.is_empty():
		return

	for entry in _visible_strokes:
		_draw_stroke(entry["stroke"], entry["points"], entry["reveal"])

func _draw_stroke(stroke: Dictionary, points: Array, reveal: float) -> void:
	var calmness: float = float(_state.get("calmness", 0.0))
	var focus: float = float(_state.get("focus", 0.0))
	var stability: float = float(_state.get("stability", 0.0))
	var intensity: float = float(_state.get("intensity", 0.0))
	var width_start: float = float(stroke["width_start"])
	var width_end: float = float(stroke["width_end"])
	var stroke_color: Color = stroke["color"]
	var dryness: float = stroke["dryness"]
	var pooling: float = stroke["pooling"]
	var count: int = points.size()
	var bleed_alpha: float = 0.10 + calmness * 0.05 + intensity * 0.05
	var core_alpha: float = min(stroke_color.a, 0.80 + intensity * 0.12)
	var highlight_alpha: float = 0.04 + focus * 0.05

	for index in range(count - 1):
		var current: Vector2 = points[index]
		var next: Vector2 = points[index + 1]
		var t: float = float(index) / float(max(count - 2, 1))
		var next_t: float = float(index + 1) / float(max(count - 2, 1))
		var base_width: float = lerp(width_start, width_end, pow(t, 0.82))
		var next_width: float = lerp(width_start, width_end, pow(next_t, 0.82))
		var segment_width: float = (base_width + next_width) * 0.5
		var noise: float = _texture_noise(int(stroke["id"]), index)
		var edge_variation: float = 1.0 + (noise - 0.5) * (0.18 - focus * 0.08)
		var bleed_width: float = max(segment_width * (1.26 + calmness * 0.10), 1.6)
		var core_width: float = max(segment_width * edge_variation, 1.0)
		var bleed_color: Color = Color(0.14, 0.13, 0.12, bleed_alpha * reveal)
		var core_color: Color = Color(stroke_color.r, stroke_color.g, stroke_color.b, core_alpha * reveal)
		draw_line(current, next, bleed_color, bleed_width)
		if not _should_break_core(dryness, focus, stability, noise):
			draw_line(current, next, core_color, core_width)
			if index % 2 == 0:
				draw_circle(current, max(core_width * 0.42, 0.8), core_color)
				draw_circle(current, max(core_width * 0.18, 0.6), Color(0.88, 0.86, 0.82, highlight_alpha * reveal))

		if index > 0 and index < count - 2:
			var previous: Vector2 = points[index - 1]
			var turn_strength: float = _turn_strength(previous, current, next)
			if turn_strength > 0.16:
				var pool_radius: float = segment_width * (0.30 + pooling * 0.55 + intensity * 0.10)
				draw_circle(current, pool_radius, Color(0.05, 0.05, 0.05, (0.06 + pooling * 0.08) * reveal))

	var tip: Vector2 = points[count - 1]
	var tip_radius: float = max(width_end * (0.55 + intensity * 0.10), 1.0)
	draw_circle(tip, tip_radius, Color(stroke_color.r, stroke_color.g, stroke_color.b, core_alpha * reveal))

func _slice_points(points: Array, reveal: float) -> Array:
	if points.size() <= 1:
		return points.duplicate()
	if reveal >= 0.999:
		return points.duplicate()
	var scaled := reveal * float(points.size() - 1)
	var whole := int(floor(scaled))
	var fraction := scaled - float(whole)
	var sliced: Array = []
	for index in range(whole + 1):
		sliced.append(points[index])
	if whole < points.size() - 1:
		var a: Vector2 = points[whole]
		var b: Vector2 = points[whole + 1]
		sliced.append(a.lerp(b, fraction))
	return sliced

func _should_break_core(dryness: float, focus: float, stability: float, noise: float) -> bool:
	var threshold: float = 0.92 - dryness * 0.24 + focus * 0.10 + stability * 0.06
	return noise > threshold

func _turn_strength(previous: Vector2, current: Vector2, next: Vector2) -> float:
	var a: Vector2 = (current - previous).normalized()
	var b: Vector2 = (next - current).normalized()
	return clamp(1.0 - a.dot(b), 0.0, 1.0)

func _texture_noise(seed: int, index: int) -> float:
	var value := float(((seed * 92821) + (index * 68917) + 37) & 1023) / 1023.0
	return value
