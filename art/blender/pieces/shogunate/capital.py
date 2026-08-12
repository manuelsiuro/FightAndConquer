# SHOGUNATE_CAPITAL — tenshu castle: battered stone steps + two faction wall
# tiers under overhanging eaves + faction top gable + gold shachihoko pair.
# H ~0.67, ~200 tris.
KIND = "SHOGUNATE_CAPITAL"
PIECE = "capital"
coll = reset_piece(KIND)

# Battered stone base — two stepped slabs.
add_box(coll, "STONE", 0.42, 0.36, 0.06, z0=0)
add_box(coll, "STONE", 0.37, 0.31, 0.05, z0=0.06)

# Tier 1 faction wall + ink gate + lattice hints + red-lacquer corner posts.
add_box(coll, "FACTION", 0.30, 0.25, 0.15, z0=0.11)
add_box(coll, "PIP", 0.08, 0.02, 0.10, z0=0.11, y=-0.128)
add_box(coll, "PIP", 0.02, 0.016, 0.10, z0=0.13, x=-0.09, y=-0.128)
add_box(coll, "PIP", 0.02, 0.016, 0.10, z0=0.13, x=0.09, y=-0.128)
for sx in (-0.14, 0.14):
    for sy in (-0.115, 0.115):
        add_box(coll, "PIP", 0.026, 0.026, 0.15, z0=0.11, x=sx, y=sy)

# Eave 1: overhanging board + shallow roof skirt the next tier rises through.
add_box(coll, "TRUNK", 0.38, 0.33, 0.02, z0=0.26)
add_wedge(coll, "TRUNK", 0.38, 0.33, 0.05, z0=0.28)

# Tier 2 faction wall + its eave.
add_box(coll, "FACTION", 0.22, 0.18, 0.13, z0=0.30)
add_box(coll, "TRUNK", 0.30, 0.25, 0.018, z0=0.43)
add_wedge(coll, "TRUNK", 0.30, 0.25, 0.045, z0=0.448)

# Small top gable, faction-roofed.
add_box(coll, "FACTION", 0.15, 0.12, 0.09, z0=0.46)
add_wedge(coll, "FACTION", 0.19, 0.16, 0.06, z0=0.55)

# Gold shachihoko finials at the ridge ends, tilting up and outward.
for (gy, tilt) in ((-0.075, -radians(25)), (0.075, radians(25))):
    add_cyl(coll, "GOLD", r=0.022, h=0.07, z0=0, seg=6, r_top=0,
            y=gy, rot=(tilt, 0, 0), z_center=0.635)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.33)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
