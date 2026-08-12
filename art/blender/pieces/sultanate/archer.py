# SULTANATE ARCHER — desert bowman: FACTION robe with a GOLD sash, conical STONE
# helm (tip 0.37), recurve horn-bow at +X (shallower, wider arc than the Viking
# longbow), TRUNK quiver on the back. Height matches the Kingdom archer, 1 pip. ~246 tris.
KIND = "SULTANATE_ARCHER"
PIECE = "archer"
coll = reset_piece(KIND)

# Plinth + single strength pip (matches Kingdom archer).
add_cyl(coll, "FACTION", r=0.135, h=0.06, z0=0)
add_pips(coll, 1, ring_r=0.148, z0=0.06)

# Body: FACTION robe, GOLD waist sash, STONE face under a conical helm.
add_cyl(coll, "FACTION", r=0.09, h=0.19, z0=0.06, r_top=0.058)
add_cyl(coll, "GOLD", r=0.082, h=0.024, z0=0.14)                  # sash band
add_sphere(coll, "STONE", r=0.042, z=0.26, seg=8, rings=4, y=-0.02)  # face
add_cyl(coll, "STONE", r=0.052, h=0.09, z0=0.28, r_top=0)         # conical helm, tip 0.37
add_sphere(coll, "GOLD", r=0.012, z=0.20, seg=6, rings=2, y=-0.072)  # chest brooch

# Recurve horn-bow at the +X side: three chained TRUNK limbs, wider + shallower
# arc than the Viking longbow, tips hooking back toward the string.
add_box(coll, "TRUNK", 0.014, 0.014, 0.095, z0=0.055, x=0.132, y=-0.02, rot=(0, radians(-32), 0))
add_box(coll, "TRUNK", 0.014, 0.014, 0.10, z0=0.135, x=0.158, y=-0.02)
add_box(coll, "TRUNK", 0.014, 0.014, 0.095, z0=0.225, x=0.132, y=-0.02, rot=(0, radians(32), 0))
add_box(coll, "PIP", 0.005, 0.005, 0.26, z0=0.055, x=0.112, y=-0.02)  # string

# TRUNK quiver on the back (+Y), leaning with the shoulder.
add_cyl(coll, "TRUNK", r=0.028, h=0.14, z0=0.13, x=-0.045, y=0.085, seg=6,
        rot=(radians(15), 0, 0))

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.20)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
