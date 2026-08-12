# SHOGUNATE FISHERY — cormorant pier: pole-borne platform, rail with hanging
# rows of dark fish, round basket, brazier with a gold night-fishing flame,
# faction banner strip. Sea side faces -Y. H 0.28, ~274 tris.
KIND = "SHOGUNATE_FISHERY"
PIECE = "fishery"
coll = reset_piece(KIND)

# Pole-borne platform over the shallows.
for xx in (-0.10, 0.10):
    for yy in (-0.10, 0.06):
        add_cyl(coll, "TRUNK", r=0.014, h=0.07, z0=0, seg=6, x=xx, y=yy)
add_box(coll, "TRUNK", 0.28, 0.22, 0.025, z0=0.07, y=-0.02)

# Drying rail: two posts + top rail over the sea edge.
add_box(coll, "TRUNK", 0.014, 0.014, 0.16, z0=0.095, x=-0.11, y=-0.09)
add_box(coll, "TRUNK", 0.014, 0.014, 0.16, z0=0.095, x=0.11, y=-0.09)
add_box(coll, "TRUNK", 0.24, 0.012, 0.012, z0=0.245, y=-0.09)

# Hanging fish row: thin dark slivers below the rail.
for xx in (-0.09, -0.045, 0.0, 0.045, 0.09):
    add_box(coll, "PIP", 0.016, 0.007, 0.05, z0=0.19, x=xx, y=-0.09)

# Round basket for the catch.
add_cyl(coll, "STONE", r=0.035, h=0.045, z0=0.095, seg=7, x=-0.08, y=0.04)

# Brazier with a gold flame (night fishing fire).
add_cyl(coll, "PIP", r=0.026, h=0.035, z0=0.095, seg=6, x=0.08, y=0.03)
add_cyl(coll, "GOLD", r=0.018, h=0.045, z0=0.13, seg=6, r_top=0.0, x=0.08, y=0.03)

# Faction banner strip on a corner pole (ownership read).
add_cyl(coll, "TRUNK", r=0.008, h=0.18, z0=0.095, seg=6, x=0.12, y=0.06)
add_box(coll, "FACTION", 0.008, 0.038, 0.10, z0=0.17, x=0.12, y=0.085)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.15)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
