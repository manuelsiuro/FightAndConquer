# STRONG_TOWER — twin turrets + faction wall + ink gate. H 0.51, ~200 tris.
KIND = "STRONG_TOWER"
PIECE = "strong_tower"
coll = reset_piece(KIND)

for sx in (-0.155, 0.155):
    add_cyl(coll, "STONE", r=0.095, h=0.42, z0=0, seg=6, x=sx)
    add_cyl(coll, "STONE", r=0.115, h=0.035, z0=0.42, seg=6, x=sx)
    for deg in (90, 210, 330):
        a = radians(deg)
        add_box(coll, "STONE", 0.06, 0.048, 0.055, z0=0.455,
                x=sx + 0.085 * math.cos(a), y=0.085 * math.sin(a), rot=(0, 0, a))

add_box(coll, "FACTION", 0.20, 0.11, 0.26, z0=0)
add_box(coll, "FACTION", 0.055, 0.11, 0.055, z0=0.26, x=-0.05)
add_box(coll, "FACTION", 0.055, 0.11, 0.055, z0=0.26, x=0.05)
add_box(coll, "PIP", 0.09, 0.13, 0.14, z0=0)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.25)
print("exported:", export_piece(PIECE, coll))
