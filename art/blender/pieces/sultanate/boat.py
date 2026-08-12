# SULTANATE BOAT — cargo dhow: shallow hull with a raised frustum poop at the
# stern, raked mast with the lateen furled along its angled yard, stone cargo
# sacks midship, striped-canopy read via a small faction awning over the stern.
# Front faces -Y. H 0.39, ~160 tris.
KIND = "SULTANATE_BOAT"
PIECE = "boat"
coll = reset_piece(KIND)

# Hull: shallow keel + gunwale, same length class as the Kingdom boat (0.46).
add_box(coll, "TRUNK", 0.16, 0.40, 0.05, z0=0)
add_box(coll, "TRUNK", 0.20, 0.46, 0.045, z0=0.05)
# Raked bow post leaning out over the water (-Y).
add_box(coll, "TRUNK", 0.06, 0.06, 0.12, z0=0.01, y=-0.235, rot=(radians(20), 0, 0))
# Raised curved stern: frustum poop deck.
add_cyl(coll, "TRUNK", r=0.075, h=0.05, z0=0.095, seg=6, y=0.17, r_top=0.058)

# Raked mast (dhow rake: top leans a few degrees toward the bow).
add_cyl(coll, "TRUNK", r=0.012, h=0.30, z0=0.09, seg=6, y=0.02,
        rot=(radians(7), 0, 0))
# Lateen yard, steeply angled low-forward to high-aft, with the furled sail
# lashed along it (slightly thicker trunk bundle over the same line).
add_box(coll, "TRUNK", 0.014, 0.34, 0.014, z0=0.253, y=-0.06,
        rot=(radians(50), 0, 0))
add_box(coll, "TRUNK", 0.030, 0.26, 0.030, z0=0.245, y=-0.06,
        rot=(radians(50), 0, 0))

# Cargo sacks midship.
add_box(coll, "STONE", 0.06, 0.06, 0.05, z0=0.095, x=-0.04, y=0.04,
        rot=(0, 0, radians(18)))
add_box(coll, "STONE", 0.05, 0.05, 0.045, z0=0.095, x=0.045, y=-0.02,
        rot=(0, 0, radians(-12)))

# Striped-canopy stern awning on two posts (the ownership read).
add_box(coll, "TRUNK", 0.012, 0.012, 0.09, z0=0.145, x=-0.045, y=0.17)
add_box(coll, "TRUNK", 0.012, 0.012, 0.09, z0=0.145, x=0.045, y=0.17)
add_box(coll, "FACTION", 0.12, 0.10, 0.012, z0=0.235, y=0.17)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.16)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
