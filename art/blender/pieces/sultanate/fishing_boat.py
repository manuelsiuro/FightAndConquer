# SULTANATE FISHING_BOAT — fishing dhow: small shallow hull, raked mast with
# the lateen furled along its yard, flat cast-net disc on the foredeck, gold
# catch beside it, faction stern pennant strip. Front faces -Y. H 0.34, ~150 tris.
KIND = "SULTANATE_FISHING_BOAT"
PIECE = "fishing_boat"
coll = reset_piece(KIND)

# Hull: shallow keel + gunwale, a size under the cargo dhow.
add_box(coll, "TRUNK", 0.14, 0.32, 0.045, z0=0)
add_box(coll, "TRUNK", 0.17, 0.38, 0.04, z0=0.045)
# Raked bow post leaning over the water (-Y).
add_box(coll, "TRUNK", 0.05, 0.05, 0.10, z0=0.01, y=-0.19, rot=(radians(20), 0, 0))

# Raked mast + steep furled lateen bundle (the dhow read, no open sail).
add_cyl(coll, "TRUNK", r=0.011, h=0.26, z0=0.08, seg=6, y=0.03, rot=(radians(7), 0, 0))
add_box(coll, "TRUNK", 0.013, 0.30, 0.013, z0=0.21, y=-0.05, rot=(radians(48), 0, 0))
add_box(coll, "TRUNK", 0.026, 0.22, 0.026, z0=0.205, y=-0.05, rot=(radians(48), 0, 0))

# Cast net: a flat woven disc on the foredeck with a small float ring.
add_cyl(coll, "PIP", r=0.062, h=0.016, z0=0.08, seg=8, y=-0.10)
add_cyl(coll, "PIP", r=0.02, h=0.026, z0=0.096, seg=6, y=-0.10)

# The catch: gold fish piled by the net.
add_sphere(coll, "GOLD", 0.022, z=0.10, x=0.06, y=-0.045, scale=(0.7, 1.5, 0.55))
add_sphere(coll, "GOLD", 0.019, z=0.10, x=0.09, y=-0.10, scale=(0.7, 1.4, 0.5))

# Faction awning strip over the stern thwart (the ownership read).
add_box(coll, "TRUNK", 0.011, 0.011, 0.08, z0=0.08, x=-0.04, y=0.14)
add_box(coll, "TRUNK", 0.011, 0.011, 0.08, z0=0.08, x=0.04, y=0.14)
add_box(coll, "FACTION", 0.11, 0.09, 0.011, z0=0.16, y=0.14)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.15)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
