# SHOGUNATE UNIVERSITY — terakoya school: raised machiya hall with two stacked
# faction eave roofs, open veranda posts, a hanging ink noren, a gold scroll
# case by the door. H ~0.39, ~330 tris.
KIND = "SHOGUNATE_UNIVERSITY"
PIECE = "university"
coll = reset_piece(KIND)

# Raised floor + hall.
add_box(coll, "TRUNK", 0.28, 0.24, 0.02, z0=0)
add_box(coll, "STONE", 0.24, 0.20, 0.12, z0=0.02)

# Veranda posts along the front (-Y).
for px in (-0.11, -0.04, 0.04, 0.11):
    add_box(coll, "TRUNK", 0.018, 0.018, 0.12, z0=0.02, x=px, y=-0.13)

# Lower eave, upper storey, upper eave (the stacked-roof read).
add_wedge(coll, "FACTION", 0.32, 0.28, 0.05, z0=0.14)
add_box(coll, "STONE", 0.16, 0.13, 0.13, z0=0.18)
add_wedge(coll, "FACTION", 0.22, 0.19, 0.07, z0=0.31)
add_box(coll, "TRUNK", 0.02, 0.20, 0.014, z0=0.38)

# Hanging noren strip under the lower eave + gold scroll case by the door.
add_box(coll, "PIP", 0.10, 0.008, 0.05, z0=0.085, y=-0.124)
add_cyl(coll, "GOLD", r=0.015, h=0.09, z0=0, seg=6, x=0.09, y=-0.135,
        rot=(0, radians(90), 0), z_center=0.035)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.19)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
