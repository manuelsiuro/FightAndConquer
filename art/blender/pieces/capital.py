# CAPITAL — keep + 4 corner towers + gold spire + banner. H 0.69, ~226 tris.
KIND = "CAPITAL"
PIECE = "capital"
coll = reset_piece(KIND)

add_box(coll, "FACTION", 0.34, 0.27, 0.27, z0=0)
add_box(coll, "GOLD", 0.37, 0.30, 0.03, z0=0.27)
add_cyl(coll, "STONE", r=0.08, h=0.20, z0=0.30)
add_cyl(coll, "GOLD", r=0.105, h=0.10, z0=0.50, r_top=0)
for sx in (-0.155, 0.155):
    for sy in (-0.12, 0.12):
        add_cyl(coll, "STONE", r=0.042, h=0.36, z0=0, seg=6, x=sx, y=sy)
        add_cyl(coll, "GOLD", r=0.052, h=0.05, z0=0.36, seg=6, r_top=0, x=sx, y=sy)
add_box(coll, "PIP", 0.07, 0.02, 0.10, z0=0, y=-0.145)
add_cyl(coll, "PIP", r=0.007, h=0.185, z0=0.505, seg=6)
add_pennant(coll, "GOLD", pole_x=0.007, top_z=0.675, drop=0.055, length=0.12)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.32)
print("exported:", export_piece(PIECE, coll))
