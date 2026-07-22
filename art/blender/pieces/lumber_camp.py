# LUMBER_CAMP — log camp: pyramid log pile + stump with axe + faction lean-to.
# H ~0.19, ~260 tris.
KIND = "LUMBER_CAMP"
PIECE = "lumber_camp"
coll = reset_piece(KIND)

# Log pile: 3 + 2 pyramid, log axes along Y (rotation bakes into the mesh).
for (x, z) in ((-0.07, 0.035), (0.0, 0.035), (0.07, 0.035)):
    add_cyl(coll, "TRUNK", r=0.035, h=0.22, z0=0, seg=7, x=x, y=0.06,
            rot=(radians(90), 0, 0), z_center=z)
for (x, z) in ((-0.035, 0.095), (0.035, 0.095)):
    add_cyl(coll, "TRUNK", r=0.035, h=0.20, z0=0, seg=7, x=x, y=0.06,
            rot=(radians(90), 0, 0), z_center=z)

# Stump with an axe sunk into it.
add_cyl(coll, "TRUNK", r=0.05, h=0.07, z0=0, x=0.17, y=-0.14)
add_box(coll, "TRUNK", 0.014, 0.014, 0.12, z0=0.06, x=0.155, y=-0.155,
        rot=(radians(20), radians(-15), 0))
add_box(coll, "STONE", 0.05, 0.016, 0.04, z0=0.150, x=0.132, y=-0.178)

# Faction lean-to shelter.
add_box(coll, "TRUNK", 0.02, 0.02, 0.13, z0=0, x=-0.21, y=-0.02)
add_box(coll, "TRUNK", 0.02, 0.02, 0.13, z0=0, x=-0.21, y=-0.18)
add_wedge(coll, "FACTION", 0.16, 0.22, 0.06, z0=0.13, x=-0.17, y=-0.10)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.10)
print("exported:", export_piece(PIECE, coll))
