# SULTANATE BANK — counting house: sandstone vault under a scalloped faction
# canopy, gold scales on the counter, coin dishes, arched ink grate.
# H ~0.28, ~320 tris.
KIND = "SULTANATE_BANK"
PIECE = "bank"
coll = reset_piece(KIND)

# Vault block + counter step.
add_box(coll, "STONE", 0.24, 0.16, 0.14, z0=0)
add_box(coll, "STONE", 0.26, 0.06, 0.09, z0=0, y=-0.11)

# Canopy on slender posts, scallops along the front edge.
for (px, py) in ((-0.12, -0.13), (0.12, -0.13), (-0.12, 0.07), (0.12, 0.07)):
    add_box(coll, "TRUNK", 0.018, 0.018, 0.18, z0=0, x=px, y=py)
add_box(coll, "FACTION", 0.28, 0.24, 0.02, z0=0.18, y=-0.03)
for px in (-0.09, 0.0, 0.09):
    add_cyl(coll, "FACTION", r=0.028, h=0.02, z0=0, seg=8, x=px, y=-0.15,
            rot=(0, radians(90), 0), z_center=0.18)

# Gold scales on the counter: post, beam, two dishes — and a coin stack.
add_box(coll, "TRUNK", 0.012, 0.012, 0.09, z0=0.09, x=-0.04, y=-0.11)
add_box(coll, "GOLD", 0.10, 0.010, 0.010, z0=0.175, x=-0.04, y=-0.11)
add_cyl(coll, "GOLD", r=0.020, h=0.008, z0=0.155, x=-0.085, y=-0.11)
add_cyl(coll, "GOLD", r=0.020, h=0.008, z0=0.155, x=0.005, y=-0.11)
add_cyl(coll, "GOLD", r=0.024, h=0.012, z0=0.09, x=0.07, y=-0.11)
add_cyl(coll, "GOLD", r=0.024, h=0.012, z0=0.102, x=0.07, y=-0.11)

# Arched ink grate on the vault face.
add_box(coll, "PIP", 0.06, 0.012, 0.06, z0=0.02, y=-0.084)
add_wedge(coll, "PIP", 0.06, 0.012, 0.025, z0=0.08, y=-0.084)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.13)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
