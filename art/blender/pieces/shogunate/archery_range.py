# SHOGUNATE ARCHERY_RANGE — kyudo lane: faction torii frame at the front
# framing a straw target on a stand, tiered-eave shelter at the back, nobori
# banner. H ~0.36, ~220 tris.
KIND = "SHOGUNATE_ARCHERY_RANGE"
PIECE = "archery_range"
coll = reset_piece(KIND)

# Torii frame at the front (-Y): two pillars, curved-read double lintel.
add_cyl(coll, "FACTION", r=0.014, h=0.24, z0=0, seg=6, x=-0.10, y=-0.13)
add_cyl(coll, "FACTION", r=0.014, h=0.24, z0=0, seg=6, x=0.10, y=-0.13)
add_box(coll, "FACTION", 0.20, 0.022, 0.020, z0=0.20, y=-0.13)
add_box(coll, "FACTION", 0.27, 0.026, 0.024, z0=0.24, y=-0.13)
add_box(coll, "PIP", 0.026, 0.020, 0.040, z0=0.20, y=-0.13)

# Straw target on a stand, seen through the torii.
add_box(coll, "TRUNK", 0.016, 0.016, 0.09, z0=0, y=-0.04)
add_cyl(coll, "GOLD", r=0.055, h=0.026, z0=0, seg=12, y=-0.04,
        rot=(radians(90), 0, 0), z_center=0.13)
add_cyl(coll, "PIP", r=0.018, h=0.010, z0=0, seg=8, y=-0.056,
        rot=(radians(90), 0, 0), z_center=0.13)

# Tiered-eave shelter at the back (+Y).
add_box(coll, "TRUNK", 0.16, 0.10, 0.02, z0=0, y=0.14)
add_box(coll, "STONE", 0.13, 0.08, 0.07, z0=0.02, y=0.14)
add_wedge(coll, "FACTION", 0.19, 0.14, 0.05, z0=0.09, y=0.14)

# Nobori banner beside the lane.
add_cyl(coll, "TRUNK", r=0.006, h=0.36, z0=0, seg=6, x=0.16, y=0.02)
add_box(coll, "FACTION", 0.040, 0.006, 0.13, z0=0.22, x=0.183, y=0.02)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.15)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
