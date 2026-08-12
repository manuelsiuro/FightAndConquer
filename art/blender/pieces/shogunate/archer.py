# SHOGUNATE ARCHER — kyudo archer: FACTION robe, STONE face with a PIP topknot,
# tall ASYMMETRIC yumi at +X (grip at one-third height — short lower limb, long
# upper limb + PIP string), TRUNK quiver box on the back. Top ~0.37 (matches the
# Kingdom archer), 1 pip. ~208 tris.
KIND = "SHOGUNATE_ARCHER"
PIECE = "archer"
coll = reset_piece(KIND)

# Plinth + single strength pip (matches Kingdom archer).
add_cyl(coll, "FACTION", r=0.135, h=0.06, z0=0)
add_pips(coll, 1, ring_r=0.148, z0=0.06)

# Body: FACTION robe, bare STONE head, small PIP topknot.
add_cyl(coll, "FACTION", r=0.09, h=0.19, z0=0.06, r_top=0.058)
add_sphere(coll, "STONE", r=0.046, z=0.28, seg=8, rings=4, y=-0.008)
add_box(coll, "PIP", 0.022, 0.022, 0.028, z0=0.318, y=0.008)      # topknot

# Yumi at the +X side, asymmetric: the grip is the lower-limb/mid junction at
# z~0.13 (one third of the 0.37 bow) — short lower limb, long upper limbs whose
# tips curve back to the PIP string at x=0.108.
add_box(coll, "TRUNK", 0.013, 0.013, 0.12, z0=0.015, x=0.13, y=-0.02, rot=(0, radians(22), 0))
add_box(coll, "TRUNK", 0.013, 0.013, 0.135, z0=0.125, x=0.155, y=-0.02)
add_box(coll, "TRUNK", 0.013, 0.013, 0.13, z0=0.25, x=0.135, y=-0.02, rot=(0, radians(-24), 0))
add_box(coll, "PIP", 0.005, 0.005, 0.355, z0=0.015, x=0.108, y=-0.02)  # string, top 0.37

# Quiver box on the back (+Y) with a PIP arrow bundle poking out the top.
add_box(coll, "TRUNK", 0.05, 0.032, 0.13, z0=0.12, x=-0.04, y=0.088, rot=(radians(12), 0, 0))
add_box(coll, "PIP", 0.032, 0.02, 0.025, z0=0.245, x=-0.04, y=0.072)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.20)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
