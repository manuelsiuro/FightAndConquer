# SULTANATE UNIVERSITY — madrasa: sandstone block behind a pointed portal, low
# gold dome on a faction drum, one slim corner minaret, faction portal awning.
# H ~0.41, ~320 tris.
KIND = "SULTANATE_UNIVERSITY"
PIECE = "university"
coll = reset_piece(KIND)

# Sandstone block.
add_box(coll, "STONE", 0.28, 0.24, 0.18, z0=0)

# Faction drum + low gold dome.
add_cyl(coll, "FACTION", r=0.10, h=0.04, z0=0.18)
add_sphere(coll, "GOLD", r=0.105, z=0.23, seg=8, rings=4, scale=(1, 1, 0.85))

# Slim corner minaret: tapering shaft, faction cap, gold tip.
add_cyl(coll, "STONE", r=0.028, h=0.34, z0=0, seg=6, r_top=0.020, x=0.15, y=-0.14)
add_sphere(coll, "FACTION", r=0.030, z=0.352, seg=6, rings=3, x=0.15, y=-0.14)
add_cyl(coll, "GOLD", r=0.012, h=0.030, z0=0.372, seg=5, r_top=0, x=0.15, y=-0.14)

# Pointed ink portal + faction awning over it (front, -Y).
add_box(coll, "PIP", 0.07, 0.02, 0.10, z0=0.02, y=-0.115)
add_wedge(coll, "PIP", 0.07, 0.02, 0.035, z0=0.12, y=-0.115)
add_box(coll, "FACTION", 0.11, 0.035, 0.015, z0=0.165, y=-0.125)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.20)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
