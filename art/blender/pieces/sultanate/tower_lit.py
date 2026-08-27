# SULTANATE_TOWER_LIT — the minaret guard tower with its beacon lit: an oil
# lamp on the balcony ring — stone bowl + gold flame at the front. H ~0.52, ~170 tris.
KIND = "SULTANATE_TOWER_LIT"
PIECE = "tower_lit"
coll = reset_piece(KIND)

# Base disc + slender tapering shaft.
add_cyl(coll, "STONE", r=0.13, h=0.04, z0=0, seg=6)
add_cyl(coll, "STONE", r=0.085, h=0.32, z0=0.04, r_top=0.065)

# Faction balcony ring near the top — the big faction surface.
add_cyl(coll, "FACTION", r=0.10, h=0.04, z0=0.36)

# Faction dome cap.
add_sphere(coll, "FACTION", r=0.065, z=0.43, seg=6, rings=3, scale=(1, 1, 1.1))

# Gold crescent — two angled boxes forming an open V above the dome.
add_box(coll, "GOLD", 0.011, 0.011, 0.05, z0=0.49, x=-0.014, rot=(0, -radians(40), 0))
add_box(coll, "GOLD", 0.011, 0.011, 0.05, z0=0.49, x=0.014, rot=(0, radians(40), 0))

# The beacon: an oil lamp on the balcony's front rim.
add_cyl(coll, "STONE", r=0.022, h=0.022, z0=0.40, seg=6, y=-0.085)
add_sphere(coll, "GOLD", r=0.026, z=0.448, seg=6, rings=3, y=-0.085)

# Two ink arrow slits on the front (-Y) — guard-tower presence.
for sx in (-0.032, 0.032):
    add_box(coll, "PIP", 0.016, 0.02, 0.09, z0=0.13, x=sx, y=-0.073)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.26)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
