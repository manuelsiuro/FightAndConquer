# SULTANATE_FORTRESS_LIT — the kasbah fortress with its beacon lit: gate lamps
# on the front wall top flanking the horseshoe gate — stone bowls + gold
# flames. H ~0.57, ~460 tris.
KIND = "SULTANATE_FORTRESS_LIT"
PIECE = "fortress_lit"
coll = reset_piece(KIND)

# Curtain walls.
add_box(coll, "STONE", 0.34, 0.05, 0.14, z0=0, y=-0.17)
add_box(coll, "STONE", 0.34, 0.05, 0.14, z0=0, y=0.17)
add_box(coll, "STONE", 0.05, 0.34, 0.14, z0=0, x=-0.17)
add_box(coll, "STONE", 0.05, 0.34, 0.14, z0=0, x=0.17)

# Corner towers: battered shafts with pointed faction caps.
for sx in (-0.17, 0.17):
    for sy in (-0.17, 0.17):
        add_cyl(coll, "STONE", r=0.050, h=0.24, z0=0, seg=6, r_top=0.040, x=sx, y=sy)
        add_cyl(coll, "FACTION", r=0.050, h=0.055, z0=0.24, seg=6, r_top=0, x=sx, y=sy)

# Central keep: tapering tower, faction band, merlon crown, gold crescent.
add_cyl(coll, "STONE", r=0.115, h=0.42, z0=0, seg=8, r_top=0.095)
add_cyl(coll, "FACTION", r=0.10, h=0.04, z0=0.42, seg=8)
for deg in (45, 135, 225, 315):
    a = radians(deg)
    add_box(coll, "STONE", 0.045, 0.03, 0.05, z0=0.46,
            x=0.075 * math.cos(a), y=0.075 * math.sin(a), rot=(0, 0, a))
add_box(coll, "GOLD", 0.012, 0.012, 0.05, z0=0.515, x=-0.014, rot=(0, -radians(40), 0))
add_box(coll, "GOLD", 0.012, 0.012, 0.05, z0=0.515, x=0.014, rot=(0, radians(40), 0))

# The beacon: gate lamps on the front wall top, flanking the gate.
for sx in (-0.10, 0.10):
    add_cyl(coll, "STONE", r=0.020, h=0.020, z0=0.14, seg=6, x=sx, y=-0.17)
    add_sphere(coll, "GOLD", r=0.024, z=0.184, seg=6, rings=3, x=sx, y=-0.17)

# Horseshoe ink gate on the front wall (-Y), faction lintel above.
add_box(coll, "PIP", 0.07, 0.05, 0.09, z0=0, y=-0.20)
add_sphere(coll, "PIP", r=0.040, z=0.09, seg=8, rings=3, y=-0.213, scale=(1, 0.5, 1))
add_box(coll, "FACTION", 0.10, 0.02, 0.04, z0=0.145, y=-0.213)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.28)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
