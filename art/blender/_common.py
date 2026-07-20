# Shared helpers for Fight & Conquer piece scripts.
# Prepended to each art/blender/pieces/*.py by tools/blender_run.py.
#
# Conventions:
# - Blender is Z-up; pieces stand on z=0 and grow along +Z (glTF export converts
#   to the game's Y-up). Front of a piece faces -Y.
# - One collection per piece (PIECE_<kind>), deleted and rebuilt each run.
# - One final object per color role, named "<piece>__<ROLE>". Role materials are
#   viewport preview only — the game tints by object-name suffix.

import bpy
import math
from math import radians

ROLE_COLORS = {
    "FACTION": (0.275, 0.392, 0.328, 1.0),      # sage preview (linear)
    "GOLD": (0.694, 0.451, 0.150, 1.0),
    "STONE": (0.624, 0.583, 0.540, 1.0),
    "TRUNK": (0.254, 0.147, 0.078, 1.0),
    "TREE_FOLIAGE": (0.047, 0.102, 0.072, 1.0),
    "PIP": (0.068, 0.059, 0.050, 1.0),
}


def role_material(role):
    name = f"MAT_{role}"
    mat = bpy.data.materials.get(name)
    if mat is None:
        mat = bpy.data.materials.new(name)
        mat.use_nodes = True
        bsdf = mat.node_tree.nodes.get("Principled BSDF")
        if bsdf:
            bsdf.inputs["Base Color"].default_value = ROLE_COLORS[role]
            bsdf.inputs["Roughness"].default_value = 1.0
            bsdf.inputs["Metallic"].default_value = 0.0
        mat.diffuse_color = ROLE_COLORS[role]
    return mat


def reset_piece(kind):
    """Idempotent: remove the piece collection + orphan meshes, recreate."""
    name = f"PIECE_{kind}"
    coll = bpy.data.collections.get(name)
    if coll:
        for obj in list(coll.objects):
            bpy.data.objects.remove(obj, do_unlink=True)
    else:
        coll = bpy.data.collections.new(name)
        bpy.context.scene.collection.children.link(coll)
    for mesh in [m for m in bpy.data.meshes if m.users == 0]:
        bpy.data.meshes.remove(mesh)
    return coll


def _apply(obj):
    with bpy.context.temp_override(active_object=obj, selected_editable_objects=[obj]):
        bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)


def _finish(obj, coll, role):
    for c in list(obj.users_collection):
        c.objects.unlink(obj)
    coll.objects.link(obj)
    obj.data.materials.clear()
    obj.data.materials.append(role_material(role))
    for p in obj.data.polygons:
        p.use_smooth = False
    obj["role"] = role
    return obj


def add_cyl(coll, role, r, h, z0, seg=8, x=0.0, y=0.0, r_top=None, rot=(0, 0, 0), z_center=None):
    """Cylinder; r_top for frustum, r_top=0 for cone. z_center overrides z0+h/2."""
    loc = (x, y, z_center if z_center is not None else z0 + h / 2)
    if r_top is None:
        bpy.ops.mesh.primitive_cylinder_add(vertices=seg, radius=r, depth=h,
                                            end_fill_type='NGON', location=loc)
    else:
        bpy.ops.mesh.primitive_cone_add(vertices=seg, radius1=r, radius2=r_top,
                                        depth=h, location=loc)
    obj = bpy.context.active_object
    obj.rotation_euler = rot
    _apply(obj)
    return _finish(obj, coll, role)


def add_sphere(coll, role, r, z, seg=8, rings=4, x=0.0, y=0.0, scale=(1, 1, 1)):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=seg, ring_count=rings, radius=r,
                                         location=(x, y, z))
    obj = bpy.context.active_object
    obj.scale = scale
    _apply(obj)
    return _finish(obj, coll, role)


def add_box(coll, role, sx, sy, sz, z0, x=0.0, y=0.0, rot=(0, 0, 0)):
    """Full sizes; sits on z0 before rotation."""
    bpy.ops.mesh.primitive_cube_add(size=1, location=(x, y, z0 + sz / 2))
    obj = bpy.context.active_object
    obj.scale = (sx, sy, sz)
    obj.rotation_euler = rot
    _apply(obj)
    return _finish(obj, coll, role)


