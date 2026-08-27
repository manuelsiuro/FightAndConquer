# TOWER_LIT — the tower with its beacon lit: crenellated turret + a stone
# brazier bowl and gold flame rising from the crown. H ~0.49, ~230 tris.
KIND = "TOWER_LIT"
PIECE = "tower_lit"
coll = reset_piece(KIND)

add_cyl(coll, "STONE", r=0.16, h=0.05, z0=0)
add_cyl(coll, "STONE", r=0.14, h=0.255, z0=0.05, r_top=0.115)
add_cyl(coll, "FACTION", r=0.145, h=0.045, z0=0.305)
add_cyl(coll, "STONE", r=0.15, h=0.045, z0=0.35)
for k in range(5):
    a = 2 * math.pi * k / 5
    add_box(coll, "STONE", 0.075, 0.055, 0.07, z0=0.395,
            x=0.115 * math.cos(a), y=0.115 * math.sin(a), rot=(0, 0, a))
for k in range(3):
    a = radians(90) + 2 * math.pi * k / 3
    add_box(coll, "PIP", 0.016, 0.02, 0.09, z0=0.14,
            x=0.132 * math.cos(a), y=0.132 * math.sin(a), rot=(0, 0, a))

# The beacon: stone bowl inside the crown + gold flame (the watchtower idiom).
add_cyl(coll, "STONE", r=0.05, h=0.03, z0=0.395, seg=6)
add_sphere(coll, "GOLD", r=0.034, z=0.455, seg=6, rings=3)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.24)
print("exported:", export_piece(PIECE, coll))
