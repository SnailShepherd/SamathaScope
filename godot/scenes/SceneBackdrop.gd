extends Node2D

var _grain_phase := 0.0

func _process(delta: float) -> void:
	_grain_phase += delta
	queue_redraw()

func _draw() -> void:
	var size := get_viewport_rect().size
	if SceneParamBus.scene_id == "ink_garden":
		draw_rect(Rect2(Vector2.ZERO, size), Color(0.965, 0.95, 0.915, 1.0), true)
		draw_circle(size * Vector2(0.24, 0.18), size.y * 0.26, Color(0.86, 0.84, 0.79, 0.08))
		draw_circle(size * Vector2(0.78, 0.26), size.y * 0.18, Color(0.78, 0.76, 0.72, 0.05))
		for index in range(18):
			var t := float(index) / 17.0
			var x := size.x * (0.06 + t * 0.88)
			var y := size.y * (0.10 + fmod((t * 2.7) + (_grain_phase * 0.018), 0.76))
			draw_circle(Vector2(x, y), 1.4 + fmod(index * 1.7, 2.1), Color(0.20, 0.18, 0.16, 0.025))
	else:
		draw_rect(Rect2(Vector2.ZERO, size), Color(0.07, 0.05, 0.04, 1.0), true)
		draw_circle(size * Vector2(0.48, 0.54), size.y * 0.24, Color(0.40, 0.20, 0.10, 0.09))
		draw_circle(size * Vector2(0.62, 0.40), size.y * 0.16, Color(0.78, 0.42, 0.14, 0.05))
