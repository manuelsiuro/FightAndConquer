# FISH_SHOAL — sea deposit: leaping fish fins + ripple rings, scattered toward
# the hex edge so a boat can share the hex visually. H 0.08, ~110 tris.
KIND = "FISH_SHOAL"
PIECE = "fish_shoal"
coll = reset_piece(KIND)

# Leaping fins (thin wedges) at three scatter points.
add_wedge(coll, "PIP", 0.05, 0.016, 0.05, z0=0.005, x=0.22, y=0.10, rot_z=radians(25))
add_wedge(coll, "PIP", 0.04, 0.014, 0.04, z0=0.005, x=0.06, y=-0.25, rot_z=radians(-40))
add_wedge(coll, "PIP", 0.045, 0.015, 0.045, z0=0.005, x=-0.21, y=0.13, rot_z=radians(80))
# A gold splash where the big one just jumped.
add_sphere(coll, "GOLD", r=0.02, z=0.03, x=-0.04, y=0.24, scale=(1.0, 0.6, 1.3))

# Ripple rings: flat thin cylinders barely above the water.
add_cyl(coll, "STONE", r=0.055, h=0.006, z0=0.001, seg=10, x=0.22, y=0.10)
add_cyl(coll, "STONE", r=0.045, h=0.006, z0=0.001, seg=10, x=0.06, y=-0.25)
add_cyl(coll, "STONE", r=0.05, h=0.006, z0=0.001, seg=10, x=-0.21, y=0.13)
add_cyl(coll, "STONE", r=0.04, h=0.006, z0=0.001, seg=10, x=-0.04, y=0.24)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.06)
print("exported:", export_piece(PIECE, coll))
