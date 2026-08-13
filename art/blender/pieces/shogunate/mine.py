# SHOGUNATE_MINE — torii-framed adit: stone mound + red-lacquer torii portal
# (double lintel, curved kasagi via two angled halves) before a dark mouth +
# gold ore glints + faction cloth and pennant. H ~0.34, ~208 tris.
KIND = "SHOGUNATE_MINE"
PIECE = "mine"
coll = reset_piece(KIND)

# Stone hill mound (two stacked frustums).
add_cyl(coll, "STONE", r=0.18, h=0.15, z0=0, r_top=0.11)
add_cyl(coll, "STONE", r=0.11, h=0.07, z0=0.15, r_top=0.06)

# Dark adit mouth on the front (-Y).
add_box(coll, "PIP", 0.09, 0.02, 0.085, z0=0, y=-0.162)

# Red-lacquer torii portal in front of the mouth: posts + nuki + curved kasagi.
add_box(coll, "PIP", 0.024, 0.024, 0.15, z0=0, x=-0.075, y=-0.21)
add_box(coll, "PIP", 0.024, 0.024, 0.15, z0=0, x=0.075, y=-0.21)
add_box(coll, "PIP", 0.19, 0.024, 0.022, z0=0.105, y=-0.21)
add_box(coll, "PIP", 0.12, 0.03, 0.026, z0=0.15, x=-0.055, y=-0.21,
        rot=(0, radians(7), 0))
add_box(coll, "PIP", 0.12, 0.03, 0.026, z0=0.15, x=0.055, y=-0.21,
        rot=(0, -radians(7), 0))

# Faction cloth hanging from the nuki.
add_box(coll, "FACTION", 0.06, 0.014, 0.05, z0=0.055, y=-0.21)

# Gold ore glints at the mouth.
add_sphere(coll, "GOLD", r=0.026, z=0.026, seg=6, rings=3, x=0.13, y=-0.15)
add_sphere(coll, "GOLD", r=0.020, z=0.020, seg=6, rings=3, x=0.16, y=-0.09)

# Faction claim pennant on the mound (pennant helper lives at y=0).
add_box(coll, "PIP", 0.008, 0.008, 0.12, z0=0.22, x=0.05, y=0.0)
add_pennant(coll, "FACTION", pole_x=0.05, top_z=0.34, drop=0.05, length=0.09)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.15)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
