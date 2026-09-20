extends Node3D

const Factory = preload("res://scripts/animatronic_factory.gd")
const NIGHT_DURATION := 480.0
const OFFICE_POS := Vector3(0, 1.85, 5.8)
const OFFICE_TARGET := Vector3(0, 1.45, -1.0)

var rng := RandomNumberGenerator.new()
var elapsed := 0.0
var power := 100.0
var light_power := 100.0
var monitor := false
var mask := false
var flash := false
var left_door_closed := false
var right_door_closed := false
var ended := false
var current_camera := 0

var view_camera: Camera3D
var office_light: OmniLight3D
var flashlight: SpotLight3D
var left_door: MeshInstance3D
var right_door: MeshInstance3D
var hud_time: Label
var hud_power: Label
var hud_light: Label
var hud_status: Label
var camera_label: Label
var camera_panel: Panel
var monitor_overlay: ColorRect
var mask_overlay: ColorRect
var end_overlay: ColorRect
var end_label: Label
var camera_points: Array[Vector3] = []
var camera_targets: Array[Vector3] = []
var camera_names: Array[String] = []
var bots: Array[Dictionary] = []

func _ready() -> void:
	rng.randomize()
	_build_world()
	_build_ui()
	_build_bots()
	_apply_view()
	_update_hud()

func _process(delta: float) -> void:
	_flicker()
	if ended:
		return
	elapsed += delta
	if elapsed >= NIGHT_DURATION:
		_win()
		return
	_drain(delta)
	_update_bots(delta)
	_update_doors(delta)
	_update_hud()
	if power <= 0.0:
		power = 0.0
		monitor = false
		mask = false
		flash = false
		left_door_closed = false
		right_door_closed = false
		hud_status.text = "POWER OUT"
		_apply_view()

func _unhandled_input(event: InputEvent) -> void:
	if ended:
		return
	if event.is_action_pressed("toggle_monitor"):
		_toggle_monitor()
	elif event.is_action_pressed("toggle_mask"):
		_toggle_mask()
	elif event.is_action_pressed("toggle_left_door"):
		_toggle_left()
	elif event.is_action_pressed("toggle_right_door"):
		_toggle_right()
	elif event.is_action_pressed("flashlight"):
		flash = true
	elif event.is_action_released("flashlight"):
		flash = false

