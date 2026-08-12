# VIKINGS_STRONG_TOWER — palisade fort: stone footing + ring of 6 sharpened
# stakes + faction watch hut + gold finial + ink gate. H ~0.60, ~222 tris.
KIND = "VIKINGS_STRONG_TOWER"
PIECE = "strong_tower"
coll = reset_piece(KIND)

# Stone footing + core that fills the palisade interior.
add_cyl(coll, "STONE", r=0.19, h=0.05, z0=0)
add_cyl(coll, "STONE", r=0.12, h=0.28, z0=0.05, seg=6)

# Ring of 6 sharpened timber stakes (gap centered on the front for the gate).
for k in range(6):
    a = 2 * math.pi * k / 6
    add_cyl(coll, "TRUNK", r=0.030, h=0.36, z0=0.05, seg=6, r_top=0.008,
            x=0.155 * math.cos(a), y=0.155 * math.sin(a))

# Ink gate in the front gap between stakes.
add_box(coll, "PIP", 0.08, 0.02, 0.13, z0=0.05, y=-0.155)

# Gabled watch hut rising above the stakes, faction walls + timber roof.
add_box(coll, "FACTION", 0.16, 0.13, 0.10, z0=0.33)
add_wedge(coll, "TRUNK", 0.19, 0.16, 0.10, z0=0.43)
add_box(coll, "FACTION", 0.07, 0.014, 0.09, z0=0.34, y=-0.073)

# Gold finial on the ridge.
add_cyl(coll, "GOLD", r=0.026, h=0.07, z0=0.53, seg=6, r_top=0)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.28)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
