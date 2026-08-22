# SHOGUNATE FORTRESS — tenshu on a sloped masonry base: two white storeys under
# wide faction eaves, gold shachihoko tips on the top ridge, a nobori banner at
# the corner. Leaner and shorter than the shogunate capital. H ~0.56, ~360 tris.
KIND = "SHOGUNATE_FORTRESS"
PIECE = "fortress"
coll = reset_piece(KIND)

# Sloped masonry base (square frustum: 4-segment cone rotated to face front).
add_cyl(coll, "STONE", r=0.30, h=0.14, z0=0, seg=4, r_top=0.21, rot=(0, 0, radians(45)))

# First storey + wide lower eave.
add_box(coll, "STONE", 0.20, 0.18, 0.15, z0=0.14)
add_wedge(coll, "FACTION", 0.30, 0.26, 0.06, z0=0.28)

# Second storey + top eave with a timber ridge.
add_box(coll, "STONE", 0.14, 0.12, 0.12, z0=0.31)
add_wedge(coll, "FACTION", 0.21, 0.18, 0.07, z0=0.42)
add_box(coll, "TRUNK", 0.02, 0.19, 0.014, z0=0.49)

# Gold shachihoko at both ridge ends.
add_cyl(coll, "GOLD", r=0.013, h=0.045, z0=0.49, seg=5, r_top=0.005,
        y=-0.085, rot=(radians(-18), 0, 0))
add_cyl(coll, "GOLD", r=0.013, h=0.045, z0=0.49, seg=5, r_top=0.005,
        y=0.085, rot=(radians(18), 0, 0))

# Ink gate in the base (front, -Y) + corner nobori banner, kept below the eaves.
add_box(coll, "PIP", 0.07, 0.04, 0.09, z0=0, y=-0.255)
add_box(coll, "TRUNK", 0.012, 0.012, 0.28, z0=0.02, x=0.23, y=-0.17)
add_box(coll, "FACTION", 0.016, 0.055, 0.14, z0=0.14, x=0.23, y=-0.145)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.27)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
