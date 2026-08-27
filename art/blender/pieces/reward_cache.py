# REWARD_CACHE — a slain monster's hoard: banded chest, gold lid rim spilling
# coins over the front lip. H 0.18, ~220 tris.
KIND = "REWARD_CACHE"
PIECE = "reward_cache"
coll = reset_piece(KIND)

add_box(coll, "STONE", 0.22, 0.155, 0.012, z0=0)  # base pad
add_box(coll, "TRUNK", 0.20, 0.14, 0.095, z0=0.012)  # chest body
# Rounded lid: a lying half-buried cylinder along X.
add_cyl(coll, "TRUNK", r=0.068, h=0.20, z0=0, seg=8,
        rot=(0, radians(90), 0), z_center=0.107)
# Iron bands over body and lid.
add_box(coll, "STONE", 0.024, 0.15, 0.10, z0=0.008, x=0.055)
add_box(coll, "STONE", 0.024, 0.15, 0.10, z0=0.008, x=-0.055)
add_cyl(coll, "STONE", r=0.072, h=0.026, z0=0, seg=8, x=0.055,
        rot=(0, radians(90), 0), z_center=0.107)
add_cyl(coll, "STONE", r=0.072, h=0.026, z0=0, seg=8, x=-0.055,
        rot=(0, radians(90), 0), z_center=0.107)
# Gold: the open lip's rim, the latch, and the coin spill.
add_box(coll, "GOLD", 0.19, 0.018, 0.018, z0=0.098, y=-0.068)
add_box(coll, "GOLD", 0.03, 0.014, 0.035, z0=0.062, y=-0.075)
add_cyl(coll, "GOLD", r=0.024, h=0.014, z0=0, seg=6, x=-0.04, y=-0.115)
add_cyl(coll, "GOLD", r=0.02, h=0.024, z0=0, seg=6, x=0.02, y=-0.135)
add_cyl(coll, "GOLD", r=0.018, h=0.012, z0=0, seg=6, x=0.08, y=-0.10)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.09)
print("exported:", export_piece(PIECE, coll))
