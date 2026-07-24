# PORT — coastal harbor: stone quay, timber crane with hanging crate,
# faction-roof warehouse, gold trade barrel, mooring bollards. H 0.42, ~300 tris.
KIND = "PORT"
PIECE = "port"
coll = reset_piece(KIND)

# Quay slab (sea side faces -Y) + steps.
add_box(coll, "STONE", 0.34, 0.24, 0.05, z0=0, y=0.02)
add_box(coll, "STONE", 0.20, 0.05, 0.03, z0=0, y=-0.125)

# Warehouse with faction gable roof.
add_box(coll, "TRUNK", 0.15, 0.13, 0.11, z0=0.05, x=0.08, y=0.07)
add_wedge(coll, "FACTION", 0.17, 0.15, 0.07, z0=0.16, x=0.08, y=0.07)

# Crane: post, angled jib over the water, rope and hanging crate.
add_cyl(coll, "TRUNK", r=0.018, h=0.32, z0=0.05, seg=6, x=-0.11, y=0.02)
add_box(coll, "TRUNK", 0.018, 0.20, 0.018, z0=0.345, x=-0.11, y=-0.06, rot=(radians(-14), 0, 0))
add_box(coll, "PIP", 0.006, 0.006, 0.12, z0=0.20, x=-0.11, y=-0.15)
add_box(coll, "STONE", 0.05, 0.05, 0.05, z0=0.15, x=-0.11, y=-0.15, rot=(0, 0, radians(15)))

# Gold trade barrel + mooring bollards on the quay edge.
add_cyl(coll, "GOLD", r=0.03, h=0.05, z0=0.05, seg=8, x=0.02, y=-0.05)
add_cyl(coll, "PIP", r=0.012, h=0.03, z0=0.05, seg=6, x=-0.15, y=-0.09)
add_cyl(coll, "PIP", r=0.012, h=0.03, z0=0.05, seg=6, x=0.14, y=-0.09)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.20)
print("exported:", export_piece(PIECE, coll))
