# SHOGUNATE FISHING_BOAT — river fishing skiff: flat squared hull with a stern
# step, small battened faction sail, woven creel pair, tall stern sculling oar.
# Front faces -Y. H 0.30, ~130 tris.
KIND = "SHOGUNATE_FISHING_BOAT"
PIECE = "fishing_boat"
coll = reset_piece(KIND)

# Hull: flat shallow slab + squared deck, a size under the river junk.
add_box(coll, "TRUNK", 0.17, 0.36, 0.045, z0=0)
add_box(coll, "TRUNK", 0.19, 0.32, 0.028, z0=0.045)
add_box(coll, "TRUNK", 0.17, 0.05, 0.03, z0=0.073, y=-0.155)  # squared bow plate
add_box(coll, "TRUNK", 0.17, 0.08, 0.045, z0=0.073, y=0.14)   # upward stern step

# Short mast + small battened faction sail (battens = thin pip strips).
add_cyl(coll, "TRUNK", r=0.011, h=0.21, z0=0.073, seg=6, y=-0.02)
add_box(coll, "FACTION", 0.13, 0.008, 0.11, z0=0.135, y=-0.008)
add_box(coll, "PIP", 0.135, 0.006, 0.013, z0=0.165, y=-0.003)
add_box(coll, "PIP", 0.135, 0.006, 0.013, z0=0.21, y=-0.003)

# Woven creel pair — the fishing tell — with the catch glinting in one.
add_cyl(coll, "STONE", r=0.038, h=0.05, z0=0.073, seg=6, x=0.055, y=-0.09)
add_cyl(coll, "STONE", r=0.032, h=0.042, z0=0.073, seg=6, x=0.075, y=-0.02)
add_sphere(coll, "GOLD", 0.02, z=0.128, x=0.055, y=-0.09, scale=(0.7, 1.4, 0.55))

# Tall stern sculling oar (ro), raked out over the transom.
add_cyl(coll, "TRUNK", r=0.009, h=0.24, z0=0.10, seg=6, y=0.155,
        rot=(radians(-28), 0, 0))
add_box(coll, "TRUNK", 0.022, 0.09, 0.012, z0=0.028, y=0.235, rot=(radians(-20), 0, 0))

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.15)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
