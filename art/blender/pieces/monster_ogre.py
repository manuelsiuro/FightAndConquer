# MONSTER_OGRE — hunched bulk on stumpy legs, knuckle arms, club planted at its
# side. Tier-2 night monster. H 0.47, ~350 tris.
KIND = "MONSTER_OGRE"
PIECE = "monster_ogre"
coll = reset_piece(KIND)

# Stumpy legs.
add_cyl(coll, "STONE", r=0.045, h=0.09, z0=0, seg=6, x=0.055, y=0.01)
add_cyl(coll, "STONE", r=0.045, h=0.09, z0=0, seg=6, x=-0.055, y=0.01)
# Torso: barrel belly under hunched shoulders.
add_sphere(coll, "STONE", r=0.115, z=0.185, seg=8, rings=4, y=-0.005, scale=(1.0, 0.95, 0.85))
add_cyl(coll, "STONE", r=0.125, h=0.17, z0=0.20, seg=8, r_top=0.08, y=0.005)
# Head, sunk forward into the shoulders.
add_sphere(coll, "STONE", r=0.075, z=0.395, seg=8, rings=4, y=-0.05)
# Jaw tusks.
add_cyl(coll, "STONE", r=0.011, h=0.035, z0=0.352, seg=5, r_top=0, x=0.03, y=-0.115)
add_cyl(coll, "STONE", r=0.011, h=0.035, z0=0.352, seg=5, r_top=0, x=-0.03, y=-0.115)
# Knuckle-dragging arms.
add_cyl(coll, "STONE", r=0.035, h=0.24, z0=0, seg=6, x=-0.145, y=-0.01,
        rot=(0, radians(-12), 0), z_center=0.175)
add_cyl(coll, "STONE", r=0.035, h=0.22, z0=0, seg=6, x=0.135, y=-0.015,
        rot=(0, radians(10), 0), z_center=0.185)
# The club, planted by the right fist.
add_cyl(coll, "TRUNK", r=0.028, h=0.20, z0=0, seg=6, x=0.215, y=-0.045,
        rot=(0, radians(-8), 0), z_center=0.10)
add_cyl(coll, "TRUNK", r=0.052, h=0.09, z0=0.19, seg=6, r_top=0.038, x=0.225, y=-0.045)
# Ink eyes under the brow.
add_sphere(coll, "PIP", r=0.012, z=0.41, seg=6, rings=3, x=0.028, y=-0.118)
add_sphere(coll, "PIP", r=0.012, z=0.41, seg=6, rings=3, x=-0.028, y=-0.118)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.24)
print("exported:", export_piece(PIECE, coll))
