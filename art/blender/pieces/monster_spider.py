# MONSTER_SPIDER — giant spider: fat ink abdomen over a wide eight-leg splay,
# gold eye cluster. Tier-1 night monster, the board's lowest-widest silhouette.
# H 0.22, ~380 tris.
KIND = "MONSTER_SPIDER"
PIECE = "monster_spider"
coll = reset_piece(KIND)

# Abdomen (rear, raised) + cephalothorax (front).
add_sphere(coll, "PIP", r=0.095, z=0.13, seg=8, rings=4, y=0.085, scale=(1.0, 1.1, 0.95))
add_sphere(coll, "PIP", r=0.068, z=0.095, seg=8, rings=4, y=-0.055)
# Eight legs: knees up at the body, tips planted wide — each an angled stilt
# whose inner end tucks under the carapace.
for side in (1, -1):
    for ly in (-0.10, -0.035, 0.035, 0.10):
        add_cyl(coll, "STONE", r=0.015, h=0.15, z0=0, seg=5,
                x=side * 0.085, y=ly,
                rot=(0, radians(side * 42), 0), z_center=0.062)
# Fangs.
add_cyl(coll, "STONE", r=0.014, h=0.045, z0=0, seg=5, r_top=0, x=0.025, y=-0.115,
        rot=(radians(180), 0, 0), z_center=0.052)
add_cyl(coll, "STONE", r=0.014, h=0.045, z0=0, seg=5, r_top=0, x=-0.025, y=-0.115,
        rot=(radians(180), 0, 0), z_center=0.052)
# Gold eye cluster.
add_sphere(coll, "GOLD", r=0.013, z=0.115, seg=6, rings=3, x=0.026, y=-0.113)
add_sphere(coll, "GOLD", r=0.013, z=0.115, seg=6, rings=3, x=-0.026, y=-0.113)
add_sphere(coll, "GOLD", r=0.011, z=0.14, seg=6, rings=3, y=-0.103)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.11)
print("exported:", export_piece(PIECE, coll))
