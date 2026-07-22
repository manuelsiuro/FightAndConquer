# MARKET — trade stall: counter + corner posts + faction awning (the ownership
# read) + coin stack + crates. H ~0.31, ~250 tris.
KIND = "MARKET"
PIECE = "market"
coll = reset_piece(KIND)

# Counter.
add_box(coll, "TRUNK", 0.24, 0.16, 0.10, z0=0)

# Corner posts holding the awning.
for (x, y) in ((-0.13, -0.09), (0.13, -0.09), (-0.13, 0.09), (0.13, 0.09)):
    add_box(coll, "TRUNK", 0.024, 0.024, 0.24, z0=0, x=x, y=y)

# Faction awning: gabled wedge with a slight overhang.
add_wedge(coll, "FACTION", 0.30, 0.22, 0.07, z0=0.24)

# Coin stack on the counter.
add_cyl(coll, "GOLD", r=0.030, h=0.012, z0=0.100, x=0.05, y=0.03)
add_cyl(coll, "GOLD", r=0.030, h=0.012, z0=0.112, x=0.05, y=0.03)
add_cyl(coll, "GOLD", r=0.026, h=0.012, z0=0.124, x=0.055, y=0.025)

# Crates by the stall.
add_box(coll, "TRUNK", 0.06, 0.06, 0.06, z0=0, x=-0.19, y=-0.14)
add_box(coll, "TRUNK", 0.05, 0.05, 0.05, z0=0, x=-0.12, y=-0.18)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.15)
print("exported:", export_piece(PIECE, coll))
