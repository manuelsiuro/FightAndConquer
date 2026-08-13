# SHOGUNATE_WATCHTOWER — hinomi fire tower: open timber frame + faction-railed
# platform + small faction eave cap on posts + gold bell hanging under it.
# H ~0.63, ~196 tris.
KIND = "SHOGUNATE_WATCHTOWER"
PIECE = "watchtower"
coll = reset_piece(KIND)

# Four legs leaning slightly inward.
for (sx, sy) in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
    add_box(coll, "TRUNK", 0.024, 0.024, 0.46, z0=0, x=sx * 0.09, y=sy * 0.09,
            rot=(sy * radians(4), sx * radians(-4), 0))

# Cross braces on the front and back faces.
add_box(coll, "TRUNK", 0.17, 0.016, 0.016, z0=0.17, y=-0.092, rot=(0, radians(32), 0))
add_box(coll, "TRUNK", 0.17, 0.016, 0.016, z0=0.17, y=0.092, rot=(0, -radians(32), 0))

# Platform deck + faction rail (open above — no crenellations).
add_box(coll, "TRUNK", 0.18, 0.18, 0.022, z0=0.46)
add_box(coll, "FACTION", 0.19, 0.016, 0.04, z0=0.482, y=-0.082)
add_box(coll, "FACTION", 0.19, 0.016, 0.04, z0=0.482, y=0.082)
add_box(coll, "FACTION", 0.016, 0.19, 0.04, z0=0.482, x=-0.082)
add_box(coll, "FACTION", 0.016, 0.19, 0.04, z0=0.482, x=0.082)

# Two posts carrying a small faction eave cap.
add_box(coll, "TRUNK", 0.018, 0.018, 0.10, z0=0.482, x=-0.06)
add_box(coll, "TRUNK", 0.018, 0.018, 0.10, z0=0.482, x=0.06)
add_box(coll, "FACTION", 0.17, 0.15, 0.014, z0=0.582)
add_wedge(coll, "FACTION", 0.17, 0.15, 0.03, z0=0.596)

# Gold fire bell hanging under the cap.
add_cyl(coll, "GOLD", r=0.028, h=0.045, z0=0, seg=6, r_top=0.016, z_center=0.545)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.31)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
