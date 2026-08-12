# SULTANATE_TOWER — minaret guard tower: slender STONE frustum + FACTION balcony
# ring + FACTION dome cap + GOLD crescent + front arrow slits. H ~0.52, ~148 tris.
KIND = "SULTANATE_TOWER"
PIECE = "tower"
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

# Two ink arrow slits on the front (-Y) — guard-tower presence.
for sx in (-0.032, 0.032):
    add_box(coll, "PIP", 0.016, 0.02, 0.09, z0=0.13, x=sx, y=-0.073)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.26)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
