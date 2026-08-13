# VIKINGS_MINE — timber-framed adit: stone mound + A-frame portal + plank
# canopy + dark entrance wedge + ore glints + faction flag. H ~0.30, ~204 tris.
KIND = "VIKINGS_MINE"
PIECE = "mine"
coll = reset_piece(KIND)

# Rock mound (two stacked frustums).
add_cyl(coll, "STONE", r=0.17, h=0.14, z0=0, r_top=0.09)
add_cyl(coll, "STONE", r=0.09, h=0.05, z0=0.14, r_top=0.05)

# Timber A-frame portal on the front: two angled beams + tie lintel.
add_box(coll, "TRUNK", 0.026, 0.026, 0.17, z0=0.004, x=-0.06, y=-0.15,
        rot=(radians(-8), radians(18), 0))
add_box(coll, "TRUNK", 0.026, 0.026, 0.17, z0=0.004, x=0.06, y=-0.15,
        rot=(radians(-8), radians(-18), 0))
add_box(coll, "TRUNK", 0.11, 0.03, 0.028, z0=0.135, y=-0.157)

# Plank canopy shedding toward the front.
add_box(coll, "TRUNK", 0.16, 0.07, 0.016, z0=0.16, y=-0.145, rot=(radians(15), 0, 0))

# Dark triangular entrance mouth (gable face toward -Y).
add_wedge(coll, "PIP", 0.09, 0.035, 0.085, z0=0, y=-0.148)

# Gold ore glints beside the entrance.
add_sphere(coll, "GOLD", r=0.030, z=0.028, seg=6, rings=3, x=0.135, y=-0.14)
add_sphere(coll, "GOLD", r=0.024, z=0.022, seg=6, rings=3, x=0.180, y=-0.09)
add_sphere(coll, "GOLD", r=0.020, z=0.058, seg=6, rings=3, x=0.150, y=-0.12)

# Faction claim flag planted on the mound (pennant helper lives at y=0).
add_box(coll, "PIP", 0.008, 0.008, 0.13, z0=0.17, x=0.06, y=0.0)
add_pennant(coll, "FACTION", pole_x=0.06, top_z=0.30, drop=0.05, length=0.09)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.15)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
