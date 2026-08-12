# SULTANATE FISHERY — net pier: stilt deck with a tall drying frame draping a
# pip net (thin sloped panel + crossing strips), hanging fish wedges, a stone
# basket, rolled net, and a faction canopy strip at the back. Front (sea side)
# faces -Y. H 0.30, ~268 tris.
KIND = "SULTANATE_FISHERY"
PIECE = "fishery"
coll = reset_piece(KIND)

# Stilt pier deck.
for xx in (-0.10, 0.10):
    for yy in (-0.08, 0.08):
        add_cyl(coll, "TRUNK", r=0.014, h=0.07, z0=0, seg=6, x=xx, y=yy)
add_box(coll, "TRUNK", 0.28, 0.24, 0.025, z0=0.07, y=-0.01)

# Drying frame on the sea side: two posts + crossbar.
add_box(coll, "TRUNK", 0.016, 0.016, 0.20, z0=0.095, x=-0.11, y=-0.10)
add_box(coll, "TRUNK", 0.016, 0.016, 0.20, z0=0.095, x=0.11, y=-0.10)
add_box(coll, "TRUNK", 0.25, 0.013, 0.013, z0=0.283, y=-0.10)

# Draped pip net: thin sloped panel + two crossing diagonal strips.
add_box(coll, "PIP", 0.21, 0.006, 0.17, z0=0.10, y=-0.085, rot=(radians(12), 0, 0))
add_box(coll, "PIP", 0.005, 0.006, 0.20, z0=0.09, x=-0.05, y=-0.085,
        rot=(radians(12), radians(35), 0))
add_box(coll, "PIP", 0.005, 0.006, 0.20, z0=0.09, x=0.05, y=-0.085,
        rot=(radians(12), radians(-35), 0))

# Hanging fish on the crossbar.
for xx in (-0.06, 0.0, 0.06):
    add_wedge(coll, "PIP", 0.018, 0.032, 0.032, z0=0.245, x=xx, y=-0.10)

# Basket and a rolled spare net on the deck.
add_cyl(coll, "STONE", r=0.035, h=0.045, z0=0.095, seg=7, x=-0.08, y=0.05)
add_cyl(coll, "PIP", r=0.018, h=0.14, z0=0, seg=6, x=0.06, y=0.06,
        rot=(0, radians(90), 0), z_center=0.113)

# Faction canopy strip over the back of the deck (ownership read).
add_box(coll, "TRUNK", 0.014, 0.014, 0.15, z0=0.095, x=-0.095, y=0.08)
add_box(coll, "TRUNK", 0.014, 0.014, 0.15, z0=0.095, x=0.095, y=0.08)
add_box(coll, "FACTION", 0.22, 0.11, 0.014, z0=0.245, y=0.08,
        rot=(radians(-6), 0, 0))

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.15)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
