# SULTANATE_STRONG_TOWER — kasbah: battered STONE cube (4-sided frustum) + four
# corner turrets + FACTION upper story and dome + GOLD finial + ink arrow slits.
# H ~0.59, ~238 tris.
KIND = "SULTANATE_STRONG_TOWER"
PIECE = "strong_tower"
coll = reset_piece(KIND)

# Battered (tapering) sandstone body: 4-sided frustum yawed 45 deg = sloped cube.
add_cyl(coll, "STONE", r=0.24, h=0.32, z0=0, seg=4, r_top=0.185, rot=(0, 0, radians(45)))

# Faction upper story — big readable faction surface.
add_box(coll, "FACTION", 0.26, 0.26, 0.10, z0=0.32)

# Four corner turrets with pointed caps.
for sx in (-0.16, 0.16):
    for sy in (-0.16, 0.16):
        add_cyl(coll, "STONE", r=0.045, h=0.42, z0=0, seg=6, x=sx, y=sy)
        add_cyl(coll, "STONE", r=0.055, h=0.05, z0=0.42, seg=6, r_top=0, x=sx, y=sy)

# Faction dome + gold finial cone.
add_sphere(coll, "FACTION", r=0.105, z=0.44, seg=8, rings=4, scale=(1, 1, 0.95))
add_cyl(coll, "GOLD", r=0.028, h=0.06, z0=0.525, seg=6, r_top=0)

# Three ink arrow slits on the front face (-Y).
for sx in (-0.06, 0.0, 0.06):
    add_box(coll, "PIP", 0.016, 0.02, 0.09, z0=0.10, x=sx, y=-0.158)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.29)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
