# VIKINGS SIEGE_WORKSHOP — ram shed: boat-shed with a steep hull roof and keel
# beam, a ram beam jutting from the open bay with a gold dragon head, faction
# shields on the wall, log stock, tall dragon standard. H ~0.34, ~260 tris.
KIND = "VIKINGS_SIEGE_WORKSHOP"
PIECE = "siege_workshop"
coll = reset_piece(KIND)

# Boat-shed: hall open at the front (-Y), steep roof, keel beam on the ridge.
add_box(coll, "TRUNK", 0.26, 0.02, 0.14, z0=0, y=0.13)
add_box(coll, "TRUNK", 0.02, 0.20, 0.14, z0=0, x=-0.12, y=0.04)
add_box(coll, "TRUNK", 0.02, 0.20, 0.14, z0=0, x=0.12, y=0.04)
add_wedge(coll, "TRUNK", 0.30, 0.24, 0.12, z0=0.14, y=0.04)
add_box(coll, "TRUNK", 0.02, 0.26, 0.02, z0=0.26, y=0.04)

# Ram beam through the bay, gold dragon head at the front tip.
add_cyl(coll, "TRUNK", r=0.020, h=0.26, z0=0, seg=7, y=-0.08,
        rot=(radians(90), 0, 0), z_center=0.08)
add_cyl(coll, "GOLD", r=0.014, h=0.05, z0=0, seg=6, r_top=0.005, y=-0.215,
        rot=(radians(-60), 0, 0), z_center=0.105)

# Faction shields on the east wall.
for py in (-0.02, 0.09):
    add_cyl(coll, "FACTION", r=0.040, h=0.012, z0=0, seg=10, x=0.132, y=py,
            rot=(0, radians(90), 0), z_center=0.075)

# Log stock along the west wall.
add_cyl(coll, "TRUNK", r=0.026, h=0.16, z0=0, seg=7, x=-0.17, y=0.02,
        rot=(radians(90), 0, 0), z_center=0.026)
add_cyl(coll, "TRUNK", r=0.026, h=0.14, z0=0, seg=7, x=-0.17, y=0.02,
        rot=(radians(90), 0, 0), z_center=0.072)

# Dragon standard at the yard corner: pole with a curling gold head.
add_cyl(coll, "TRUNK", r=0.007, h=0.30, z0=0, seg=6, x=0.17, y=-0.14)
add_cyl(coll, "GOLD", r=0.011, h=0.045, z0=0, seg=6, r_top=0.004, x=0.17,
        y=-0.152, rot=(radians(-35), 0, 0), z_center=0.315)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.16)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
