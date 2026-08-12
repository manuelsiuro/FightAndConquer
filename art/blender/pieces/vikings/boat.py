# VIKINGS BOAT — knarr: broad clinker cargo ship, curled prow/stern posts, cargo
# hump under a faction tarp, stubby unstepped mast. Front faces -Y. H 0.23, ~172 tris.
KIND = "VIKINGS_BOAT"
PIECE = "boat"
coll = reset_piece(KIND)

# Hull: narrow keel strake + broad clinker gunwale (knarr beam). Same length/
# height class as the Kingdom boat so the transport read is unchanged.
add_box(coll, "TRUNK", 0.15, 0.40, 0.05, z0=0)
add_box(coll, "TRUNK", 0.23, 0.46, 0.045, z0=0.05)
# Gunwale strakes.
add_box(coll, "PIP", 0.012, 0.44, 0.012, z0=0.09, x=0.11)
add_box(coll, "PIP", 0.012, 0.44, 0.012, z0=0.09, x=-0.11)

# Curled prow post (-Y): stacked frustums leaning out and curling forward.
add_cyl(coll, "TRUNK", r=0.030, h=0.11, z0=0.02, seg=6, y=-0.225, r_top=0.020,
        rot=(radians(18), 0, 0))
add_cyl(coll, "TRUNK", r=0.020, h=0.06, z0=0.115, seg=6, y=-0.255, r_top=0.013,
        rot=(radians(42), 0, 0))
# Stern post: shorter single curl.
add_cyl(coll, "TRUNK", r=0.028, h=0.10, z0=0.02, seg=6, y=0.225, r_top=0.018,
        rot=(radians(-16), 0, 0))

# Cargo hump midship under a faction tarp (the ownership read).
add_box(coll, "STONE", 0.14, 0.16, 0.05, z0=0.09, y=0.02)
add_wedge(coll, "FACTION", 0.17, 0.19, 0.05, z0=0.138, y=0.02)

# Stubby mast forward; yard stowed athwart the stern.
add_cyl(coll, "TRUNK", r=0.011, h=0.13, z0=0.095, seg=6, y=-0.10)
add_box(coll, "TRUNK", 0.16, 0.013, 0.013, z0=0.10, y=0.16)

# Deck crate forward of the tarp.
add_box(coll, "STONE", 0.05, 0.05, 0.04, z0=0.095, x=-0.05, y=-0.13, rot=(0, 0, radians(15)))

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.13)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
