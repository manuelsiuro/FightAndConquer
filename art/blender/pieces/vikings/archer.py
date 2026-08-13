# VIKINGS ARCHER — fur-hooded hunter: TRUNK fur hood + collar over a FACTION
# robe, vertical longbow at the side, FACTION quiver on the back. Height matches
# the Kingdom archer (hood tip ~0.37, bow to ~0.29), 1 pip. ~200 tris.
KIND = "VIKINGS_ARCHER"
PIECE = "archer"
coll = reset_piece(KIND)

# Plinth + single strength pip (matches Kingdom archer).
add_cyl(coll, "FACTION", r=0.135, h=0.06, z0=0)
add_pips(coll, 1, ring_r=0.148, z0=0.06)

# Body: FACTION robe, face peeking out under a TRUNK fur hood.
add_cyl(coll, "FACTION", r=0.09, h=0.19, z0=0.06, r_top=0.058)
add_cyl(coll, "TRUNK", r=0.085, h=0.045, z0=0.22, r_top=0.056)     # fur collar
add_sphere(coll, "STONE", r=0.042, z=0.262, seg=8, rings=4, y=-0.028)  # face
add_cyl(coll, "TRUNK", r=0.06, h=0.115, z0=0.25, r_top=0, rot=(radians(-10), 0, 0))  # fur hood

# Longbow at the +X side: a clear D-arc from three chained TRUNK limbs + string.
add_box(coll, "TRUNK", 0.014, 0.014, 0.11, z0=0.02, x=0.125, y=-0.02, rot=(0, radians(-28), 0))
add_box(coll, "TRUNK", 0.014, 0.014, 0.12, z0=0.115, x=0.15, y=-0.02)
add_box(coll, "TRUNK", 0.014, 0.014, 0.11, z0=0.225, x=0.125, y=-0.02, rot=(0, radians(28), 0))
add_box(coll, "PIP", 0.006, 0.006, 0.30, z0=0.02, x=0.108, y=-0.02)

# FACTION quiver on the back (+Y), leaning with the shoulder; gold cloak brooch.
add_cyl(coll, "FACTION", r=0.028, h=0.14, z0=0.13, x=-0.045, y=0.085, seg=6,
        rot=(radians(15), 0, 0))
add_sphere(coll, "GOLD", r=0.016, z=0.29, seg=6, rings=3, x=-0.02, y=0.05)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.20)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
