extends Node

var presets := [
	{
		"scene_id": "ink_garden",
		"running": true,
		"paused": false,
		"version": 1,
		"composition_seed": 1001,
		"calmness": 0.82,
		"focus": 0.64,
		"stability": 0.78,
		"intensity": 0.48,
		"drift": -0.18,
		"progress": 0.34,
		"calmness_rate": 0.0,
		"focus_rate": 0.0,
		"intensity_rate": 0.0,
	},
	{
		"scene_id": "ink_garden",
		"running": true,
		"paused": false,
		"version": 2,
		"composition_seed": 1002,
		"calmness": 0.58,
		"focus": 0.84,
		"stability": 0.74,
		"intensity": 0.74,
		"drift": 0.12,
		"progress": 0.66,
		"calmness_rate": -0.12,
		"focus_rate": 0.16,
		"intensity_rate": 0.24,
	},
	{
		"scene_id": "fire",
		"running": true,
		"paused": false,
		"version": 3,
		"composition_seed": 1003,
		"calmness": 0.44,
		"focus": 0.72,
		"stability": 0.62,
		"intensity": 0.88,
		"drift": 0.26,
		"progress": 0.78,
		"calmness_rate": -0.18,
		"focus_rate": 0.04,
		"intensity_rate": 0.28,
	},
]

var _elapsed := 0.0
var _index := 0

func current_payload() -> Dictionary:
	return presets[_index]

func advance(delta: float) -> void:
	_elapsed += delta
	if _elapsed >= 12.0:
		_elapsed = 0.0
		_index = (_index + 1) % presets.size()
