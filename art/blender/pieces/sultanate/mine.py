# SULTANATE_MINE — arched adit: dune mound (squashed sphere) + pointed-arch
# STONE portal + dark PIP entrance wedge + GOLD ore glints + faction claim
# pennant. H ~0.30 with flag, ~184 tris.
KIND = "SULTANATE_MINE"
PIECE = "mine"
coll = reset_piece(KIND)

# Dune mound: squashed sandstone sphere.
add_sphere(coll, "STONE", r=0.17, z=0.096, seg=8, rings=4, scale=(1.15, 1.0, 0.6))

# Pointed-arch portal on the front (-Y): two inward-leaning jambs + apex block.
add_box(coll, "STONE", 0.032, 0.032, 0.15, z0=0, x=-0.055, y=-0.15, rot=(0, radians(16), 0))
add_box(coll, "STONE", 0.032, 0.032, 0.15, z0=0, x=0.055, y=-0.15, rot=(0, -radians(16), 0))
add_box(coll, "STONE", 0.05, 0.034, 0.035, z0=0.135, y=-0.15)

# Dark pointed entrance mouth (wedge gable faces -Y).
add_wedge(coll, "PIP", 0.09, 0.035, 0.10, z0=0, y=-0.145)

# Gold ore glints beside the entrance.
add_sphere(coll, "GOLD", r=0.030, z=0.028, seg=6, rings=3, x=0.135, y=-0.14)
add_sphere(coll, "GOLD", r=0.024, z=0.022, seg=6, rings=3, x=0.180, y=-0.09)
add_sphere(coll, "GOLD", r=0.020, z=0.058, seg=6, rings=3, x=0.150, y=-0.12)

# Faction claim pennant planted on the dune (pennant helper lives at y=0).
add_box(coll, "PIP", 0.008, 0.008, 0.13, z0=0.165, x=0.06, y=0.0)
add_pennant(coll, "FACTION", pole_x=0.06, top_z=0.295, drop=0.05, length=0.09)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.14)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
