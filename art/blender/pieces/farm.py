# FARM — farmhouse + gabled roof + silo + chimney + crop ridges. H 0.23, ~122 tris.
KIND = "FARM"
PIECE = "farm"
coll = reset_piece(KIND)

add_box(coll, "FACTION", 0.20, 0.14, 0.10, z0=0, x=-0.13, y=-0.06)
add_wedge(coll, "TRUNK", 0.24, 0.16, 0.085, z0=0.10, x=-0.13, y=-0.06)
add_cyl(coll, "TRUNK", r=0.055, h=0.045, z0=0.185, r_top=0, x=-0.10, y=0.135)
add_box(coll, "STONE", 0.035, 0.035, 0.10, z0=0.13, x=-0.185, y=-0.10)
add_cyl(coll, "STONE", r=0.05, h=0.185, z0=0, x=-0.10, y=0.135)
for x in (0.10, 0.17, 0.24):
    add_wedge(coll, "TREE_FOLIAGE", 0.055, 0.24, 0.045, z0=0, x=x)
add_box(coll, "PIP", 0.045, 0.015, 0.065, z0=0, x=-0.13, y=-0.135)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.12)
print("exported:", export_piece(PIECE, coll))
