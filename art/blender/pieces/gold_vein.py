# GOLD_VEIN — ore outcrop: three rock humps in an edge arc with gold nuggets.
# Terrain deposit: low (H ~0.06), hex center left clear for units. ~370 tris.
KIND = "GOLD_VEIN"
PIECE = "gold_vein"
coll = reset_piece(KIND)

# Rock humps (squashed spheres resting on the ground, minY >= -0.01).
add_sphere(coll, "STONE", r=0.07, z=0.034, x=0.24, y=0.10, scale=(1, 1, 0.6))
add_sphere(coll, "STONE", r=0.06, z=0.030, x=0.10, y=-0.26, scale=(1, 1, 0.6))
add_sphere(coll, "STONE", r=0.065, z=0.032, x=-0.22, y=0.14, scale=(1, 1, 0.62))

# Nuggets nested in the crevices.
add_sphere(coll, "GOLD", r=0.026, z=0.062, seg=6, rings=3, x=0.27, y=0.13)
add_sphere(coll, "GOLD", r=0.020, z=0.058, seg=6, rings=3, x=0.20, y=0.05)
add_sphere(coll, "GOLD", r=0.024, z=0.055, seg=6, rings=3, x=0.07, y=-0.29)
add_sphere(coll, "GOLD", r=0.020, z=0.058, seg=6, rings=3, x=-0.19, y=0.18)
add_sphere(coll, "GOLD", r=0.018, z=0.060, seg=6, rings=3, x=-0.26, y=0.11)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.05)
print("exported:", export_piece(PIECE, coll))
