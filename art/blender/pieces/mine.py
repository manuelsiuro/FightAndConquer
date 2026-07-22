# MINE — mine portal: rock mound + timber A-frame entrance + ore pile + cart
# rails + faction claim flag. H ~0.32 with flag, ~330 tris.
KIND = "MINE"
PIECE = "mine"
coll = reset_piece(KIND)

# Rock mound (two stacked frustums).
add_cyl(coll, "STONE", r=0.17, h=0.16, z0=0, r_top=0.10)
add_cyl(coll, "STONE", r=0.10, h=0.06, z0=0.16, r_top=0.055)

# Timber entrance frame on the front (-Y): two posts + lintel.
add_box(coll, "TRUNK", 0.022, 0.022, 0.10, z0=0, x=-0.06, y=-0.155, rot=(radians(-8), 0, 0))
add_box(coll, "TRUNK", 0.022, 0.022, 0.10, z0=0, x=0.06, y=-0.155, rot=(radians(-8), 0, 0))
add_box(coll, "TRUNK", 0.15, 0.028, 0.03, z0=0.095, y=-0.165)

# Dark entrance mouth.
add_box(coll, "PIP", 0.09, 0.02, 0.085, z0=0, y=-0.152)

# Ore pile beside the entrance.
add_sphere(coll, "GOLD", r=0.030, z=0.028, seg=6, rings=3, x=0.135, y=-0.14)
add_sphere(coll, "GOLD", r=0.024, z=0.022, seg=6, rings=3, x=0.180, y=-0.09)
add_sphere(coll, "GOLD", r=0.020, z=0.058, seg=6, rings=3, x=0.150, y=-0.12)

# Cart rails running out the front.
add_box(coll, "TRUNK", 0.012, 0.14, 0.012, z0=0, x=-0.03, y=-0.26)
add_box(coll, "TRUNK", 0.012, 0.14, 0.012, z0=0, x=0.03, y=-0.26)

# Faction claim flag planted on the mound (pennant helper lives at y=0).
add_box(coll, "PIP", 0.008, 0.008, 0.12, z0=0.20, x=0.06, y=0.0)
add_pennant(coll, "FACTION", pole_x=0.06, top_z=0.32, drop=0.05, length=0.09)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.15)
print("exported:", export_piece(PIECE, coll))
