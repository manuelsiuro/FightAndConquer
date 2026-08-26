# SULTANATE ARCHERY_RANGE — qabaq range: ring target on a crescent-topped
# pole (the mounted-archery qabaq game), striped faction-and-stone awning
# shelter, low fence rail. H ~0.36, ~290 tris.
KIND = "SULTANATE_ARCHERY_RANGE"
PIECE = "archery_range"
coll = reset_piece(KIND)

# Qabaq pole at the front (-Y): tall shaft, faction ring target facing the
# lane, gold crescent finial.
add_cyl(coll, "TRUNK", r=0.009, h=0.30, z0=0, seg=6, y=-0.12)
add_cyl(coll, "FACTION", r=0.062, h=0.014, z0=0, seg=12, y=-0.134,
        rot=(radians(90), 0, 0), z_center=0.19)
add_cyl(coll, "PIP", r=0.024, h=0.010, z0=0, seg=8, y=-0.142,
        rot=(radians(90), 0, 0), z_center=0.19)
add_sphere(coll, "GOLD", r=0.020, z=0.315, seg=8, rings=4, y=-0.12,
           scale=(1, 0.5, 1))
add_cyl(coll, "GOLD", r=0.007, h=0.030, z0=0.335, seg=5, r_top=0, y=-0.12)

# Striped awning shelter at the back (+Y): short front posts, tall back
# posts, sloped alternating faction-and-stone canopy strips — a lean-to, not
# a table.
for px in (-0.10, 0.10):
    add_box(coll, "TRUNK", 0.014, 0.014, 0.10, z0=0, x=px, y=0.06)
    add_box(coll, "TRUNK", 0.014, 0.014, 0.145, z0=0, x=px, y=0.18)
for i, role in enumerate(("FACTION", "STONE", "FACTION", "STONE", "FACTION")):
    add_box(coll, role, 0.048, 0.17, 0.012, z0=0.116, x=-0.096 + 0.048 * i,
            y=0.12, rot=(radians(15), 0, 0))

# Low fence rail along the west side of the lane.
add_box(coll, "TRUNK", 0.012, 0.012, 0.05, z0=0, x=-0.17, y=-0.08)
add_box(coll, "TRUNK", 0.012, 0.012, 0.05, z0=0, x=-0.17, y=0.06)
add_box(coll, "TRUNK", 0.012, 0.18, 0.012, z0=0.045, x=-0.17, y=-0.01)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.16)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
