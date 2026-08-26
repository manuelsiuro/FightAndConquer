# SHOGUNATE SIEGE_WORKSHOP — siege yard: tiered-eave workshop hall with a
# bamboo scaffold cage around an angled siege arm, log stock, nobori banner.
# H ~0.38, ~260 tris.
KIND = "SHOGUNATE_SIEGE_WORKSHOP"
PIECE = "siege_workshop"
coll = reset_piece(KIND)

# Workshop hall on the east side: raised floor, hall, faction eave.
add_box(coll, "TRUNK", 0.16, 0.18, 0.02, z0=0, x=0.10, y=0.04)
add_box(coll, "STONE", 0.13, 0.14, 0.11, z0=0.02, x=0.10, y=0.04)
add_wedge(coll, "FACTION", 0.19, 0.20, 0.06, z0=0.13, x=0.10, y=0.04)
add_box(coll, "PIP", 0.07, 0.008, 0.06, z0=0.04, x=0.10, y=-0.035)

# Bamboo scaffold cage on the west side: four verticals, two rails.
for (px, py) in ((-0.19, -0.08), (-0.19, 0.10), (-0.03, -0.08), (-0.03, 0.10)):
    add_cyl(coll, "TRUNK", r=0.007, h=0.20, z0=0, seg=6, x=px, y=py)
add_cyl(coll, "TRUNK", r=0.006, h=0.17, z0=0, seg=6, x=-0.19, y=0.01,
        rot=(radians(90), 0, 0), z_center=0.19)
add_cyl(coll, "TRUNK", r=0.006, h=0.17, z0=0, seg=6, x=-0.03, y=0.01,
        rot=(radians(90), 0, 0), z_center=0.19)

# Siege arm inside the scaffold: A-frame and angled throw beam.
add_box(coll, "TRUNK", 0.014, 0.014, 0.13, z0=0, x=-0.14, y=0.01,
        rot=(0, radians(16), 0))
add_box(coll, "TRUNK", 0.014, 0.014, 0.13, z0=0, x=-0.08, y=0.01,
        rot=(0, radians(-16), 0))
add_box(coll, "TRUNK", 0.018, 0.022, 0.21, z0=0.015, x=-0.11, y=0.01,
        rot=(radians(-40), 0, 0))

# Log stock between yard and hall.
add_cyl(coll, "TRUNK", r=0.024, h=0.13, z0=0, seg=7, y=0.17,
        rot=(0, radians(90), 0), z_center=0.024)

# Nobori banner at the yard corner.
add_cyl(coll, "TRUNK", r=0.006, h=0.38, z0=0, seg=6, x=0.17, y=-0.14)
add_box(coll, "FACTION", 0.042, 0.006, 0.14, z0=0.23, x=0.194, y=-0.14)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.16)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
