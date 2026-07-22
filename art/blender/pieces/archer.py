# ARCHER — hooded ranger with an unmistakable side-held bow + quiver.
# H 0.44 (sits between T2 0.41 and T3 0.48 — the silhouette, not height, is the
# identifier), 1 pip (strength 1). ~250 tris.
KIND = "ARCHER"
PIECE = "archer"
coll = reset_piece(KIND)

# Plinth + single strength pip.
add_cyl(coll, "FACTION", r=0.135, h=0.06, z0=0)
add_pips(coll, 1, ring_r=0.148, z0=0.06)

# Body: hooded robe (frustum) + hood cone leaning slightly forward.
add_cyl(coll, "FACTION", r=0.095, h=0.20, z0=0.06, r_top=0.062)
add_cyl(coll, "FACTION", r=0.058, h=0.115, z0=0.255, r_top=0, rot=(radians(-10), 0, 0))

# Bow at the +X side: vertical arc from three chained thin boxes + a string.
add_box(coll, "TRUNK", 0.014, 0.014, 0.115, z0=0.085, x=0.118, y=-0.02, rot=(0, radians(18), 0))
add_box(coll, "TRUNK", 0.014, 0.014, 0.105, z0=0.19, x=0.138, y=-0.02, rot=(0, radians(-20), 0))
add_box(coll, "TRUNK", 0.013, 0.013, 0.10, z0=0.0, x=0.135, y=-0.02, rot=(0, radians(-16), 0))
add_box(coll, "PIP", 0.004, 0.004, 0.27, z0=0.01, x=0.155, y=-0.02)

# Quiver on the back (+Y), leaning with the shoulder.
add_cyl(coll, "TRUNK", r=0.028, h=0.14, z0=0.13, x=-0.045, y=0.085, seg=6, rot=(radians(15), 0, 0))
add_sphere(coll, "GOLD", r=0.018, z=0.30, x=-0.02, y=0.05)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.20)
print("exported:", export_piece(PIECE, coll))
