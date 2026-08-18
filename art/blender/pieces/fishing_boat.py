# FISHING_BOAT — fishing dory: stub working hull a class below the longboat,
# short mast with a small faction lug sail, stern boom raked up over the transom
# trailing a hanging net, the gold catch spilling from a basket amidships
# (kept +X/-Y so the icon camera sees it). Front faces -Y. H 0.33, ~210 tris.
KIND = "FISHING_BOAT"
PIECE = "fishing_boat"
coll = reset_piece(KIND)

# Hull: keel slab + flared gunwale, shorter and narrower than the longboat.
add_box(coll, "TRUNK", 0.15, 0.32, 0.05, z0=0)
add_box(coll, "TRUNK", 0.185, 0.38, 0.042, z0=0.05)
add_box(coll, "TRUNK", 0.06, 0.06, 0.10, z0=0, y=-0.20, rot=(radians(-12), 0, 0))  # bow post
add_box(coll, "TRUNK", 0.055, 0.05, 0.07, z0=0, y=0.20, rot=(radians(8), 0, 0))    # transom post
# Gunwale rails.
add_box(coll, "PIP", 0.011, 0.36, 0.012, z0=0.092, x=0.088)
add_box(coll, "PIP", 0.011, 0.36, 0.012, z0=0.092, x=-0.088)

# Short mast forward + small lug sail (the ownership read).
add_cyl(coll, "TRUNK", r=0.012, h=0.24, z0=0.092, seg=6, y=-0.055)
add_box(coll, "TRUNK", 0.15, 0.013, 0.013, z0=0.295, y=-0.055)
add_box(coll, "FACTION", 0.13, 0.008, 0.115, z0=0.175, y=-0.043)

# Stern boom raked up over the transom — the working silhouette.
add_box(coll, "TRUNK", 0.013, 0.24, 0.013, z0=0.185, y=0.065, rot=(radians(32), 0, 0))
# The net: a thin panel draped from the boom toward the deck, plus a rolled
# spare along the gunwale.
add_box(coll, "PIP", 0.10, 0.006, 0.13, z0=0.10, y=0.125, rot=(radians(10), 0, 0))
add_cyl(coll, "PIP", r=0.016, h=0.14, z0=0, z_center=0.10, seg=6, x=-0.065, y=0.02,
        rot=(radians(90), 0, 0))

# The catch: wicker basket + gold fish spilling beside it (+X/-Y for the icon).
add_cyl(coll, "STONE", r=0.042, h=0.05, z0=0.092, seg=6, x=0.05, y=-0.10)
add_sphere(coll, "GOLD", 0.024, z=0.15, x=0.05, y=-0.10, scale=(0.7, 1.5, 0.55))
add_sphere(coll, "GOLD", 0.021, z=0.105, x=0.085, y=-0.155, scale=(0.7, 1.5, 0.55))
add_sphere(coll, "GOLD", 0.019, z=0.105, x=0.02, y=-0.165, scale=(0.7, 1.4, 0.5))

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.17)
print("exported:", export_piece(PIECE, coll))
