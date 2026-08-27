# MONSTER_WOLF — crouched dire wolf: long low body, raised haunches, gold eyes.
# Tier-1 night monster; horizontal mass, deliberately unlike the garrison verticals.
# H 0.30, ~330 tris.
KIND = "MONSTER_WOLF"
PIECE = "monster_wolf"
coll = reset_piece(KIND)

# Body: long barrel, haunches slightly higher than the shoulders (crouch).
add_sphere(coll, "STONE", r=0.10, z=0.155, seg=8, rings=4, y=0.05, scale=(0.85, 1.45, 0.85))
add_sphere(coll, "STONE", r=0.085, z=0.17, seg=8, rings=4, y=-0.09, scale=(0.9, 1.0, 0.95))
# Head, low and forward.
add_sphere(coll, "STONE", r=0.065, z=0.21, seg=8, rings=4, y=-0.185)
add_box(coll, "PIP", 0.05, 0.06, 0.038, z0=0.175, y=-0.255)  # muzzle
# Ears.
add_cyl(coll, "PIP", r=0.02, h=0.05, z0=0.245, seg=5, r_top=0, x=0.034, y=-0.175)
add_cyl(coll, "PIP", r=0.02, h=0.05, z0=0.245, seg=5, r_top=0, x=-0.034, y=-0.175)
# Legs: four planted stumps.
for lx in (0.058, -0.058):
    for ly in (-0.10, 0.115):
        add_cyl(coll, "STONE", r=0.028, h=0.10, z0=0, seg=6, x=lx, y=ly)
# Tail: angled up off the haunches.
add_cyl(coll, "STONE", r=0.018, h=0.13, z0=0, seg=5, y=0.20,
        rot=(radians(-38), 0, 0), z_center=0.20)
# Eyes: gold catching the moonlight.
add_sphere(coll, "GOLD", r=0.013, z=0.225, seg=6, rings=3, x=0.028, y=-0.238)
add_sphere(coll, "GOLD", r=0.013, z=0.225, seg=6, rings=3, x=-0.028, y=-0.238)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.15)
print("exported:", export_piece(PIECE, coll))