def add_wedge(coll, role, sx, sy, sz, z0, x=0.0, y=0.0, rot_z=0.0):
    """Gabled wedge, ridge along Y."""
    hx, hy = sx / 2, sy / 2
    verts = [(-hx, -hy, 0), (hx, -hy, 0), (hx, hy, 0), (-hx, hy, 0), (0, -hy, sz), (0, hy, sz)]
    faces = [(3, 2, 1, 0), (0, 1, 4), (2, 3, 5), (0, 4, 5, 3), (1, 2, 5, 4)]
    mesh = bpy.data.meshes.new("wedge")
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    obj = bpy.data.objects.new("wedge", mesh)
    obj.location = (x, y, z0)
    obj.rotation_euler = (0, 0, rot_z)
    bpy.context.scene.collection.objects.link(obj)
    _apply(obj)
    return _finish(obj, coll, role)


def add_pennant(coll, role, pole_x, top_z, drop, length):
    verts = [(pole_x, 0, top_z), (pole_x, 0, top_z - drop), (pole_x + length, 0, top_z - drop / 2)]
    mesh = bpy.data.meshes.new("pennant")
    mesh.from_pydata(verts, [], [(0, 1, 2)])
    mesh.update()
    obj = bpy.data.objects.new("pennant", mesh)
    bpy.context.scene.collection.objects.link(obj)
    solidify = obj.modifiers.new("Solidify", 'SOLIDIFY')
    solidify.thickness = 0.006
    solidify.offset = 0
    return _finish(obj, coll, role)


def add_pips(coll, count, ring_r, z0, stud_r=0.018, stud_h=0.014):
    """Ink studs on the plinth top rim — countable from above at any yaw."""
    out = []
    for k in range(count):
        angle = radians(90) + 2 * math.pi * k / count  # start at back, keep the face clean
        out.append(add_cyl(coll, "PIP", stud_r, stud_h, z0, seg=6,
                           x=ring_r * math.cos(angle), y=ring_r * math.sin(angle)))
    return out


def join_roles(coll, piece):
    """Join temp parts per role into '<piece>__<ROLE>' with a Triangulate modifier."""
    by_role = {}
    for obj in [o for o in coll.objects if o.type == 'MESH']:
        by_role.setdefault(obj["role"], []).append(obj)
    for role, objs in by_role.items():
        target = objs[0]
        if len(objs) > 1:
            with bpy.context.temp_override(active_object=target,
                                           selected_editable_objects=objs,
                                           selected_objects=objs):
                bpy.ops.object.join()
        target.name = f"{piece}__{role}"
        target.data.name = target.name
        tri = target.modifiers.new("Triangulate", 'TRIANGULATE')
        tri.quad_method = 'BEAUTY'
        tri.ngon_method = 'BEAUTY'
    return coll


def export_piece(piece, coll):
    """Export the collection to art/models/<piece>.glb (repo path)."""
    import os
    out_dir = os.path.expanduser("~/AndroidStudioProjects/FightAndConquer/art/models")
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, f"{piece}.glb")
    bpy.ops.object.select_all(action='DESELECT')
    for obj in coll.objects:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = coll.objects[0]
    bpy.ops.export_scene.gltf(
        filepath=path,
        export_format='GLB',
        use_selection=True,
        export_yup=True,
        export_apply=True,
        export_materials='NONE',
        export_normals=True,
        export_animations=False,
        export_skins=False,
    )
    return path


def stage_for_render(kind=None, z_focus=0.25):
    """Camera + sun aimed at the origin piece; hides every OTHER piece collection."""
    import mathutils
    scene = bpy.context.scene
    if kind is not None:
        for coll in bpy.data.collections:
            if coll.name.startswith("PIECE_"):
                solo = coll.name == f"PIECE_{kind}"
                coll.hide_render = not solo
                coll.hide_viewport = not solo
    cam = bpy.data.objects.get("PieceCam")
    if cam is None:
        cam = bpy.data.objects.new("PieceCam", bpy.data.cameras.new("PieceCam"))
        scene.collection.objects.link(cam)
    cam.location = (0.95, -0.95, 0.75)
    direction = mathutils.Vector((0, 0, z_focus)) - cam.location
    cam.rotation_euler = direction.to_track_quat('-Z', 'Y').to_euler()
    scene.camera = cam
    sun = bpy.data.objects.get("PieceSun")
    if sun is None:
        sun_data = bpy.data.lights.new("PieceSun", type='SUN')
        sun_data.energy = 3.0
        sun_data.angle = radians(5)
        sun = bpy.data.objects.new("PieceSun", sun_data)
        scene.collection.objects.link(sun)
    sun.rotation_euler = (radians(50), 0, radians(30))
    if scene.world and scene.world.use_nodes:
        bg = scene.world.node_tree.nodes.get("Background")
        if bg:
            bg.inputs[0].default_value = (0.913, 0.897, 0.869, 1.0)
    for name in ("Cube",):
        stale = bpy.data.objects.get(name)
        if stale:
            stale.hide_render = True
            stale.hide_set(True)
