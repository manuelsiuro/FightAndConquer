# VIKINGS BANK — barrow hoard: a stone burial mound with standing stones, a
# timber-framed door, the hoard chest spilling gold out front, a faction shield
# leaned on the slope. H ~0.28, ~300 tris.
KIND = "VIKINGS_BANK"
PIECE = "bank"
coll = reset_piece(KIND)

# The mound: broad frustum + rounded cap.
add_cyl(coll, "STONE", r=0.20, h=0.13, z0=0, seg=10, r_top=0.11)
add_sphere(coll, "STONE", r=0.115, z=0.13, seg=8, rings=4, scale=(1, 1, 0.55))

# Standing stones on the crown.
add_box(coll, "STONE", 0.035, 0.025, 0.10, z0=0.17, x=0.0, y=0.02, rot=(0, 0, radians(10)))
add_box(coll, "STONE", 0.028, 0.022, 0.075, z0=0.16, x=-0.06, y=0.05, rot=(0, radians(6), 0))

# Timber door frame into the mound (front, -Y) with an ink void.
add_box(coll, "TRUNK", 0.025, 0.03, 0.085, z0=0, x=-0.055, y=-0.175)
add_box(coll, "TRUNK", 0.025, 0.03, 0.085, z0=0, x=0.055, y=-0.175)
add_box(coll, "TRUNK", 0.135, 0.03, 0.025, z0=0.085, y=-0.175)
add_box(coll, "PIP", 0.085, 0.02, 0.075, z0=0, y=-0.172)

# The hoard: open chest half out of the door, gold heaped in and beside.
add_box(coll, "TRUNK", 0.09, 0.07, 0.045, z0=0, x=0.11, y=-0.20)
add_sphere(coll, "GOLD", r=0.038, z=0.05, seg=8, rings=3, x=0.11, y=-0.20, scale=(1, 1, 0.55))
add_cyl(coll, "GOLD", r=0.024, h=0.012, z0=0, x=0.02, y=-0.235)
add_cyl(coll, "GOLD", r=0.020, h=0.012, z0=0, x=0.17, y=-0.135)

# Faction round shield leaned on the slope.
add_cyl(coll, "FACTION", r=0.055, h=0.014, z0=0, seg=10, x=-0.13, y=-0.115,
        rot=(radians(70), 0, radians(25)), z_center=0.075)
add_sphere(coll, "PIP", r=0.012, z=0.078, seg=6, rings=3, x=-0.135, y=-0.132)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.13)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