func _build_world() -> void:
	var world := WorldEnvironment.new()
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.002, 0.004, 0.006)
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.10, 0.12, 0.15)
	env.ambient_light_energy = 0.18
	env.fog_enabled = true
	env.fog_density = 0.025
	world.environment = env
	add_child(world)

	_box(Vector3(9.5, 0.2, 7.0), Vector3(0, -0.1, 3.0), Color(0.09, 0.09, 0.10))
	_box(Vector3(9.5, 0.2, 7.0), Vector3(0, 4.2, 3.0), Color(0.035, 0.038, 0.045))
	_box(Vector3(0.25, 4.2, 7.0), Vector3(-4.75, 2.0, 3.0), Color(0.055, 0.06, 0.07))
	_box(Vector3(0.25, 4.2, 7.0), Vector3(4.75, 2.0, 3.0), Color(0.055, 0.06, 0.07))
	_box(Vector3(3.0, 4.2, 0.25), Vector3(-3.25, 2.0, -0.45), Color(0.05, 0.055, 0.065))
	_box(Vector3(3.0, 4.2, 0.25), Vector3(3.25, 2.0, -0.45), Color(0.05, 0.055, 0.065))
	_box(Vector3(4.2, 1.05, 1.55), Vector3(0, 0.55, 1.55), Color(0.10, 0.085, 0.07))
	_box(Vector3(2.0, 0.08, 0.95), Vector3(0, 1.12, 1.15), Color(0.18, 0.17, 0.15))

	_corridor(Vector3(0, 0, -7.5), Vector3(7.0, 4.0, 14.0))
	_corridor(Vector3(-6.2, 0, -5.5), Vector3(5.2, 3.5, 8.0))
	_corridor(Vector3(6.2, 0, -5.5), Vector3(5.2, 3.5, 8.0))
	_box(Vector3(13.0, 0.2, 8.0), Vector3(0, -0.1, -16.0), Color(0.07, 0.065, 0.06))
	_box(Vector3(11.0, 0.65, 3.0), Vector3(0, 0.32, -18.4), Color(0.16, 0.12, 0.10))
	for x in [-3.5, 0.0, 3.5]:
		_box(Vector3(1.8, 0.12, 2.5), Vector3(x, 0.82, -12.7), Color(0.12, 0.11, 0.10))
		_cylinder(0.42, 0.78, Vector3(x, 0.40, -12.7), Color(0.08, 0.08, 0.09))

	left_door = _box(Vector3(2.0, 3.3, 0.18), Vector3(-2.0, 5.5, -0.35), Color(0.17, 0.18, 0.19))
	right_door = _box(Vector3(2.0, 3.3, 0.18), Vector3(2.0, 5.5, -0.35), Color(0.17, 0.18, 0.19))

	view_camera = Camera3D.new()
	view_camera.position = OFFICE_POS
	view_camera.fov = 67.0
	view_camera.current = true
	add_child(view_camera)
	view_camera.look_at(OFFICE_TARGET, Vector3.UP)

	flashlight = SpotLight3D.new()
	flashlight.light_color = Color(0.82, 0.90, 1.0)
	flashlight.light_energy = 4.2
	flashlight.spot_range = 22.0
	flashlight.spot_angle = 24.0
	flashlight.shadow_enabled = true
	view_camera.add_child(flashlight)

	office_light = OmniLight3D.new()
	office_light.position = Vector3(0, 3.35, 2.0)
	office_light.light_color = Color(0.55, 0.64, 0.78)
	office_light.light_energy = 0.62
	office_light.omni_range = 9.0
	office_light.shadow_enabled = true
	add_child(office_light)

	var stage_light := SpotLight3D.new()
	stage_light.position = Vector3(0, 3.9, -14.5)
	stage_light.rotation_degrees = Vector3(-72, 0, 0)
	stage_light.light_color = Color(0.45, 0.48, 0.56)
	stage_light.light_energy = 2.0
	stage_light.spot_range = 10.0
	stage_light.spot_angle = 45.0
	add_child(stage_light)

	camera_names = ["STAGE", "DINING", "LEFT HALL", "RIGHT HALL", "LEFT SERVICE", "RIGHT SERVICE", "STORAGE", "WORKSHOP"]
	camera_points = [Vector3(0,3.1,-14.2), Vector3(0,2.7,-9.0), Vector3(-6,2.45,-3.5), Vector3(6,2.45,-3.5), Vector3(-2.7,2.15,-1.2), Vector3(2.7,2.15,-1.2), Vector3(-8,2.5,-10), Vector3(8,2.5,-10)]
	camera_targets = [Vector3(0,1.5,-18.2), Vector3(0,1.2,-13), Vector3(-3.2,1.3,-1), Vector3(3.2,1.3,-1), Vector3(-1.4,1.2,0), Vector3(1.4,1.2,0), Vector3(-5,1.2,-8.5), Vector3(5,1.2,-8.5)]

func _corridor(center: Vector3, size: Vector3) -> void:
	_box(Vector3(size.x,0.18,size.z), center + Vector3(0,-0.09,0), Color(0.065,0.067,0.072))
	_box(Vector3(0.20,size.y,size.z), center + Vector3(-size.x*0.5,size.y*0.5,0), Color(0.045,0.048,0.055))
	_box(Vector3(0.20,size.y,size.z), center + Vector3(size.x*0.5,size.y*0.5,0), Color(0.045,0.048,0.055))
	_box(Vector3(size.x,0.18,size.z), center + Vector3(0,size.y,0), Color(0.035,0.038,0.045))

func _box(size: Vector3, pos: Vector3, color: Color) -> MeshInstance3D:
	var mesh := BoxMesh.new()
	mesh.size = size
	var mat := StandardMaterial3D.new()
	mat.albedo_color = color
	mat.roughness = 0.78
	mesh.material = mat
	var n := MeshInstance3D.new()
	n.mesh = mesh
	n.position = pos
	add_child(n)
	return n

