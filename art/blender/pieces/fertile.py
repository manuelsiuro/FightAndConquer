# FERTILE — lush ground: wheat-sheaf cones in an edge crescent + a low bush.
# Terrain deposit: low (H ~0.08), hex center left clear for units. ~220 tris.
KIND = "FERTILE"
PIECE = "fertile"
coll = reset_piece(KIND)

# Wheat sheaves (cones) on an arc near the hex edge.
for (x, y, h) in (
    (0.24, 0.08, 0.070),
    (0.12, -0.24, 0.065),
    (-0.20, 0.16, 0.070),
    (-0.04, 0.27, 0.060),
    (0.27, -0.06, 0.060),
):
    add_cyl(coll, "TREE_FOLIAGE", r=0.028, h=h, z0=0, seg=6, r_top=0, x=x, y=y)

# Trunk stubs at the sheaves' feet.
add_box(coll, "TRUNK", 0.03, 0.03, 0.02, z0=0, x=0.18, y=0.02)
add_box(coll, "TRUNK", 0.03, 0.03, 0.02, z0=0, x=-0.12, y=0.22)

# One low bush.
add_sphere(coll, "TREE_FOLIAGE", r=0.05, z=0.04, seg=8, rings=3, x=0.02, y=-0.08, scale=(1, 1, 0.7))

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.05)
print("exported:", export_piece(PIECE, coll))
