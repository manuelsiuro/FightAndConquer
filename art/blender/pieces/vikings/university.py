# VIKINGS UNIVERSITY — skald's longhouse: long turf-ridged hall with curled
# prow-posts at the gable ends, a leaning runestone at the entry, faction door
# cloth, gold ring on the post. H ~0.38, ~300 tris.
KIND = "VIKINGS_UNIVERSITY"
PIECE = "university"
coll = reset_piece(KIND)

# Longhouse (long along Y) with a deep roof.
add_box(coll, "TRUNK", 0.20, 0.34, 0.12, z0=0)
add_wedge(coll, "TRUNK", 0.26, 0.40, 0.15, z0=0.12)

# Turf ridge line.
add_box(coll, "TREE_FOLIAGE", 0.03, 0.38, 0.02, z0=0.265)

# Curled prow-posts rising past the ridge at both gable ends.
add_cyl(coll, "TRUNK", r=0.020, h=0.17, z0=0.22, seg=6, r_top=0.010,
        y=-0.195, rot=(radians(-14), 0, 0))
add_cyl(coll, "TRUNK", r=0.020, h=0.17, z0=0.22, seg=6, r_top=0.010,
        y=0.195, rot=(radians(14), 0, 0))

# Leaning runestone with an ink rune band.
add_box(coll, "STONE", 0.07, 0.035, 0.15, z0=0, x=0.14, y=-0.13, rot=(radians(6), 0, radians(8)))
add_box(coll, "PIP", 0.045, 0.008, 0.10, z0=0.02, x=0.135, y=-0.148, rot=(radians(6), 0, radians(8)))

# Faction door cloth (the ownership read) + gold ring hung on the doorpost.
add_box(coll, "FACTION", 0.08, 0.012, 0.11, z0=0.005, y=-0.176)
add_cyl(coll, "GOLD", r=0.024, h=0.012, z0=0, seg=8, x=-0.075, y=-0.178,
        rot=(radians(90), 0, 0), z_center=0.15)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.19)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
