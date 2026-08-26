# ARCHERY_RANGE — shooting lane: straw target butt on a tripod facing the
# front, faction ring + ink bullseye, low fence rail down the lane, open
# archer shelter at the back, tall pennant pole. The big target IS the read.
# H ~0.33, ~240 tris.
KIND = "ARCHERY_RANGE"
PIECE = "archery_range"
coll = reset_piece(KIND)

# Target butt at the front (-Y): straw disc, faction ring, ink bullseye.
add_cyl(coll, "GOLD", r=0.085, h=0.030, z0=0, seg=12, y=-0.12,
        rot=(radians(90), 0, 0), z_center=0.12)
add_cyl(coll, "FACTION", r=0.055, h=0.010, z0=0, seg=12, y=-0.137,
        rot=(radians(90), 0, 0), z_center=0.12)
add_cyl(coll, "PIP", r=0.022, h=0.010, z0=0, seg=8, y=-0.142,
        rot=(radians(90), 0, 0), z_center=0.12)

# Tripod under the butt: two splayed front legs, one back leg.
add_box(coll, "TRUNK", 0.012, 0.012, 0.15, z0=0, x=-0.05, y=-0.115,
        rot=(0, radians(20), 0))
add_box(coll, "TRUNK", 0.012, 0.012, 0.15, z0=0, x=0.05, y=-0.115,
        rot=(0, radians(-20), 0))
add_box(coll, "TRUNK", 0.012, 0.012, 0.15, z0=0, y=-0.075,
        rot=(radians(-22), 0, 0))

# Open archer shelter at the back (+Y): two posts, faction lean-to roof.
add_box(coll, "TRUNK", 0.018, 0.018, 0.12, z0=0, x=-0.06, y=0.15)
add_box(coll, "TRUNK", 0.018, 0.018, 0.12, z0=0, x=0.06, y=0.15)
add_wedge(coll, "FACTION", 0.18, 0.14, 0.05, z0=0.12, y=0.13)

# Low fence rail along the west side of the lane.
add_box(coll, "TRUNK", 0.012, 0.012, 0.05, z0=0, x=-0.16, y=-0.02)
add_box(coll, "TRUNK", 0.012, 0.012, 0.05, z0=0, x=-0.16, y=0.10)
add_box(coll, "TRUNK", 0.012, 0.16, 0.012, z0=0.045, x=-0.16, y=0.04)

# Tall pennant pole beside the butt (y=0 plane).
add_cyl(coll, "TRUNK", r=0.007, h=0.33, z0=0, seg=6, x=0.15)
add_pennant(coll, "FACTION", 0.15, 0.33, 0.04, 0.075)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.15)
print("exported:", export_piece(PIECE, coll))
