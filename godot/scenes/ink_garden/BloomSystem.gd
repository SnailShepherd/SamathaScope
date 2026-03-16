extends Node2D

var _composition := {}
var _state := {}
var _progress := 0.0
var _tips: Array = []
var _delta := 0.0

func reset_composition() -> void:
	_composition = {}
	_tips.clear()
	_progress = 0.0
	queue_redraw()

func apply_state(composition: Dictionary, state: Dictionary, composition_progress: float, tips: Array, delta: float) -> void:
	_composition = composition
	_state = state.duplicate()
	_progress = composition_progress
	_tips = tips.duplicate()
	_delta = delta
	queue_redraw()

func _draw() -> void:
	if _composition.is_empty():
		return

	for wash in _composition.get("washes", []):
		var reveal := float(wash.get("reveal", 0.0))
		if reveal <= 0.01:
			continue
		_draw_wash(wash, reveal)

	for blossom in _composition.get("blossoms", []):
		var reveal := float(blossom.get("reveal", 0.0))
		if reveal <= 0.01:
			continue
		_draw_blossom(blossom, reveal)

	_draw_tip_pools()
	_draw_seal()

func _draw_wash(wash: Dictionary, reveal: float) -> void:
	var calmness: float = float(_state.get("calmness", 0.0))
	var intensity: float = float(_state.get("intensity", 0.0))
	var radius: float = wash["radius"] * (0.92 + calmness * 0.08 + reveal * 0.08)
	var color: Color = wash["color"]
	var ring_count: int = wash["rings"]
	for ring in range(ring_count):
		var t: float = float(ring) / float(max(ring_count - 1, 1))
		var offset: Vector2 = Vector2((t - 0.5) * radius * 0.20, sin((t + 1.0) * 1.8) * radius * 0.08)
		var alpha: float = color.a * reveal * (1.0 - t * 0.74) * (1.0 + intensity * 0.20)
		draw_circle(
			wash["center"] + offset,
			radius * (0.42 + t * 0.34),
			Color(color.r, color.g, color.b, alpha)
		)

func _draw_blossom(blossom: Dictionary, reveal: float) -> void:
	var red: bool = bool(blossom["red"])
	var radius: float = blossom["radius"] * (0.84 + reveal * 0.24)
	var petal_count: int = blossom["petals"]
	var center: Vector2 = blossom["center"]
	var petal_color: Color = Color(0.86, 0.20, 0.22, 0.84 * reveal) if red else Color(0.18, 0.18, 0.18, 0.30 * reveal)
	var shadow_color: Color = Color(0.12, 0.10, 0.10, 0.08 * reveal)
	for petal_index in range(petal_count):
		var angle: float = TAU * float(petal_index) / float(petal_count)
		var offset: Vector2 = Vector2(cos(angle), sin(angle)) * radius * 0.48
		draw_circle(center + offset + Vector2(1.0, 1.0), radius * 0.42, shadow_color)
		draw_circle(center + offset, radius * 0.36, petal_color)
	draw_circle(center, radius * 0.14, Color(0.18, 0.10, 0.08, 0.90 * reveal))
	if red:
		draw_circle(center, radius * 0.08, Color(0.96, 0.80, 0.40, 0.72 * reveal))

func _draw_tip_pools() -> void:
	var intensity: float = float(_state.get("intensity", 0.0))
	var brush_profile: Dictionary = _composition.get("brush_profile", {})
	var pooling_bias: float = float(brush_profile.get("pooling", 1.0))
	if _tips.is_empty():
		return
	for tip_index in range(min(_tips.size(), 4)):
		var tip: Vector2 = _tips[tip_index]
		var droplet_radius: float = (2.0 + intensity * 1.8 + float(tip_index % 2) * 0.5) * clamp(0.82 + pooling_bias * 0.30, 0.80, 1.36)
		draw_circle(tip + Vector2(0.0, 1.8 + tip_index), droplet_radius, Color(0.07, 0.07, 0.07, 0.10))
		draw_circle(tip + Vector2(0.0, 0.8 + tip_index * 0.4), droplet_radius * 0.52, Color(0.12, 0.11, 0.10, 0.06))

func _draw_seal() -> void:
	var seal: Dictionary = _composition.get("seal", {})
	if seal.is_empty():
		return
	var reveal: float = float(seal.get("reveal", 0.0))
	if reveal <= 0.01:
		return
	var position: Vector2 = seal["position"]
	var size: float = seal["size"]
	var alpha: float = 0.82 * reveal
	var rect: Rect2 = Rect2(position - Vector2(size * 0.5, size * 0.5), Vector2(size, size))
	draw_rect(rect, Color(0.74, 0.14, 0.16, alpha), true)
	draw_rect(rect, Color(0.56, 0.08, 0.10, alpha), false, 1.8)
	draw_line(rect.position + Vector2(size * 0.24, size * 0.18), rect.position + Vector2(size * 0.24, size * 0.76), Color(0.98, 0.90, 0.90, alpha * 0.72), 1.4)
	draw_line(rect.position + Vector2(size * 0.50, size * 0.20), rect.position + Vector2(size * 0.58, size * 0.68), Color(0.98, 0.90, 0.90, alpha * 0.72), 1.2)
	draw_line(rect.position + Vector2(size * 0.30, size * 0.52), rect.position + Vector2(size * 0.68, size * 0.52), Color(0.98, 0.90, 0.90, alpha * 0.72), 1.0)
