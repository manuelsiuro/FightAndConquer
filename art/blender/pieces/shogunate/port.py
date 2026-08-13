# SHOGUNATE PORT — junk quay: plank pier toward -Y on timber piles, red-lacquer
# lantern post with a gold light, mooring cleats, small moored junk with a furled
# sail, low stone breakwater edge. Sea side faces -Y. H 0.33, ~196 tris.
KIND = "SHOGUNATE_PORT"
PIECE = "port"
coll = reset_piece(KIND)

# Quay slab (land side +Y) + plank pier running out over the water.
add_box(coll, "STONE", 0.32, 0.20, 0.05, z0=0, y=0.12)
add_box(coll, "TRUNK", 0.12, 0.28, 0.03, z0=0.03, x=-0.05, y=-0.10)
add_box(coll, "TRUNK", 0.025, 0.025, 0.03, z0=0, x=-0.08, y=-0.20)  # pier pile
add_box(coll, "TRUNK", 0.025, 0.025, 0.03, z0=0, x=-0.02, y=-0.20)  # pier pile

# Red-lacquer lantern post with a gold light on the quay.
add_cyl(coll, "PIP", r=0.013, h=0.24, z0=0.05, seg=6, x=0.10, y=0.10)
add_cyl(coll, "GOLD", r=0.020, h=0.035, z0=0.29, seg=6, x=0.10, y=0.10)

# Mooring cleats along the quay's sea edge.
add_cyl(coll, "PIP", r=0.011, h=0.028, z0=0.05, seg=6, x=-0.13, y=0.05)
add_cyl(coll, "PIP", r=0.011, h=0.028, z0=0.05, seg=6, x=0.04, y=0.05)

# Low stone breakwater edge on the west flank.
add_box(coll, "STONE", 0.09, 0.30, 0.045, z0=0, x=-0.20, y=-0.02)

# Small moored junk alongside the pier, sail furled on the lowered yard.
add_box(coll, "TRUNK", 0.08, 0.18, 0.03, z0=0, x=0.10, y=-0.17)
add_box(coll, "TRUNK", 0.08, 0.045, 0.022, z0=0.03, x=0.10, y=-0.10)  # stern step
add_cyl(coll, "TRUNK", r=0.007, h=0.13, z0=0.03, seg=6, x=0.10, y=-0.16)
add_box(coll, "FACTION", 0.09, 0.016, 0.016, z0=0.115, x=0.10, y=-0.16)  # furled sail

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.16)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
