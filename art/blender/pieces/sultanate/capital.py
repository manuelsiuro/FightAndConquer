# SULTANATE_CAPITAL — domed palace: FACTION plinth + STONE walls + FACTION drum
# and onion dome + two corner minarets + GOLD crescent finial + pointed ink door.
# H ~0.66, ~258 tris.
KIND = "SULTANATE_CAPITAL"
PIECE = "capital"
coll = reset_piece(KIND)

# Faction plinth — the big "this is mine" surface.
add_box(coll, "FACTION", 0.42, 0.42, 0.05, z0=0)

# Sandstone palace block.
add_box(coll, "STONE", 0.30, 0.30, 0.22, z0=0.05)

# Faction drum + onion dome (stretched sphere) + tapering tip.
add_cyl(coll, "FACTION", r=0.125, h=0.05, z0=0.27)
add_sphere(coll, "FACTION", r=0.135, z=0.40, seg=8, rings=4, scale=(1, 1, 1.15))
add_cyl(coll, "FACTION", r=0.05, h=0.09, z0=0.53, seg=6, r_top=0)

# Gold crescent finial — two angled boxes forming an open V above the tip.
add_box(coll, "GOLD", 0.013, 0.013, 0.055, z0=0.605, x=-0.016, rot=(0, -radians(40), 0))
add_box(coll, "GOLD", 0.013, 0.013, 0.055, z0=0.605, x=0.016, rot=(0, radians(40), 0))

# Two slender minarets on the front corners: frustum + faction dome cap + gold tip.
for sx in (-0.185, 0.185):
    add_cyl(coll, "STONE", r=0.034, h=0.55, z0=0, seg=6, r_top=0.026, x=sx, y=-0.185)
    add_sphere(coll, "FACTION", r=0.038, z=0.565, seg=6, rings=3, x=sx, y=-0.185)
    add_cyl(coll, "GOLD", r=0.015, h=0.035, z0=0.59, seg=5, r_top=0, x=sx, y=-0.185)

# Pointed-arch ink door on the front (-Y): box + wedge apex.
add_box(coll, "PIP", 0.07, 0.02, 0.10, z0=0.05, y=-0.155)
add_wedge(coll, "PIP", 0.07, 0.02, 0.035, z0=0.15, y=-0.155)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.33)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
