# SIEGE_WORKSHOP — engineer's yard: tall open-fronted work shed sheltering a
# half-built siege arm on an A-frame, spoked wheel leaned on one wall, log
# stock on the other, anvil in the yard, ridge pennant. The tall shed + wheel
# keep it distinct from the lumber camp (0.19). H ~0.35, ~230 tris.
KIND = "SIEGE_WORKSHOP"
PIECE = "siege_workshop"
coll = reset_piece(KIND)

# Shed: back wall + two side walls, open at the front (-Y), faction roof.
add_box(coll, "TRUNK", 0.30, 0.02, 0.18, z0=0, y=0.12)
add_box(coll, "TRUNK", 0.02, 0.20, 0.18, z0=0, x=-0.14, y=0.03)
add_box(coll, "TRUNK", 0.02, 0.20, 0.18, z0=0, x=0.14, y=0.03)
add_wedge(coll, "FACTION", 0.34, 0.26, 0.09, z0=0.18, y=0.03)

# Ridge pennant (y=0 plane).
add_cyl(coll, "TRUNK", r=0.007, h=0.08, z0=0.27, seg=6)
add_pennant(coll, "FACTION", 0.0, 0.35, 0.04, 0.075)

# Half-built siege arm inside: A-frame legs crossing in X, angled throw beam.
add_box(coll, "TRUNK", 0.016, 0.016, 0.15, z0=0, x=-0.045, y=0.03,
        rot=(0, radians(18), 0))
add_box(coll, "TRUNK", 0.016, 0.016, 0.15, z0=0, x=0.045, y=0.03,
        rot=(0, radians(-18), 0))
add_box(coll, "TRUNK", 0.020, 0.026, 0.24, z0=0.02, y=0.03,
        rot=(radians(-38), 0, 0))

# Spoked wheel leaned on the east wall, gold hub.
add_cyl(coll, "TRUNK", r=0.065, h=0.018, z0=0, seg=10, x=0.165, y=-0.02,
        rot=(0, radians(78), 0), z_center=0.065)
add_sphere(coll, "GOLD", r=0.012, z=0.065, seg=6, rings=3, x=0.165, y=-0.02)

# Log stock along the west wall.
add_cyl(coll, "TRUNK", r=0.028, h=0.18, z0=0, seg=7, x=-0.19, y=0.02,
        rot=(radians(90), 0, 0), z_center=0.028)
add_cyl(coll, "TRUNK", r=0.028, h=0.16, z0=0, seg=7, x=-0.19, y=0.02,
        rot=(radians(90), 0, 0), z_center=0.078)

# Anvil in the yard: stone block, gold face.
add_box(coll, "STONE", 0.032, 0.032, 0.030, z0=0, x=0.06, y=-0.14)
add_box(coll, "GOLD", 0.050, 0.020, 0.018, z0=0.03, x=0.06, y=-0.14)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.17)
print("exported:", export_piece(PIECE, coll))