func _cylinder(radius: float, height: float, pos: Vector3, color: Color) -> MeshInstance3D:
	var mesh := CylinderMesh.new()
	mesh.top_radius = radius
	mesh.bottom_radius = radius
	mesh.height = height
	mesh.radial_segments = 16
	var mat := StandardMaterial3D.new()
	mat.albedo_color = color
	mat.roughness = 0.7
	mesh.material = mat
	var n := MeshInstance3D.new()
	n.mesh = mesh
	n.position = pos
	add_child(n)
	return n

func _build_bots() -> void:
	_spawn("Bramble Stag","stag",Color(0.28,0.18,0.12),Color(0.52,0.34,0.18),true,[0,1,2,4],"left",2.2)
	_spawn("Rook Raccoon","raccoon",Color(0.22,0.24,0.26),Color(0.40,0.44,0.48),true,[0,1,3,5],"right",2.0)
	_spawn("Piper Owl","owl",Color(0.30,0.25,0.16),Color(0.58,0.45,0.20),true,[0,1,4],"mask",2.4)
	_spawn("Mako Shark","shark",Color(0.13,0.24,0.28),Color(0.25,0.55,0.62),true,[6,2,4],"flash",2.8)
	_spawn("Nova Stag","stag",Color(0.18,0.36,0.48),Color(0.58,0.86,0.94),false,[0,1,3,5],"mask",2.0)
	_spawn("Nova Raccoon","raccoon",Color(0.44,0.20,0.34),Color(0.92,0.52,0.76),false,[0,1,2,4],"mask",2.1)
	_spawn("Nova Owl","owl",Color(0.28,0.42,0.20),Color(0.70,0.94,0.42),false,[7,3,5],"flash",2.35)
	_spawn("Nova Shark","shark",Color(0.14,0.30,0.50),Color(0.40,0.74,1.0),false,[7,1,2,4],"left",2.55)

func _spawn(name: String, species: String, base: Color, accent: Color, worn: bool, route: Array, counter: String, difficulty: float) -> void:
	var model: Node3D = Factory.create_animatronic(name,species,base,accent,worn)
	model.scale = Vector3.ONE * (0.83 if worn else 0.88)
	add_child(model)
	model.position = camera_targets[int(route[0])] + Vector3(rng.randf_range(-1.2,1.2),-0.6,rng.randf_range(-0.8,0.8))
	bots.append({"name":name,"node":model,"route":route,"index":0,"counter":counter,"difficulty":difficulty,"cooldown":rng.randf_range(8.0,18.0),"attacking":false,"attack":0.0,"deflect":0.0,"phase":rng.randf_range(0.0,TAU)})

func _build_ui() -> void:
	var layer := CanvasLayer.new()
	add_child(layer)
	var root := Control.new()
	root.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	layer.add_child(root)
	var top := ColorRect.new()
	top.color = Color(0,0,0,0.58)
	top.size = Vector2(1280,58)
	root.add_child(top)
	hud_time = _label(root,"12 AM",Vector2(24,13),28)
	hud_power = _label(root,"POWER 100%",Vector2(470,15),22)
	hud_light = _label(root,"LIGHT 100%",Vector2(680,15),22)
	hud_status = _label(root,"Night 1",Vector2(970,16),18)
	_button(root,"LEFT DOOR [A]",Vector2(25,640),Vector2(190,58),func(): _toggle_left())
	_button(root,"MONITOR [M]",Vector2(250,640),Vector2(190,58),func(): _toggle_monitor())
	_button(root,"MASK [SPACE]",Vector2(475,640),Vector2(190,58),func(): _toggle_mask())
	var fb := _button(root,"FLASH [F]",Vector2(700,640),Vector2(190,58),func(): pass)
	fb.button_down.connect(func(): flash = true)
	fb.button_up.connect(func(): flash = false)
	_button(root,"RIGHT DOOR [D]",Vector2(925,640),Vector2(190,58),func(): _toggle_right())
	monitor_overlay = ColorRect.new()
	monitor_overlay.color = Color(0.08,0.13,0.16,0.13)
	monitor_overlay.position = Vector2(0,58)
	monitor_overlay.size = Vector2(1280,570)
	monitor_overlay.mouse_filter = Control.MOUSE_FILTER_IGNORE
	root.add_child(monitor_overlay)
	camera_label = _label(root,"CAM 01 - STAGE",Vector2(24,80),24)
	camera_panel = Panel.new()
	camera_panel.position = Vector2(1015,95)
	camera_panel.size = Vector2(240,410)
	root.add_child(camera_panel)
	for i in range(8):
		var b := Button.new()
		b.text = "CAM %02d" % (i+1)
		b.position = Vector2(12+(i%2)*108,14+int(i/2)*94)
		b.size = Vector2(98,72)
		var idx := i
		b.pressed.connect(func(): _select_camera(idx))
		camera_panel.add_child(b)
	mask_overlay = ColorRect.new()
	mask_overlay.color = Color(0,0,0,0.68)
	mask_overlay.size = Vector2(1280,720)
	mask_overlay.mouse_filter = Control.MOUSE_FILTER_IGNORE
	root.add_child(mask_overlay)
	_label(mask_overlay,"SERVICE VISOR",Vector2(520,330),26)
	end_overlay = ColorRect.new()
	end_overlay.color = Color(0.08,0,0,0.90)
	end_overlay.size = Vector2(1280,720)
	end_overlay.visible = false
	root.add_child(end_overlay)
	end_label = _label(end_overlay,"",Vector2(280,245),46)
	end_label.size = Vector2(720,160)
	end_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_button(end_overlay,"RESTART NIGHT",Vector2(505,455),Vector2(270,70),func(): get_tree().reload_current_scene())

