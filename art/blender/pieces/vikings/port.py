# VIKINGS PORT — timber jetty running to sea (-Y): plank deck on piles, raked
# mooring posts, curled-prow skiff tied alongside, gold lantern, faction
# pennant. H 0.29, ~280 tris.
KIND = "VIKINGS_PORT"
PIECE = "port"
coll = reset_piece(KIND)

# Shore platform (+Y) and the jetty running out over the water (-Y).
add_box(coll, "TRUNK", 0.28, 0.16, 0.035, z0=0.055, y=0.13)
add_box(coll, "TRUNK", 0.16, 0.34, 0.03, z0=0.06, x=-0.06, y=-0.12)

# Piles under the decks.
for (px, py) in ((-0.11, 0.16), (0.10, 0.16), (-0.11, -0.05), (-0.01, -0.05),
                 (-0.11, -0.25), (-0.01, -0.25)):
    add_cyl(coll, "TRUNK", r=0.016, h=0.06, z0=0, seg=6, x=px, y=py)

# Mooring posts at the jetty head, raked out to sea.
add_cyl(coll, "TRUNK", r=0.014, h=0.09, z0=0.09, seg=6, x=-0.115, y=-0.27,
        rot=(radians(10), 0, 0))
add_cyl(coll, "TRUNK", r=0.014, h=0.09, z0=0.09, seg=6, x=-0.005, y=-0.27,
        rot=(radians(10), 0, 0))

# Small curled-prow skiff tied alongside, with a pip rope to the jetty.
add_box(coll, "TRUNK", 0.07, 0.18, 0.032, z0=0.005, x=0.10, y=-0.14)
add_cyl(coll, "TRUNK", r=0.016, h=0.05, z0=0.01, seg=6, x=0.10, y=-0.225,
        r_top=0.009, rot=(radians(22), 0, 0))
add_box(coll, "PIP", 0.055, 0.008, 0.008, z0=0.055, x=0.042, y=-0.12)

# Gold lantern on a shore post; faction pennant mast at mid-jetty.
add_box(coll, "TRUNK", 0.016, 0.016, 0.17, z0=0.09, x=0.10, y=0.10)
add_box(coll, "GOLD", 0.035, 0.035, 0.04, z0=0.245, x=0.10, y=0.10)
add_cyl(coll, "TRUNK", r=0.009, h=0.20, z0=0.09, seg=6, x=-0.06)
add_pennant(coll, "FACTION", pole_x=-0.051, top_z=0.28, drop=0.04, length=0.07)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.15)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
