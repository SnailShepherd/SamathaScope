extends Node2D

var _embers: Array = []

func apply_state(state: Dictionary, active: bool, delta: float, anchor: Vector2) -> void:
	var intensity: float = state["intensity"]
	var focus: float = state["focus"]
	if active and randf() < (0.06 + intensity * 0.18):
		_embers.append({
			"position": anchor + Vector2(randf_range(-34.0, 34.0), randf_range(-10.0, 10.0)),
			"velocity": Vector2(randf_range(-26.0, 26.0) * (1.0 - focus * 0.4), randf_range(-160.0, -80.0)),
			"radius": randf_range(2.0, 5.0) + intensity * 3.0,
			"alpha": 0.4 + intensity * 0.5,
		})
	for ember in _embers:
		ember.position += ember.velocity * delta
		ember.velocity *= 0.98
		ember.alpha = max(ember.alpha - delta * 0.42, 0.0)
	_embers = _embers.filter(func(ember): return ember.alpha > 0.02)
	queue_redraw()

func _draw() -> void:
	for ember in _embers:
		draw_circle(ember.position, ember.radius, Color(1.0, 0.77, 0.34, ember.alpha))
		draw_circle(ember.position, ember.radius * 0.4, Color(1.0, 0.94, 0.72, ember.alpha))

