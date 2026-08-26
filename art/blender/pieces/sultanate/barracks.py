# SULTANATE BARRACKS — askar hall: arcaded sandstone barracks with a gold dome
# and crescent spire on the east end, faction awning over the arch row, tall
# faction standard in the yard. H ~0.38, ~240 tris.
KIND = "SULTANATE_BARRACKS"
PIECE = "barracks"
coll = reset_piece(KIND)

# Sandstone hall, set back for the yard.
add_box(coll, "STONE", 0.30, 0.20, 0.17, z0=0, y=0.04)

# Arcade: three pointed ink arches on the front face.
for px in (-0.09, 0.0, 0.09):
    add_box(coll, "PIP", 0.05, 0.014, 0.09, z0=0.02, x=px, y=-0.062)
    add_wedge(coll, "PIP", 0.05, 0.014, 0.025, z0=0.11, x=px, y=-0.062)

# Faction awning band above the arcade.
add_box(coll, "FACTION", 0.24, 0.030, 0.014, z0=0.15, y=-0.07)

# Gold dome on a faction drum at the east end, crescent spire above.
add_cyl(coll, "FACTION", r=0.062, h=0.030, z0=0.17, x=0.08, y=0.04)
add_sphere(coll, "GOLD", r=0.068, z=0.21, seg=8, rings=4, x=0.08, y=0.04,
           scale=(1, 1, 0.85))
add_cyl(coll, "GOLD", r=0.009, h=0.045, z0=0.265, seg=5, r_top=0, x=0.08,
        y=0.04)

# Tall faction standard clear of the hall (y=0 plane for the pennant).
add_cyl(coll, "TRUNK", r=0.008, h=0.38, z0=0, seg=6, x=-0.19)
add_pennant(coll, "FACTION", -0.19, 0.38, 0.05, 0.08)

# Training pell in the yard.
add_cyl(coll, "STONE", r=0.028, h=0.016, z0=0, seg=8, x=0.08, y=-0.14)
add_cyl(coll, "TRUNK", r=0.010, h=0.10, z0=0.016, seg=6, x=0.08, y=-0.14)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.18)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
