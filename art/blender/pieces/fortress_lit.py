# FORTRESS_LIT — the fortress with its beacon lit: the keep's gold finial
# becomes a proper brazier fire on a stone bowl. H ~0.60, ~485 tris.
KIND = "FORTRESS_LIT"
PIECE = "fortress_lit"
coll = reset_piece(KIND)

# Outer curtain walls (square ring).
add_box(coll, "STONE", 0.36, 0.05, 0.15, z0=0, y=-0.19)
add_box(coll, "STONE", 0.36, 0.05, 0.15, z0=0, y=0.19)
add_box(coll, "STONE", 0.05, 0.36, 0.15, z0=0, x=-0.19)
add_box(coll, "STONE", 0.05, 0.36, 0.15, z0=0, x=0.19)

# Corner turrets with caps and three merlons each.
for sx in (-0.19, 0.19):
    for sy in (-0.19, 0.19):
        add_cyl(coll, "STONE", r=0.055, h=0.26, z0=0, seg=6, x=sx, y=sy)
        add_cyl(coll, "STONE", r=0.068, h=0.028, z0=0.26, seg=6, x=sx, y=sy)
        for deg in (90, 210, 330):
            a = radians(deg)
            add_box(coll, "STONE", 0.036, 0.028, 0.038, z0=0.288,
                    x=sx + 0.048 * math.cos(a), y=sy + 0.048 * math.sin(a), rot=(0, 0, a))

# Central keep: faction band near the top, corner merlons.
add_box(coll, "STONE", 0.18, 0.18, 0.50, z0=0)
add_box(coll, "FACTION", 0.19, 0.19, 0.05, z0=0.42)
for sx in (-0.065, 0.065):
    for sy in (-0.065, 0.065):
        add_box(coll, "STONE", 0.05, 0.05, 0.05, z0=0.50, x=sx, y=sy)

# The beacon: the finial grows into a keep-top brazier — stone bowl + gold fire.
add_cyl(coll, "STONE", r=0.045, h=0.03, z0=0.50, seg=6)
add_sphere(coll, "GOLD", r=0.036, z=0.565, seg=6, rings=3)

# Gatehouse on the front wall (-Y): ink gate proud of the face + faction banner.
add_box(coll, "PIP", 0.08, 0.05, 0.11, z0=0, y=-0.20)
add_box(coll, "FACTION", 0.10, 0.02, 0.055, z0=0.11, y=-0.213)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.29)
print("exported:", export_piece(PIECE, coll))
