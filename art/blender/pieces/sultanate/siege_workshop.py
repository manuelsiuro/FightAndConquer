# SULTANATE SIEGE_WORKSHOP — engineer's forge: domed forge annex with a
# crescent spire beside an open faction awning bay sheltering a counterweight
# trebuchet arm, log stock at the wall. H ~0.33, ~240 tris.
KIND = "SULTANATE_SIEGE_WORKSHOP"
PIECE = "siege_workshop"
coll = reset_piece(KIND)

# Forge annex on the east side: sandstone block, faction drum, gold dome,
# crescent spire.
add_box(coll, "STONE", 0.14, 0.14, 0.17, z0=0, x=0.10, y=0.05)
add_cyl(coll, "FACTION", r=0.052, h=0.025, z0=0.17, x=0.10, y=0.05)
add_sphere(coll, "GOLD", r=0.056, z=0.205, seg=8, rings=4, x=0.10, y=0.05,
           scale=(1, 1, 0.85))
add_cyl(coll, "GOLD", r=0.008, h=0.050, z0=0.252, seg=5, r_top=0, x=0.10,
        y=0.05)
add_box(coll, "PIP", 0.05, 0.014, 0.07, z0=0.02, x=0.10, y=-0.022)

# Open awning bay on the west side: four posts, flat faction awning.
for px in (-0.20, -0.02):
    add_box(coll, "TRUNK", 0.016, 0.016, 0.15, z0=0, x=px, y=-0.10)
    add_box(coll, "TRUNK", 0.016, 0.016, 0.21, z0=0, x=px, y=0.12)
add_box(coll, "FACTION", 0.24, 0.28, 0.016, z0=0.18, x=-0.11, y=0.01,
        rot=(radians(14), 0, 0))

# Counterweight trebuchet arm under the awning: A-frame, angled beam, stone
# counterweight box at the low end.
add_box(coll, "TRUNK", 0.014, 0.014, 0.13, z0=0, x=-0.14, y=0.01,
        rot=(0, radians(16), 0))
add_box(coll, "TRUNK", 0.014, 0.014, 0.13, z0=0, x=-0.08, y=0.01,
        rot=(0, radians(-16), 0))
add_box(coll, "TRUNK", 0.018, 0.022, 0.22, z0=0.015, x=-0.11, y=0.01,
        rot=(radians(-42), 0, 0))
add_box(coll, "STONE", 0.05, 0.05, 0.045, z0=0.10, x=-0.11, y=0.085)

# Log stock at the annex wall.
add_cyl(coll, "TRUNK", r=0.024, h=0.12, z0=0, seg=7, x=0.10, y=0.16,
        rot=(0, radians(90), 0), z_center=0.024)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.15)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
