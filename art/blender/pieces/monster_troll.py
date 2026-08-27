# MONSTER_TROLL — tall stooped brute, arms past the knees, moss on its back.
# Tier-2 night monster, a head above the ogre. H 0.54, ~340 tris.
KIND = "MONSTER_TROLL"
PIECE = "monster_troll"
coll = reset_piece(KIND)

# Legs.
add_cyl(coll, "STONE", r=0.04, h=0.11, z0=0, seg=6, x=0.05, y=0.01)
add_cyl(coll, "STONE", r=0.04, h=0.11, z0=0, seg=6, x=-0.05, y=0.01)
# Long lean torso, leaning forward.
add_cyl(coll, "STONE", r=0.105, h=0.29, z0=0.10, seg=8, r_top=0.07, y=-0.005,
        rot=(radians(7), 0, 0))
# Slab shoulders.
add_sphere(coll, "STONE", r=0.09, z=0.41, seg=8, rings=4, y=-0.03, scale=(1.35, 0.85, 0.6))
# Head hanging forward off the stoop.
add_sphere(coll, "STONE", r=0.065, z=0.465, seg=8, rings=4, y=-0.085)
add_box(coll, "STONE", 0.045, 0.04, 0.03, z0=0.42, y=-0.14)  # heavy jaw
# Arms past the knees.
add_cyl(coll, "STONE", r=0.03, h=0.32, z0=0, seg=6, x=-0.135, y=-0.02,
        rot=(0, radians(-7), 0), z_center=0.21)
add_cyl(coll, "STONE", r=0.03, h=0.32, z0=0, seg=6, x=0.135, y=-0.02,
        rot=(0, radians(7), 0), z_center=0.21)
# Moss: a green mantle across the back and one shoulder tuft.
add_box(coll, "TREE_FOLIAGE", 0.17, 0.05, 0.16, z0=0.24, y=0.085, rot=(radians(8), 0, 0))
add_sphere(coll, "TREE_FOLIAGE", r=0.035, z=0.44, seg=6, rings=3, x=0.09, y=0.03)
# Ink eyes.
add_sphere(coll, "PIP", r=0.011, z=0.48, seg=6, rings=3, x=0.026, y=-0.143)
add_sphere(coll, "PIP", r=0.011, z=0.48, seg=6, rings=3, x=-0.026, y=-0.143)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.28)
print("exported:", export_piece(PIECE, coll))
