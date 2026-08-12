# VIKINGS WARSHIP — longship: long low narrow hull, dragon-head prow with pip
# eyes, upswept tail, big faction sail with pip battens, shield rows, gold
# masthead. Front faces -Y. H 0.48, ~366 tris.
KIND = "VIKINGS_WARSHIP"
PIECE = "warship"
coll = reset_piece(KIND)

# Hull: longer, lower and narrower than both the knarr and the Kingdom warship.
add_box(coll, "TRUNK", 0.12, 0.56, 0.05, z0=0)
add_box(coll, "TRUNK", 0.16, 0.62, 0.04, z0=0.05)

# Dragon prow (-Y): neck frustums curling up and forward, cone snout, pip eyes.
add_cyl(coll, "TRUNK", r=0.034, h=0.14, z0=0.02, seg=6, y=-0.30, r_top=0.024,
        rot=(radians(16), 0, 0))
add_cyl(coll, "TRUNK", r=0.024, h=0.09, z0=0.14, seg=6, y=-0.335, r_top=0.018,
        rot=(radians(38), 0, 0))
add_cyl(coll, "TRUNK", r=0.020, h=0.06, z0=0, seg=6, y=-0.375, r_top=0.0,
        rot=(radians(90), 0, 0), z_center=0.225)  # snout, tip toward -Y
add_box(coll, "PIP", 0.014, 0.014, 0.014, z0=0.225, x=0.016, y=-0.355)
add_box(coll, "PIP", 0.014, 0.014, 0.014, z0=0.225, x=-0.016, y=-0.355)

# Upswept tail spiral at the stern (+Y).
add_cyl(coll, "TRUNK", r=0.030, h=0.16, z0=0.02, seg=6, y=0.30, r_top=0.012,
        rot=(radians(-28), 0, 0))

# Shield rows: bold round faction shields along both gunwales, facing outward.
for side in (-1, 1):
    for yy in (-0.18, -0.06, 0.06, 0.18):
        add_cyl(coll, "FACTION", r=0.038, h=0.012, z0=0, seg=6,
                x=side * 0.084, y=yy, rot=(0, radians(90), 0), z_center=0.105)

# Mast, yard, big square faction sail with pip batten stripes, gold masthead.
add_cyl(coll, "TRUNK", r=0.012, h=0.34, z0=0.09, seg=6)
add_box(coll, "TRUNK", 0.30, 0.013, 0.013, z0=0.335)
add_box(coll, "FACTION", 0.28, 0.008, 0.18, z0=0.15, y=0.012)
add_box(coll, "PIP", 0.285, 0.006, 0.020, z0=0.20, y=0.017)
add_box(coll, "PIP", 0.285, 0.006, 0.020, z0=0.26, y=0.017)
add_cyl(coll, "GOLD", r=0.020, h=0.05, z0=0.425, seg=6)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.22)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
