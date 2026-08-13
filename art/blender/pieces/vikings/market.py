# VIKINGS MARKET — trading knarr hauled ashore on log rollers, faction awning
# over the open hold, stacked cargo and coin beside. H ~0.23, ~264 tris.
KIND = "VIKINGS_MARKET"
PIECE = "market"
coll = reset_piece(KIND)

# Log rollers under the beached hull.
for yy in (-0.13, 0.0, 0.13):
    add_cyl(coll, "TRUNK", r=0.020, h=0.24, z0=0, seg=6, x=-0.05, y=yy,
            rot=(0, radians(90), 0), z_center=0.020)

# Beached knarr hull resting on the rollers, prow toward the buyer (-Y).
add_box(coll, "TRUNK", 0.13, 0.34, 0.045, z0=0.04, x=-0.05)
add_box(coll, "TRUNK", 0.18, 0.40, 0.04, z0=0.085, x=-0.05)
add_cyl(coll, "TRUNK", r=0.026, h=0.09, z0=0.05, seg=6, x=-0.05, y=-0.195,
        r_top=0.016, rot=(radians(20), 0, 0))

# Faction awning strip on poles over the hold (the ownership read).
for (px, py) in ((-0.13, -0.10), (-0.13, 0.10), (0.03, -0.10), (0.03, 0.10)):
    add_box(coll, "TRUNK", 0.02, 0.02, 0.165, z0=0.04, x=px, y=py)
add_box(coll, "FACTION", 0.20, 0.24, 0.018, z0=0.195, x=-0.05, rot=(0, radians(8), 0))

# Cargo stacked beside the ship: crates, barrel, coin stack.
add_box(coll, "STONE", 0.07, 0.07, 0.06, z0=0, x=0.13, y=0.05)
add_box(coll, "STONE", 0.055, 0.055, 0.05, z0=0.06, x=0.125, y=0.045, rot=(0, 0, radians(18)))
add_cyl(coll, "TRUNK", r=0.030, h=0.06, z0=0, seg=6, x=0.14, y=-0.06)
add_cyl(coll, "GOLD", r=0.026, h=0.012, z0=0, x=0.10, y=-0.14)
add_cyl(coll, "GOLD", r=0.026, h=0.012, z0=0.012, x=0.10, y=-0.14)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.13)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
