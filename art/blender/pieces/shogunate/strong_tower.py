# SHOGUNATE_STRONG_TOWER — tenshu keep: battered stone steps + two stepped
# faction tiers with double eaves + red corner posts + gold finial. Bulkier and
# taller than the yagura tower. H ~0.58, ~170 tris.
KIND = "SHOGUNATE_STRONG_TOWER"
PIECE = "strong_tower"
coll = reset_piece(KIND)

# Battered stone base — two stepped slabs.
add_box(coll, "STONE", 0.40, 0.34, 0.07, z0=0)
add_box(coll, "STONE", 0.35, 0.29, 0.06, z0=0.07)

# Tier 1 faction wall + ink gate + red-lacquer corner posts.
add_box(coll, "FACTION", 0.28, 0.23, 0.14, z0=0.13)
add_box(coll, "PIP", 0.10, 0.02, 0.10, z0=0.13, y=-0.118)
for sx in (-0.13, 0.13):
    for sy in (-0.105, 0.105):
        add_box(coll, "PIP", 0.024, 0.024, 0.14, z0=0.13, x=sx, y=sy)

# Eave 1: overhanging board + shallow roof skirt.
add_box(coll, "TRUNK", 0.34, 0.29, 0.02, z0=0.27)
add_wedge(coll, "TRUNK", 0.34, 0.29, 0.045, z0=0.29)

# Tier 2 faction wall + arrow-slit hints + its eave roof.
add_box(coll, "FACTION", 0.20, 0.16, 0.12, z0=0.31)
add_box(coll, "PIP", 0.016, 0.014, 0.07, z0=0.335, x=-0.05, y=-0.083)
add_box(coll, "PIP", 0.016, 0.014, 0.07, z0=0.335, x=0.05, y=-0.083)
add_box(coll, "TRUNK", 0.26, 0.21, 0.018, z0=0.43)
add_wedge(coll, "TRUNK", 0.26, 0.21, 0.05, z0=0.448)

# Single gold finial at the ridge.
add_cyl(coll, "GOLD", r=0.022, h=0.08, z0=0.498, seg=6, r_top=0)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.29)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
