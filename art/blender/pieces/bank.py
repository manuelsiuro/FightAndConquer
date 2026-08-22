# BANK — squat stronghouse: stone vault on a plinth lip, faction roof, barred
# ink slit door, coin stacks and a small chest. Economy band, peer of the
# market and mine. H ~0.30, ~250 tris.
KIND = "BANK"
PIECE = "bank"
coll = reset_piece(KIND)

# Plinth lip + vault block.
add_box(coll, "STONE", 0.30, 0.24, 0.03, z0=0)
add_box(coll, "STONE", 0.26, 0.20, 0.15, z0=0.03)

# Faction roof — the ownership read.
add_wedge(coll, "FACTION", 0.30, 0.24, 0.12, z0=0.18)

# Barred slit door on the front (-Y): slit + one crossbar.
add_box(coll, "PIP", 0.07, 0.012, 0.09, z0=0.05, y=-0.104)
add_box(coll, "PIP", 0.09, 0.010, 0.014, z0=0.085, y=-0.106)

# The takings: coin stacks and a strongbox by the door.
add_cyl(coll, "GOLD", r=0.030, h=0.012, z0=0.0, x=0.17, y=-0.09)
add_cyl(coll, "GOLD", r=0.030, h=0.012, z0=0.012, x=0.17, y=-0.09)
add_cyl(coll, "GOLD", r=0.026, h=0.012, z0=0.024, x=0.175, y=-0.085)
add_box(coll, "TRUNK", 0.06, 0.05, 0.04, z0=0, x=-0.17, y=-0.10)
add_box(coll, "GOLD", 0.05, 0.04, 0.012, z0=0.04, x=-0.17, y=-0.10)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.15)
print("exported:", export_piece(PIECE, coll))
