# VIKINGS BARRACKS — hird longhouse: timber hall with a steep roof ridged
# along X, gold dragon prows curling off both gable ends, three faction round
# shields on the front wall, training pell in the yard. H ~0.34, ~220 tris.
KIND = "VIKINGS_BARRACKS"
PIECE = "barracks"
coll = reset_piece(KIND)

# Longhouse hall (long axis along X), set back for the yard.
add_box(coll, "TRUNK", 0.32, 0.16, 0.13, z0=0, y=0.05)

# Steep roof — wedge ridge runs along Y, so rotate for a ridge along X.
add_wedge(coll, "TRUNK", 0.20, 0.36, 0.17, z0=0.13, y=0.05, rot_z=radians(90))

# Gold dragon prows at both gable ends.
add_cyl(coll, "GOLD", r=0.014, h=0.06, z0=0, seg=6, r_top=0.006, x=-0.185,
        y=0.05, rot=(0, radians(35), 0), z_center=0.315)
add_cyl(coll, "GOLD", r=0.014, h=0.06, z0=0, seg=6, r_top=0.006, x=0.185,
        y=0.05, rot=(0, radians(-35), 0), z_center=0.315)

# Faction round shields hung on the front wall.
for px in (-0.09, 0.0, 0.09):
    add_cyl(coll, "FACTION", r=0.042, h=0.012, z0=0, seg=10, x=px, y=-0.036,
            rot=(radians(90), 0, 0), z_center=0.075)

# Training pell in the yard.
add_cyl(coll, "STONE", r=0.030, h=0.018, z0=0, seg=8, y=-0.15)
add_cyl(coll, "TRUNK", r=0.011, h=0.11, z0=0.018, seg=6, y=-0.15)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.17)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
