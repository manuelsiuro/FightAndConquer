# WARSHIP — sleeker hull, wedge ram prow, faction shields along the gunwales,
# taller mast with crow's nest and a gold pennant. Front faces -Y. H 0.53, ~340 tris.
KIND = "WARSHIP"
PIECE = "warship"
coll = reset_piece(KIND)

# Hull: longer, narrower keel + gunwale; raised sterncastle.
add_box(coll, "TRUNK", 0.16, 0.46, 0.055, z0=0)
add_box(coll, "TRUNK", 0.20, 0.52, 0.045, z0=0.055)
add_box(coll, "TRUNK", 0.14, 0.10, 0.05, z0=0.10, y=0.20)  # sterncastle deck
# Ram prow: ink wedge driving forward at the waterline.
add_wedge(coll, "PIP", 0.10, 0.10, 0.055, z0=0.01, y=-0.30, rot_z=0.0)

# Shield row along both gunwales (discs facing outward).
for side in (-1, 1):
    for yy in (-0.13, 0.0, 0.13):
        add_cyl(coll, "FACTION", r=0.032, h=0.012, z0=0, seg=8,
                x=side * 0.104, y=yy, rot=(0, radians(90), 0), z_center=0.115)

# Mast, yard, sail, crow's nest, pennant.
add_cyl(coll, "TRUNK", r=0.013, h=0.40, z0=0.10, seg=6)
add_box(coll, "TRUNK", 0.26, 0.014, 0.014, z0=0.335)
add_box(coll, "FACTION", 0.24, 0.008, 0.17, z0=0.16, y=0.012)
add_cyl(coll, "TRUNK", r=0.028, h=0.03, z0=0.46, seg=6)
add_pennant(coll, "GOLD", pole_x=0.013, top_z=0.53, drop=0.05, length=0.10)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.24)
print("exported:", export_piece(PIECE, coll))
