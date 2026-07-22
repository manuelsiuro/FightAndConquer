# WATCHTOWER — skeletal timber scaffold: no crenellations (those code
# "defense"), tall and thin, brazier flame + faction pennant. H ~0.61, ~230 tris.
KIND = "WATCHTOWER"
PIECE = "watchtower"
coll = reset_piece(KIND)

# Four legs leaning slightly inward.
for (sx, sy) in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
    add_box(coll, "TRUNK", 0.022, 0.022, 0.43, z0=0, x=sx * 0.085, y=sy * 0.085,
            rot=(sy * radians(4), sx * radians(-4), 0))

# Cross braces on two faces.
add_box(coll, "TRUNK", 0.16, 0.016, 0.016, z0=0.16, y=-0.088, rot=(0, radians(30), 0))
add_box(coll, "TRUNK", 0.16, 0.016, 0.016, z0=0.16, y=0.088, rot=(0, -radians(30), 0))

# Platform + faction parapet band.
add_box(coll, "TRUNK", 0.17, 0.17, 0.022, z0=0.43)
add_box(coll, "FACTION", 0.16, 0.16, 0.032, z0=0.452)

# Brazier bowl + gold flame.
add_cyl(coll, "STONE", r=0.032, h=0.030, z0=0.484, seg=6)
add_sphere(coll, "GOLD", r=0.026, z=0.532, seg=6, rings=3)

# Pole + faction pennant (pennant helper lives at y=0).
add_box(coll, "PIP", 0.007, 0.007, 0.13, z0=0.484, x=0.10, y=0.0)
add_pennant(coll, "FACTION", pole_x=0.10, top_z=0.614, drop=0.05, length=0.09)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.30)
print("exported:", export_piece(PIECE, coll))
