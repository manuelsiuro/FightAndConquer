# SULTANATE WARSHIP — war dhow: longer hull with a pip waterline ram, tall
# raked mast flying a big triangular faction lateen sail along its angled yard,
# gold crescent masthead, round faction shields on both gunwales. Front faces
# -Y. H 0.51, ~268 tris.
KIND = "SULTANATE_WARSHIP"
PIECE = "warship"
coll = reset_piece(KIND)

# Hull: longer than the cargo dhow (0.58 vs 0.46), raised stern deck.
add_box(coll, "TRUNK", 0.15, 0.52, 0.05, z0=0)
add_box(coll, "TRUNK", 0.19, 0.58, 0.045, z0=0.05)
add_box(coll, "TRUNK", 0.13, 0.10, 0.05, z0=0.095, y=0.23)  # stern deck
# Pip waterline ram driving forward.
add_wedge(coll, "PIP", 0.09, 0.10, 0.05, z0=0.005, y=-0.33)
# Gunwale strakes.
add_box(coll, "PIP", 0.012, 0.50, 0.012, z0=0.095, x=0.092)
add_box(coll, "PIP", 0.012, 0.50, 0.012, z0=0.095, x=-0.092)

# Round faction shields along both gunwales, facing outward.
for side in (-1, 1):
    for yy in (-0.16, 0.0, 0.16):
        add_cyl(coll, "FACTION", r=0.034, h=0.012, z0=0, seg=6,
                x=side * 0.098, y=yy, rot=(0, radians(90), 0), z_center=0.115)

# Tall raked mast (top leans toward the bow).
add_cyl(coll, "TRUNK", r=0.013, h=0.36, z0=0.085, seg=6, y=0.04,
        rot=(radians(8), 0, 0))
# Big triangular lateen sail: thin wedge turned so the triangle stands in the
# Y-Z plane (base along the deck, apex up), with the yard running along its
# forward edge from the low tack to the peak.
add_wedge(coll, "FACTION", 0.36, 0.010, 0.26, z0=0.11, y=-0.02,
          rot_z=radians(90))
add_box(coll, "TRUNK", 0.016, 0.40, 0.016, z0=0.233, y=-0.11,
        rot=(radians(55), 0, 0))

# Gold crescent masthead: short bar with two horns curving up and outward.
add_cyl(coll, "GOLD", r=0.014, h=0.05, z0=0, seg=6, y=-0.01,
        rot=(0, radians(90), 0), z_center=0.45)
add_cyl(coll, "GOLD", r=0.011, h=0.05, z0=0, seg=6, x=-0.025, y=-0.01,
        r_top=0.0, rot=(0, radians(-20), 0), z_center=0.48)
add_cyl(coll, "GOLD", r=0.011, h=0.05, z0=0, seg=6, x=0.025, y=-0.01,
        r_top=0.0, rot=(0, radians(20), 0), z_center=0.48)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.24)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
