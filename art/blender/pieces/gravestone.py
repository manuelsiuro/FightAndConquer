# GRAVESTONE — round-top slab + base pad + grass tufts. H 0.24, ~72 tris.
KIND = "GRAVESTONE"
PIECE = "gravestone"
coll = reset_piece(KIND)

add_box(coll, "STONE", 0.16, 0.09, 0.02, z0=0)
add_box(coll, "STONE", 0.20, 0.06, 0.12, z0=0.02)
add_cyl(coll, "STONE", r=0.10, h=0.056, z0=0, seg=8,
        rot=(radians(90), 0, 0), z_center=0.14)
add_cyl(coll, "TREE_FOLIAGE", r=0.02, h=0.05, z0=0, seg=6, r_top=0, x=0.09, y=-0.03)
add_cyl(coll, "TREE_FOLIAGE", r=0.02, h=0.05, z0=0, seg=6, r_top=0, x=-0.09, y=-0.03)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.12)
print("exported:", export_piece(PIECE, coll))
