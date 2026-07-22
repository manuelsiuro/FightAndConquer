# CATAPULT — siege engine: the only wide-low unit. Chassis + four wheels +
# angled throwing arm with boulder + faction side panels + 1 pip. ~300 tris.
# Arm tip ~0.50 (stays under T4 0.55); footprint within the 0.45 radius bound.
KIND = "CATAPULT"
PIECE = "catapult"
coll = reset_piece(KIND)

# Base disc + strength pip.
add_cyl(coll, "FACTION", r=0.155, h=0.03, z0=0)
add_pips(coll, 1, ring_r=0.145, z0=0.03)

# Chassis.
add_box(coll, "TRUNK", 0.28, 0.18, 0.05, z0=0.055)

# Four wheels, axes along X (rotation bakes into the mesh).
for sx in (-1, 1):
    for sy in (-1, 1):
        add_cyl(coll, "TRUNK", r=0.055, h=0.03, z0=0, seg=8,
                x=sx * 0.145, y=sy * 0.10, rot=(0, radians(90), 0), z_center=0.065)

# Uprights + stone crossbar.
add_box(coll, "TRUNK", 0.024, 0.024, 0.12, z0=0.105, x=-0.05, y=-0.06)
add_box(coll, "TRUNK", 0.024, 0.024, 0.12, z0=0.105, x=-0.05, y=0.06)
add_box(coll, "STONE", 0.03, 0.16, 0.03, z0=0.21, x=-0.05)

# Throwing arm angled up and back (+Y): a 0.40 beam rotated -50 deg about X from
# center z=0.16 puts its raised tip at (y ~0.15, z ~0.31) — cup + boulder sit there.
add_box(coll, "TRUNK", 0.03, 0.40, 0.03, z0=0.145, y=0.02, rot=(radians(50), 0, 0))
add_cyl(coll, "TRUNK", r=0.045, h=0.02, z0=0.305, y=0.15, seg=7)
add_sphere(coll, "STONE", r=0.038, z=0.355, y=0.15, seg=8, rings=4)

# Faction side panels — the ownership read.
add_box(coll, "FACTION", 0.24, 0.014, 0.06, z0=0.08, y=-0.098)
add_box(coll, "FACTION", 0.24, 0.014, 0.06, z0=0.08, y=0.098)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.20)
print("exported:", export_piece(PIECE, coll))
