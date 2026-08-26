# SHOGUNATE BARRACKS — ashigaru dojo: raised hall with two stacked faction
# eave roofs, ink sliding-door band, nobori banner at the yard corner,
# training pell. H ~0.40, ~160 tris.
KIND = "SHOGUNATE_BARRACKS"
PIECE = "barracks"
coll = reset_piece(KIND)

# Raised floor + hall, set back for the yard.
add_box(coll, "TRUNK", 0.28, 0.20, 0.02, z0=0, y=0.03)
add_box(coll, "STONE", 0.24, 0.16, 0.12, z0=0.02, y=0.03)

# Ink sliding-door band on the front.
add_box(coll, "PIP", 0.14, 0.008, 0.07, z0=0.04, y=-0.052)

# Stacked eaves with a slim upper storey, ridge beam on top.
add_wedge(coll, "FACTION", 0.30, 0.22, 0.05, z0=0.14, y=0.03)
add_box(coll, "STONE", 0.13, 0.10, 0.09, z0=0.19, y=0.03)
add_wedge(coll, "FACTION", 0.19, 0.15, 0.06, z0=0.28, y=0.03)
add_box(coll, "TRUNK", 0.02, 0.17, 0.014, z0=0.34, y=0.03)

# Nobori banner on a pole at the yard corner.
add_cyl(coll, "TRUNK", r=0.006, h=0.40, z0=0, seg=6, x=0.15, y=-0.13)
add_box(coll, "FACTION", 0.045, 0.006, 0.15, z0=0.24, x=0.176, y=-0.13)

# Training pell.
add_cyl(coll, "STONE", r=0.028, h=0.016, z0=0, seg=8, x=-0.10, y=-0.14)
add_cyl(coll, "TRUNK", r=0.010, h=0.10, z0=0.016, seg=6, x=-0.10, y=-0.14)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.19)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
