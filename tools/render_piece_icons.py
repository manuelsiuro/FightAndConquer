#!/usr/bin/env python3
"""Bake UI icons of every piece model via the Blender Lab MCP server.

For each piece this re-runs its author script (art/blender/pieces/<name>.py,
which also re-exports the GLB, keeping icon and model in lockstep), then
renders a transparent-background orthographic PNG twice:

  art/icons/piece_<name>.png                          512x512 checked-in master
  app/src/main/res/drawable-nodpi/piece_<name>.png    256x256 shipped drawable

FACTION parts are baked in a neutral warm gray (#B8B2AA) — ownership is shown
by the faction dot in the UI, not the icon. The epilogue restores the sage
preview color and perspective camera so interactive sessions stay sane.

Blender must be running with the MCP add-on connected (same precondition as
`blender_run.py exec`).

Usage:
  render_piece_icons.py            bake all pieces
  render_piece_icons.py capital farm    bake a subset
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from blender_run import Bridge

REPO = Path(__file__).parent.parent
PIECES_DIR = REPO / "art" / "blender" / "pieces"
MASTER_DIR = REPO / "art" / "icons"
DRAWABLE_DIR = REPO / "app" / "src" / "main" / "res" / "drawable-nodpi"

# Renders on top of the piece script, which already ran stage_for_render(KIND).
ICON_EPILOGUE = """
# ---- icon render epilogue (render_piece_icons.py) ----
import os
import mathutils

def _srgb_to_linear(c):
    c /= 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

_scene = bpy.context.scene
_cam = bpy.data.objects["PieceCam"]

# Neutral warm-gray FACTION (#B8B2AA) so icons read as "generic piece".
_faction = bpy.data.materials["MAT_FACTION"]
_neutral = tuple(_srgb_to_linear(c) for c in (0xB8, 0xB2, 0xAA)) + (1.0,)
_bsdf = _faction.node_tree.nodes.get("Principled BSDF")
_bsdf.inputs["Base Color"].default_value = _neutral
_faction.diffuse_color = _neutral

# Orthographic auto-fit: aim at the piece bbox center, frame with margin.
_corners = []
for _obj in coll.objects:
    if _obj.type == 'MESH':
        _corners += [_obj.matrix_world @ mathutils.Vector(c) for c in _obj.bound_box]
_lo = mathutils.Vector((min(v[i] for v in _corners) for i in range(3)))
_hi = mathutils.Vector((max(v[i] for v in _corners) for i in range(3)))
_center = (_lo + _hi) / 2
_cam.location = mathutils.Vector((0.95, -0.95, 0.75)) + _center
_dir = _center - _cam.location
_cam.rotation_euler = _dir.to_track_quat('-Z', 'Y').to_euler()
_cam.data.type = 'ORTHO'
bpy.context.view_layer.update()
_inv = _cam.matrix_world.inverted()
_local = [_inv @ v for v in _corners]
_extent = max(
    max(v.x for v in _local) - min(v.x for v in _local),
    max(v.y for v in _local) - min(v.y for v in _local),
)
_cam.data.ortho_scale = _extent * 1.12

# Soften lighting for the Standard transform: the authoring sun (3.0) plus the
# warm world clips highlights and bleaches the palette. Sunlit-top irradiance
# ~= 1.5*cos40/pi + 0.65 ~ 1.0, so rendered color ~ albedo = the palette hex.
_sun = bpy.data.lights["PieceSun"]
_sun.energy = 1.5
_bg = _scene.world.node_tree.nodes["Background"]
_bg.inputs[0].default_value = (0.65, 0.65, 0.65, 1.0)

# Flat pastel output: transparent film, Standard transform (AgX washes the
# palette away from the UI hex values).
try:
    _scene.render.engine = 'BLENDER_EEVEE_NEXT'
except TypeError:
    _scene.render.engine = 'BLENDER_EEVEE'
_scene.render.film_transparent = True
_scene.render.image_settings.file_format = 'PNG'
_scene.render.image_settings.color_mode = 'RGBA'
_scene.view_settings.view_transform = 'Standard'
_scene.view_settings.look = 'None'
_scene.view_settings.exposure = 0.0
assert _scene.view_settings.view_transform == 'Standard'

for _size, _out in (
    (512, os.path.expanduser("~/AndroidStudioProjects/FightAndConquer/art/icons/piece_{name}.png")),
    (256, os.path.expanduser("~/AndroidStudioProjects/FightAndConquer/app/src/main/res/drawable-nodpi/piece_{name}.png")),
):
    os.makedirs(os.path.dirname(_out), exist_ok=True)
    _scene.render.resolution_x = _size
    _scene.render.resolution_y = _size
    _scene.render.resolution_percentage = 100
    _scene.render.filepath = _out
    bpy.ops.render.render(write_still=True)
    print("ICON_OK", _out)

# Restore the interactive authoring setup.
_cam.data.type = 'PERSP'
_sage = ROLE_COLORS["FACTION"]
_bsdf.inputs["Base Color"].default_value = _sage
_faction.diffuse_color = _sage
_scene.render.film_transparent = False
_sun.energy = 3.0
_bg.inputs[0].default_value = (0.913, 0.897, 0.869, 1.0)
"""


def bake(name: str) -> bool:
    script = PIECES_DIR / f"{name}.py"
    code = (
        (PIECES_DIR.parent / "_common.py").read_text()
        + "\n\n" + script.read_text()
        + "\n\n" + ICON_EPILOGUE.replace("{name}", name)
    )
    bridge = Bridge()  # one bridge per piece: stays under the 180s watchdog
    try:
        reply = bridge.call("execute_blender_code", {"code": code})
    finally:
        bridge.close()
    master = MASTER_DIR / f"piece_{name}.png"
    drawable = DRAWABLE_DIR / f"piece_{name}.png"
    ok = (
        reply.count("ICON_OK") >= 2
        and master.is_file() and master.stat().st_size > 0
        and drawable.is_file() and drawable.stat().st_size > 0
    )
    if not ok:
        print(f"FAIL {name}\n{reply}", file=sys.stderr)
    else:
        print(f"ok   {name}  ({master.stat().st_size}B / {drawable.stat().st_size}B)")
    return ok


def main() -> int:
    all_pieces = sorted(p.stem for p in PIECES_DIR.glob("*.py"))
    targets = sys.argv[1:] or all_pieces
    unknown = [t for t in targets if t not in all_pieces]
    if unknown:
        print(f"unknown piece(s): {', '.join(unknown)}\navailable: {', '.join(all_pieces)}",
              file=sys.stderr)
        return 2
    failed = [t for t in targets if not bake(t)]
    if failed:
        print(f"{len(failed)} piece(s) failed: {', '.join(failed)}", file=sys.stderr)
        return 1
    print(f"baked {len(targets)} icon(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
