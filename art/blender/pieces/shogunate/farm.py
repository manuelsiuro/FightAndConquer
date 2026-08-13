# SHOGUNATE_FARM — paddy terrace: two flooded foliage steps + trunk bund edges
# + tiny faction-roofed hut + gold rice sheaves. H ~0.21, ~132 tris.
KIND = "SHOGUNATE_FARM"
PIECE = "farm"
coll = reset_piece(KIND)

# Two low flooded terraces (broad discs, upper one offset).
add_cyl(coll, "TREE_FOLIAGE", r=0.30, h=0.035, z0=0)
add_cyl(coll, "TREE_FOLIAGE", r=0.21, h=0.035, z0=0.035, x=-0.03, y=0.03)

# Bund edges — thin trunk boxes hugging the terrace rims.
add_box(coll, "TRUNK", 0.20, 0.018, 0.024, z0=0.028, y=-0.275)
add_box(coll, "TRUNK", 0.20, 0.018, 0.024, z0=0.028, x=0.24, y=-0.13,
        rot=(0, 0, radians(60)))
add_box(coll, "TRUNK", 0.16, 0.018, 0.024, z0=0.063, x=-0.03, y=-0.155)

# Tiny hut on the upper terrace with a wide-eaved faction roof.
add_box(coll, "TRUNK", 0.12, 0.10, 0.07, z0=0.07, x=-0.05, y=0.06)
add_wedge(coll, "FACTION", 0.19, 0.16, 0.065, z0=0.14, x=-0.05, y=0.06)

# Gold rice sheaves drying on the lower terrace.
add_cyl(coll, "GOLD", r=0.030, h=0.055, z0=0.035, seg=6, r_top=0, x=0.16, y=-0.12)
add_cyl(coll, "GOLD", r=0.026, h=0.048, z0=0.035, seg=6, r_top=0, x=0.21, y=-0.03)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.10)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
