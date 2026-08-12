# SULTANATE_LUMBER_CAMP — sawyer's yard: stacked palm logs under a FACTION tarp
# + frame-saw arch with hanging PIP blade over a work log + frond pile + stump.
# H ~0.20, ~240 tris.
KIND = "SULTANATE_LUMBER_CAMP"
PIECE = "lumber_camp"
coll = reset_piece(KIND)

# Palm log pile at the back: 3 + 2, log axes along X (rotation bakes into mesh).
for (y, z) in ((0.05, 0.035), (0.12, 0.035), (0.19, 0.035)):
    add_cyl(coll, "TRUNK", r=0.035, h=0.20, z0=0, seg=7, y=y,
            rot=(0, radians(90), 0), z_center=z)
for (y, z) in ((0.085, 0.095), (0.155, 0.095)):
    add_cyl(coll, "TRUNK", r=0.035, h=0.20, z0=0, seg=7, y=y,
            rot=(0, radians(90), 0), z_center=z)

# Faction tarp draped over the pile, ridge along X — the big faction surface.
add_wedge(coll, "FACTION", 0.18, 0.26, 0.06, z0=0.11, y=0.12, rot_z=radians(90))

# Frame-saw arch on the front: two posts + top beam + hanging ink blade + work log.
add_box(coll, "TRUNK", 0.025, 0.025, 0.17, z0=0, x=-0.08, y=-0.13)
add_box(coll, "TRUNK", 0.025, 0.025, 0.17, z0=0, x=0.08, y=-0.13)
add_box(coll, "TRUNK", 0.20, 0.03, 0.03, z0=0.17, y=-0.13)
add_box(coll, "PIP", 0.012, 0.010, 0.12, z0=0.05, y=-0.13)
add_cyl(coll, "TRUNK", r=0.03, h=0.18, z0=0, seg=6, y=-0.13,
        rot=(0, radians(90), 0), z_center=0.03)

# Palm frond pile: tilted foliage cones.
for k in range(3):
    a = 0.4 + 2.0 * k
    add_cyl(coll, "TREE_FOLIAGE", r=0.04, h=0.10, z0=0, seg=5, r_top=0,
            x=-0.17 + 0.02 * math.cos(a), y=-0.02 + 0.02 * math.sin(a),
            rot=(radians(75), 0, a), z_center=0.045)

# Cutting stump.
add_cyl(coll, "TRUNK", r=0.045, h=0.06, z0=0, seg=6, x=0.18, y=-0.02)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.09)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
