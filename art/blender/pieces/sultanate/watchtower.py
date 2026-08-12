# SULTANATE_WATCHTOWER — desert beacon: tapered STONE tower + open top platform
# with FACTION parapet band + FACTION banner strip + GOLD signal mirror angled
# toward -Y on timber posts. H ~0.62, ~144 tris.
KIND = "SULTANATE_WATCHTOWER"
PIECE = "watchtower"
coll = reset_piece(KIND)

# Base disc + tapering shaft.
add_cyl(coll, "STONE", r=0.13, h=0.02, z0=0, seg=6)
add_cyl(coll, "STONE", r=0.11, h=0.42, z0=0.02, seg=6, r_top=0.075)

# Open top platform + faction parapet band (no crenellations — those code defense).
add_cyl(coll, "STONE", r=0.10, h=0.025, z0=0.44, seg=6)
add_cyl(coll, "FACTION", r=0.105, h=0.03, z0=0.465, seg=6)

# Faction banner strip hanging down the front (-Y) of the shaft.
add_box(coll, "FACTION", 0.07, 0.014, 0.20, z0=0.16, y=-0.095)

# Timber posts holding the gold signal mirror, disc face angled toward -Y.
for sx in (-0.045, 0.045):
    add_box(coll, "TRUNK", 0.016, 0.016, 0.11, z0=0.465, x=sx, y=0.01)
add_cyl(coll, "GOLD", r=0.055, h=0.012, z0=0, seg=8,
        rot=(radians(35), 0, 0), z_center=0.575, y=-0.01)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.31)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
