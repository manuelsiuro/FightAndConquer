# BOAT — transport: wide flat-bottomed longboat, faction square sail, cargo
# crates on deck. Front faces -Y. H 0.40, ~260 tris.
KIND = "BOAT"
PIECE = "boat"
coll = reset_piece(KIND)

# Hull: keel slab + flared gunwale ring, bow/stern posts.
add_box(coll, "TRUNK", 0.18, 0.40, 0.055, z0=0)
add_box(coll, "TRUNK", 0.22, 0.46, 0.045, z0=0.055)
add_box(coll, "TRUNK", 0.07, 0.07, 0.13, z0=0, y=-0.245, rot=(radians(-12), 0, 0))  # bow post
add_box(coll, "TRUNK", 0.07, 0.06, 0.11, z0=0, y=0.245, rot=(radians(10), 0, 0))    # stern post
# Gunwale rails.
add_box(coll, "PIP", 0.012, 0.44, 0.014, z0=0.10, x=0.104)
add_box(coll, "PIP", 0.012, 0.44, 0.014, z0=0.10, x=-0.104)

# Mast + yard + square faction sail (spans athwart, travel is -Y).
add_cyl(coll, "TRUNK", r=0.013, h=0.30, z0=0.10, seg=6)
add_box(coll, "TRUNK", 0.22, 0.014, 0.014, z0=0.345)
add_box(coll, "FACTION", 0.20, 0.008, 0.15, z0=0.19, y=0.012)

# Cargo crates.
add_box(coll, "STONE", 0.055, 0.055, 0.05, z0=0.10, x=-0.045, y=0.13)
add_box(coll, "STONE", 0.045, 0.045, 0.04, z0=0.10, x=0.05, y=0.16, rot=(0, 0, radians(20)))

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.20)
print("exported:", export_piece(PIECE, coll))