func _label(parent: Control, text: String, pos: Vector2, font_size: int) -> Label:
	var l := Label.new()
	l.text = text
	l.position = pos
	l.add_theme_font_size_override("font_size",font_size)
	parent.add_child(l)
	return l

func _button(parent: Control, text: String, pos: Vector2, size: Vector2, callback: Callable) -> Button:
	var b := Button.new()
	b.text = text
	b.position = pos
	b.size = size
	b.add_theme_font_size_override("font_size",18)
	b.pressed.connect(callback)
	parent.add_child(b)
	return b

func _toggle_monitor() -> void:
	if power <= 0.0: return
	monitor = not monitor
	if monitor:
		mask = false
		flash = false
	_apply_view()

func _toggle_mask() -> void:
	mask = not mask
	if mask:
		monitor = false
		flash = false
	_apply_view()

func _toggle_left() -> void:
	if power > 0.0: left_door_closed = not left_door_closed

func _toggle_right() -> void:
	if power > 0.0: right_door_closed = not right_door_closed

func _select_camera(index: int) -> void:
	current_camera = clampi(index,0,camera_points.size()-1)
	_apply_view()

func _apply_view() -> void:
	if monitor:
		view_camera.position = camera_points[current_camera]
		view_camera.look_at(camera_targets[current_camera],Vector3.UP)
		camera_label.text = "CAM %02d - %s" % [current_camera+1,camera_names[current_camera]]
	else:
		view_camera.position = OFFICE_POS
		view_camera.look_at(OFFICE_TARGET,Vector3.UP)
	camera_panel.visible = monitor
	monitor_overlay.visible = monitor
	camera_label.visible = monitor
	mask_overlay.visible = mask

func _drain(delta: float) -> void:
	var rate := 0.027 + (0.035 if monitor else 0.0) + (0.072 if left_door_closed else 0.0) + (0.072 if right_door_closed else 0.0) + (0.010 if mask else 0.0)
	power = maxf(0.0,power-rate*delta)
	if flash and light_power > 0.0 and power > 0.0:
		light_power = maxf(0.0,light_power-0.09*delta)
	if light_power <= 0.0: flash = false
	flashlight.visible = flash and not monitor and not mask and power > 0.0

