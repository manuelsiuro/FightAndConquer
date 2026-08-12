# SHOGUNATE BOAT — river junk: flat shallow hull with squared bow/stern and an
# upward stern step, short mast with a small battened square faction sail, woven
# cargo bale. Front faces -Y. H 0.32, ~116 tris.
KIND = "SHOGUNATE_BOAT"
PIECE = "boat"
coll = reset_piece(KIND)

# Hull: flat shallow slab + squared upper deck; bow plate and raised stern step.
add_box(coll, "TRUNK", 0.20, 0.44, 0.05, z0=0)
add_box(coll, "TRUNK", 0.23, 0.40, 0.03, z0=0.05)
add_box(coll, "TRUNK", 0.20, 0.06, 0.035, z0=0.08, y=-0.19)  # squared bow plate
add_box(coll, "TRUNK", 0.20, 0.10, 0.05, z0=0.08, y=0.17)    # upward stern step

# Short mast + small battened square faction sail (battens = thin pip strips).
add_cyl(coll, "TRUNK", r=0.012, h=0.24, z0=0.08, seg=6)
add_box(coll, "FACTION", 0.16, 0.008, 0.13, z0=0.15, y=0.012)
add_box(coll, "PIP", 0.165, 0.006, 0.015, z0=0.19, y=0.017)
add_box(coll, "PIP", 0.165, 0.006, 0.015, z0=0.24, y=0.017)

# Woven cargo bale on the foredeck.
add_box(coll, "STONE", 0.075, 0.09, 0.05, z0=0.08, x=-0.04, y=-0.08, rot=(0, 0, radians(15)))

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.18)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
