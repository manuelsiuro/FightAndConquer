# VIKINGS FISHING_BOAT — faering: small clinker hull with a single curled prow,
# stern A-frame stockfish rack with the gold catch hung in a row, faction tarp
# over the bait tub. Front faces -Y. H 0.30, ~180 tris.
KIND = "VIKINGS_FISHING_BOAT"
PIECE = "fishing_boat"
coll = reset_piece(KIND)

# Hull: narrow keel strake + clinker gunwale, a size under the knarr.
add_box(coll, "TRUNK", 0.13, 0.32, 0.045, z0=0)
add_box(coll, "TRUNK", 0.19, 0.38, 0.04, z0=0.045)
add_box(coll, "PIP", 0.011, 0.36, 0.011, z0=0.078, x=0.09)
add_box(coll, "PIP", 0.011, 0.36, 0.011, z0=0.078, x=-0.09)

# Curled prow post (-Y), single curl — the family read without the knarr's bulk.
add_cyl(coll, "TRUNK", r=0.026, h=0.10, z0=0.015, seg=6, y=-0.185, r_top=0.017,
        rot=(radians(18), 0, 0))
add_cyl(coll, "TRUNK", r=0.017, h=0.05, z0=0.10, seg=6, y=-0.21, r_top=0.011,
        rot=(radians(40), 0, 0))

# Stern A-frame stockfish rack: two legs to a ridge, the catch drying under it.
add_box(coll, "TRUNK", 0.013, 0.013, 0.20, z0=0.078, x=0.065, y=0.13,
        rot=(0, radians(-16), 0))
add_box(coll, "TRUNK", 0.013, 0.013, 0.20, z0=0.078, x=-0.065, y=0.13,
        rot=(0, radians(16), 0))
add_box(coll, "TRUNK", 0.15, 0.012, 0.012, z0=0.265, y=0.13)
add_sphere(coll, "GOLD", 0.018, z=0.225, x=-0.045, y=0.13, scale=(0.55, 0.9, 1.5))
add_sphere(coll, "GOLD", 0.018, z=0.225, x=0.0, y=0.13, scale=(0.55, 0.9, 1.5))
add_sphere(coll, "GOLD", 0.018, z=0.225, x=0.045, y=0.13, scale=(0.55, 0.9, 1.5))

# Bait tub under a faction tarp amidships (the ownership read).
add_box(coll, "STONE", 0.11, 0.11, 0.045, z0=0.078, y=-0.02)
add_wedge(coll, "FACTION", 0.14, 0.14, 0.04, z0=0.12, y=-0.02)

# Stowed oars along the port gunwale.
add_cyl(coll, "TRUNK", r=0.009, h=0.26, z0=0, z_center=0.095, seg=6, x=-0.075, y=0.0,
        rot=(radians(90), 0, 0))

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.15)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
