class_name AnimatronicFactory
extends RefCounted

static func _material(color: Color, metallic := 0.25, roughness := 0.62, emission := Color(0, 0, 0)) -> StandardMaterial3D:
	var mat := StandardMaterial3D.new()
	mat.albedo_color = color
	mat.metallic = metallic
	mat.roughness = roughness
	if emission != Color(0, 0, 0):
		mat.emission_enabled = true
		mat.emission = emission
		mat.emission_energy_multiplier = 2.0
	return mat

static func _box(parent: Node3D, size: Vector3, pos: Vector3, color: Color, rot := Vector3.ZERO) -> MeshInstance3D:
	var mesh := BoxMesh.new()
	mesh.size = size
	mesh.material = _material(color)
	var n := MeshInstance3D.new()
	n.mesh = mesh
	n.position = pos
	n.rotation = rot
	parent.add_child(n)
	return n

static func _sphere(parent: Node3D, radius: float, pos: Vector3, color: Color, scale := Vector3.ONE) -> MeshInstance3D:
	var mesh := SphereMesh.new()
	mesh.radius = radius
	mesh.height = radius * 2.0
	mesh.radial_segments = 16
	mesh.rings = 8
	mesh.material = _material(color)
	var n := MeshInstance3D.new()
	n.mesh = mesh
	n.position = pos
	n.scale = scale
	parent.add_child(n)
	return n

static func _cylinder(parent: Node3D, radius: float, height: float, pos: Vector3, color: Color, rot := Vector3.ZERO) -> MeshInstance3D:
	var mesh := CylinderMesh.new()
	mesh.top_radius = radius
	mesh.bottom_radius = radius
	mesh.height = height
	mesh.radial_segments = 12
	mesh.material = _material(color, 0.36, 0.55)
	var n := MeshInstance3D.new()
	n.mesh = mesh
	n.position = pos
	n.rotation = rot
	parent.add_child(n)
	return n

static func create_animatronic(display_name: String, species: String, base: Color, accent: Color, worn: bool) -> Node3D:
	var root := Node3D.new()
	root.name = display_name.replace(" ", "_")
	root.set_meta("display_name", display_name)
	root.set_meta("species", species)
	root.set_meta("worn", worn)

	var dark := base.darkened(0.55)
	var metal := Color(0.20, 0.22, 0.24)
	var eye := Color(0.75, 0.95, 1.0) if not worn else Color(1.0, 0.22, 0.12)

	_box(root, Vector3(0.95, 1.2, 0.58), Vector3(0, 1.55, 0), base)
	_box(root, Vector3(0.72, 0.42, 0.50), Vector3(0, 0.82, 0), dark)

	for side in [-1.0, 1.0]:
		_sphere(root, 0.17, Vector3(0.60 * side, 1.86, 0), metal)
		_cylinder(root, 0.17, 0.82, Vector3(0.68 * side, 1.48, 0), base, Vector3(0, 0, deg_to_rad(7.0 * side)))
		_sphere(root, 0.15, Vector3(0.75 * side, 1.02, 0), metal)
		_cylinder(root, 0.15, 0.78, Vector3(0.78 * side, 0.63, 0.02), base)
		_box(root, Vector3(0.34, 0.18, 0.48), Vector3(0.78 * side, 0.18, -0.08), dark)
		_sphere(root, 0.18, Vector3(0.28 * side, 0.72, 0), metal)
		_cylinder(root, 0.19, 0.78, Vector3(0.30 * side, 0.34, 0), base)
		_sphere(root, 0.16, Vector3(0.31 * side, -0.08, 0), metal)
		_cylinder(root, 0.17, 0.72, Vector3(0.31 * side, -0.43, 0), base)
		_box(root, Vector3(0.38, 0.20, 0.68), Vector3(0.31 * side, -0.85, -0.10), dark)

	_cylinder(root, 0.18, 0.28, Vector3(0, 2.28, 0), metal)
	_sphere(root, 0.62, Vector3(0, 2.82, 0), base, Vector3(1.0, 0.9, 0.82))
	_box(root, Vector3(0.78, 0.32, 0.48), Vector3(0, 2.55, -0.38), accent)
	_box(root, Vector3(0.72, 0.13, 0.50), Vector3(0, 2.40, -0.36), dark)

	for side in [-1.0, 1.0]:
		var eye_mesh := SphereMesh.new()
		eye_mesh.radius = 0.11
		eye_mesh.height = 0.22
		eye_mesh.radial_segments = 12
		eye_mesh.rings = 6
		eye_mesh.material = _material(Color(0.06, 0.07, 0.08), 0.0, 0.3, eye)
		var e := MeshInstance3D.new()
		e.mesh = eye_mesh
		e.position = Vector3(0.23 * side, 2.91, -0.47)
		root.add_child(e)

	match species:
		"stag":
			for side in [-1.0, 1.0]:
				_cylinder(root, 0.055, 0.70, Vector3(0.30 * side, 3.48, 0.02), accent, Vector3(0, 0, deg_to_rad(18.0 * side)))
				_cylinder(root, 0.045, 0.35, Vector3(0.49 * side, 3.62, 0.02), accent, Vector3(0, 0, deg_to_rad(48.0 * side)))
		"raccoon":
			for side in [-1.0, 1.0]:
				_sphere(root, 0.25, Vector3(0.47 * side, 3.22, 0.02), dark, Vector3(0.85, 1.15, 0.65))
			_box(root, Vector3(0.75, 0.19, 0.08), Vector3(0, 2.91, -0.58), dark)
		"owl":
			for side in [-1.0, 1.0]:
				_sphere(root, 0.31, Vector3(0.25 * side, 2.95, -0.22), accent, Vector3(0.95, 1.0, 0.42))
			_box(root, Vector3(0.22, 0.26, 0.42), Vector3(0, 2.66, -0.59), Color(0.78, 0.58, 0.16), Vector3(deg_to_rad(25), 0, 0))
		"shark":
			_box(root, Vector3(0.18, 0.62, 0.56), Vector3(0, 3.34, 0.18), accent, Vector3(deg_to_rad(-28), 0, 0))
			_box(root, Vector3(0.34, 0.28, 0.86), Vector3(0, 2.68, -0.62), accent)

	if worn:
		_box(root, Vector3(0.30, 0.42, 0.08), Vector3(-0.30, 1.70, -0.34), metal, Vector3(0, 0, deg_to_rad(8)))
		_box(root, Vector3(0.24, 0.24, 0.07), Vector3(0.33, 2.63, -0.49), metal, Vector3(0, 0, deg_to_rad(-12)))
		for side in [-1.0, 1.0]:
			_cylinder(root, 0.035, 0.38, Vector3(0.45 * side, 1.25, -0.22), Color(0.10, 0.11, 0.12), Vector3(deg_to_rad(30), 0, 0))

	return root
