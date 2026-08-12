# VIKINGS_FARM — turf-roofed longhouse: timber body + green turf roof + crossed
# gable beams + faction door + fenced yard. H ~0.27, ~160 tris.
KIND = "VIKINGS_FARM"
PIECE = "farm"
coll = reset_piece(KIND)

# Small longhouse, ridge along Y, at the west side of the tile.
add_box(coll, "TRUNK", 0.16, 0.26, 0.09, z0=0, x=-0.08)
add_wedge(coll, "TRUNK", 0.20, 0.30, 0.105, z0=0.09, x=-0.08)
# Turf laid over the roof (timber peeks out at eaves and gable ends).
add_wedge(coll, "TREE_FOLIAGE", 0.21, 0.22, 0.10, z0=0.10, x=-0.08)

# Crossed gable beams on the front end only.
for tilt in (radians(24), -radians(24)):
    add_box(coll, "PIP", 0.016, 0.016, 0.15, z0=0.13, x=-0.08, y=-0.148,
            rot=(0, tilt, 0))

# Faction door + lintel band on the front gable.
add_box(coll, "FACTION", 0.05, 0.018, 0.07, z0=0, x=-0.08, y=-0.133)
add_box(coll, "FACTION", 0.14, 0.012, 0.05, z0=0.09, x=-0.08, y=-0.132)

# Fenced yard: short timber posts + two rails.
for (px, py) in ((0.10, -0.12), (0.20, -0.12), (0.20, 0.0), (0.20, 0.12), (0.10, 0.12)):
    add_box(coll, "TRUNK", 0.016, 0.016, 0.055, z0=0, x=px, y=py)
add_box(coll, "TRUNK", 0.016, 0.26, 0.02, z0=0.028, x=0.20)
add_box(coll, "TRUNK", 0.11, 0.016, 0.02, z0=0.028, x=0.15, y=-0.12)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.13)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
