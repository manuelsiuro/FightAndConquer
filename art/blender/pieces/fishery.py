# FISHERY — stilt hut with faction roof, net-drying rack and a gold catch.
# Front (sea side) faces -Y. H 0.34, ~250 tris.
KIND = "FISHERY"
PIECE = "fishery"
coll = reset_piece(KIND)

# Stilt platform over the beach.
for xx in (-0.10, 0.10):
    for yy in (-0.10, 0.06):
        add_cyl(coll, "TRUNK", r=0.014, h=0.07, z0=0, seg=6, x=xx, y=yy)
add_box(coll, "TRUNK", 0.28, 0.22, 0.025, z0=0.07, y=-0.02)

# Hut with faction gable roof.
add_box(coll, "TRUNK", 0.16, 0.13, 0.10, z0=0.095, x=0.03, y=0.03)
add_wedge(coll, "FACTION", 0.19, 0.15, 0.07, z0=0.195, x=0.03, y=0.03)

# Net-drying rack: two posts and a thin lattice.
add_box(coll, "TRUNK", 0.014, 0.014, 0.15, z0=0.095, x=-0.11, y=-0.09)
add_box(coll, "TRUNK", 0.014, 0.014, 0.15, z0=0.095, x=0.11, y=-0.09)
add_box(coll, "TRUNK", 0.24, 0.012, 0.012, z0=0.235, y=-0.09)
add_box(coll, "PIP", 0.22, 0.006, 0.09, z0=0.14, y=-0.09)

# The catch: gold fish hanging on the rack + a basket on the deck.
add_sphere(coll, "GOLD", r=0.022, z=0.20, x=-0.05, y=-0.095, scale=(1.0, 0.5, 1.6))
add_sphere(coll, "GOLD", r=0.022, z=0.19, x=0.04, y=-0.095, scale=(1.0, 0.5, 1.6))
add_cyl(coll, "STONE", r=0.035, h=0.04, z0=0.095, seg=7, x=-0.09, y=0.05)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.17)
print("exported:", export_piece(PIECE, coll))
