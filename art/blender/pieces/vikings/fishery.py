# VIKINGS FISHERY — fish-drying racks: two trunk A-frames with rails, rows of
# hanging pip fish, barrel with the gold catch, faction net. Front faces -Y.
# H 0.30, ~268 tris.
KIND = "VIKINGS_FISHERY"
PIECE = "fishery"
coll = reset_piece(KIND)

# Two A-frames (legs lean in across X) at y = -0.10 and +0.10.
for yy in (-0.10, 0.10):
    add_box(coll, "TRUNK", 0.022, 0.022, 0.30, z0=0, x=-0.045, y=yy, rot=(0, radians(16), 0))
    add_box(coll, "TRUNK", 0.022, 0.022, 0.30, z0=0, x=0.045, y=yy, rot=(0, radians(-16), 0))

# Rails between the frames: ridge pole + mid and low rails lashed on the slopes.
add_box(coll, "TRUNK", 0.020, 0.30, 0.020, z0=0.285)
add_box(coll, "TRUNK", 0.016, 0.28, 0.016, z0=0.20, x=-0.038)
add_box(coll, "TRUNK", 0.016, 0.28, 0.016, z0=0.20, x=0.038)
add_box(coll, "TRUNK", 0.016, 0.28, 0.016, z0=0.10, x=-0.066)
add_box(coll, "TRUNK", 0.016, 0.28, 0.016, z0=0.10, x=0.066)

# Rows of drying fish hanging from the rails.
for fy in (-0.05, 0.0, 0.05):
    add_box(coll, "PIP", 0.014, 0.010, 0.05, z0=0.235, y=fy)          # ridge row
for fx in (-0.045, 0.045):
    for fy in (-0.04, 0.04):
        add_box(coll, "PIP", 0.014, 0.010, 0.05, z0=0.145, x=fx, y=fy)  # mid rows
add_box(coll, "PIP", 0.014, 0.010, 0.05, z0=0.045, x=0.072, y=-0.02)   # low rail
add_box(coll, "PIP", 0.014, 0.010, 0.05, z0=0.045, x=0.072, y=0.03)

# Barrel with a gold fish on top; faction net leaning on the seaward end.
add_cyl(coll, "TRUNK", r=0.032, h=0.07, z0=0, x=0.14, y=0.12)
add_box(coll, "GOLD", 0.02, 0.05, 0.018, z0=0.07, x=0.14, y=0.12, rot=(0, 0, radians(20)))
add_box(coll, "FACTION", 0.10, 0.008, 0.13, z0=0, y=-0.125, rot=(radians(-14), 0, 0))

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.15)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
