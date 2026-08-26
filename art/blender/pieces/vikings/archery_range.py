# VIKINGS ARCHERY_RANGE — shield butts: a faction round-shield target with a
# gold boss hung on a timber frame, mini-longhouse shelter with a dragon
# finial, low fence rail. H ~0.26, ~220 tris.
KIND = "VIKINGS_ARCHERY_RANGE"
PIECE = "archery_range"
coll = reset_piece(KIND)

# Timber frame at the front (-Y): two posts and a lintel.
add_box(coll, "TRUNK", 0.018, 0.018, 0.24, z0=0, x=-0.09, y=-0.12)
add_box(coll, "TRUNK", 0.018, 0.018, 0.24, z0=0, x=0.09, y=-0.12)
add_box(coll, "TRUNK", 0.21, 0.020, 0.020, z0=0.24, y=-0.12)

# Round-shield target hung in the frame, gold boss, ink rim ring.
add_cyl(coll, "FACTION", r=0.062, h=0.014, z0=0, seg=12, y=-0.124,
        rot=(radians(90), 0, 0), z_center=0.135)
add_cyl(coll, "PIP", r=0.066, h=0.008, z0=0, seg=12, y=-0.118,
        rot=(radians(90), 0, 0), z_center=0.135)
add_sphere(coll, "GOLD", r=0.016, z=0.135, seg=6, rings=3, y=-0.134)

# Mini-longhouse shelter at the back (+Y), dragon finial on the ridge.
add_box(coll, "TRUNK", 0.16, 0.10, 0.08, z0=0, y=0.13)
add_wedge(coll, "TRUNK", 0.10, 0.20, 0.09, z0=0.08, y=0.13, rot_z=radians(90))
add_cyl(coll, "GOLD", r=0.010, h=0.04, z0=0, seg=6, r_top=0.004, x=-0.09,
        y=0.13, rot=(0, radians(35), 0), z_center=0.185)

# Low fence rail along the west side of the lane.
add_box(coll, "TRUNK", 0.012, 0.012, 0.05, z0=0, x=-0.17, y=-0.06)
add_box(coll, "TRUNK", 0.012, 0.012, 0.05, z0=0, x=-0.17, y=0.08)
add_box(coll, "TRUNK", 0.012, 0.18, 0.012, z0=0.045, x=-0.17, y=0.01)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.14)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