func _update_bots(delta: float) -> void:
	var progress := clampf(elapsed/NIGHT_DURATION,0.0,1.0)
	for bot in bots:
		var node: Node3D = bot["node"]
		bot["phase"] = float(bot["phase"]) + delta*0.65
		node.rotation.z = sin(float(bot["phase"]))*0.015
		if bool(bot["attacking"]):
			if _counter_active(String(bot["counter"])):
				bot["deflect"] = float(bot["deflect"]) + delta
				if float(bot["deflect"]) >= 0.75: _reset_bot(bot)
			else:
				bot["deflect"] = 0.0
				bot["attack"] = float(bot["attack"]) + delta
				if float(bot["attack"]) >= 1.45:
					_lose(String(bot["name"]),node)
					return
			continue
		bot["cooldown"] = float(bot["cooldown"]) - delta
		if float(bot["cooldown"]) > 0.0: continue
		var chance := clampf(0.16 + float(bot["difficulty"])*0.06 + progress*0.36,0.0,0.88)
		bot["cooldown"] = rng.randf_range(5.5,13.5)/(1.0+progress*0.25)
		if rng.randf() > chance: continue
		var route: Array = bot["route"]
		bot["index"] = int(bot["index"]) + 1
		if int(bot["index"]) >= route.size():
			bot["attacking"] = true
			bot["attack"] = 0.0
			bot["deflect"] = 0.0
			node.position = _entry(String(bot["counter"]))
			node.look_at(Vector3(0,1.5,2.0),Vector3.UP)
			hud_status.text = "MOVEMENT NEAR OFFICE"
		else:
			var cam_idx := int(route[int(bot["index"])])
			node.position = camera_targets[cam_idx] + Vector3(rng.randf_range(-0.8,0.8),-0.65,rng.randf_range(-0.7,0.7))

func _entry(counter: String) -> Vector3:
	match counter:
		"left": return Vector3(-2.1,0.9,-0.8)
		"right": return Vector3(2.1,0.9,-0.8)
		"flash": return Vector3(0,0.9,-2.1)
		_: return Vector3(0,0.9,-0.55)

func _counter_active(counter: String) -> bool:
	match counter:
		"left": return left_door_closed and power > 0.0
		"right": return right_door_closed and power > 0.0
		"mask": return mask
		"flash": return flash and light_power > 0.0 and not monitor and not mask
	return false

func _reset_bot(bot: Dictionary) -> void:
	bot["attacking"] = false
	bot["attack"] = 0.0
	bot["deflect"] = 0.0
	bot["index"] = 0
	bot["cooldown"] = rng.randf_range(9.0,18.0)
	var route: Array = bot["route"]
	var node: Node3D = bot["node"]
	node.position = camera_targets[int(route[0])] + Vector3(rng.randf_range(-1.1,1.1),-0.65,rng.randf_range(-0.6,0.6))
	hud_status.text = "%s RETREATED" % String(bot["name"])

func _update_doors(delta: float) -> void:
	left_door.position.y = lerpf(left_door.position.y,1.45 if left_door_closed else 5.5,minf(1.0,delta*7.0))
	right_door.position.y = lerpf(right_door.position.y,1.45 if right_door_closed else 5.5,minf(1.0,delta*7.0))

func _update_hud() -> void:
	var hour_idx := clampi(int(elapsed/(NIGHT_DURATION/6.0)),0,5)
	hud_time.text = "%s AM" % ("12" if hour_idx == 0 else str(hour_idx))
	hud_power.text = "POWER %03d%%" % int(ceil(power))
	hud_light.text = "LIGHT %03d%%" % int(ceil(light_power))
	if monitor: hud_status.text = "CAM %02d / %s" % [current_camera+1,camera_names[current_camera]]

func _flicker() -> void:
	if office_light:
		office_light.light_energy = maxf(0.12,0.58 + sin(Time.get_ticks_msec()*0.004)*0.06 + rng.randf_range(-0.05,0.05))

func _lose(attacker: String, node: Node3D) -> void:
	ended = true
	monitor = false
	mask = false
	flash = false
	_apply_view()
	node.scale *= 1.45
	node.global_position = view_camera.global_position - view_camera.global_transform.basis.z*1.15 + Vector3(0,-1.2,0)
	node.look_at(view_camera.global_position,Vector3.UP)
	end_overlay.color = Color(0.15,0,0,0.90)
	end_overlay.visible = true
	end_label.text = "SIGNAL LOST\n%s REACHED THE OFFICE" % attacker

func _win() -> void:
	ended = true
	monitor = false
	mask = false
	flash = false
	_apply_view()
	hud_time.text = "6 AM"
	end_overlay.color = Color(0.02,0.09,0.07,0.92)
	end_overlay.visible = true
	end_label.text = "6 AM\nNIGHT COMPLETE"
