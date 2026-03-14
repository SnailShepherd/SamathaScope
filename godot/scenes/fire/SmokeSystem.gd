extends Node2D

var _puffs: Array = []

func apply_state(state: Dictionary, active: bool, delta: float, anchor: Vector2) -> void:
	var stability: float = state["stability"]
	var intensity: float = state["intensity"]
	var drift: float = state["drift"]
	if active and randf() < (0.03 + (1.0 - stability) * 0.10):
		_puffs.append({
			"position": anchor + Vector2(randf_range(-24.0, 24.0), randf_range(-6.0, 8.0)),
			"velocity": Vector2(drift * 34.0 + randf_range(-6.0, 6.0), randf_range(-40.0, -18.0)),
			"radius": 20.0 + intensity * 22.0,
			"alpha": 0.10 + (1.0 - stability) * 0.16,
		})
	for puff in _puffs:
		puff.position += puff.velocity * delta
		puff.radius += delta * 18.0
		puff.alpha = max(puff.alpha - delta * 0.08, 0.0)
	_puffs = _puffs.filter(func(puff): return puff.alpha > 0.01)
	queue_redraw()

func _draw() -> void:
	for puff in _puffs:
		draw_circle(puff.position, puff.radius, Color(0.65, 0.67, 0.69, puff.alpha))

