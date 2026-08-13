# SULTANATE PORT — stone quay: sandstone mole running to the sea (-Y) through a
# pointed-arch gate (two jambs + angled apex) flying a faction pennant, mooring
# bollards, a small moored dhow with furled sail, gold lantern at the mole tip.
# H 0.38, ~200 tris.
KIND = "SULTANATE_PORT"
PIECE = "port"
coll = reset_piece(KIND)

# Quay slab (land side, +Y) and the mole running toward the water (-Y).
add_box(coll, "STONE", 0.32, 0.24, 0.06, z0=0, y=0.14)
add_box(coll, "STONE", 0.14, 0.32, 0.05, z0=0, y=-0.14)

# Pointed-arch gate astride the mole at y=0: two jambs + two members angling
# up to the apex.
add_box(coll, "STONE", 0.045, 0.05, 0.15, z0=0.05, x=-0.075)
add_box(coll, "STONE", 0.045, 0.05, 0.15, z0=0.05, x=0.075)
add_box(coll, "STONE", 0.045, 0.05, 0.11, z0=0.20, x=-0.045,
        rot=(0, radians(32), 0))
add_box(coll, "STONE", 0.045, 0.05, 0.11, z0=0.20, x=0.045,
        rot=(0, radians(-32), 0))
# Faction pennant on a short staff at the apex (ownership read).
add_box(coll, "TRUNK", 0.012, 0.012, 0.09, z0=0.29)
add_pennant(coll, "FACTION", pole_x=0.006, top_z=0.375, drop=0.035, length=0.07)

# Mooring bollards at the mole tip.
add_cyl(coll, "PIP", r=0.012, h=0.03, z0=0.05, seg=6, x=-0.05, y=-0.26)
add_cyl(coll, "PIP", r=0.012, h=0.03, z0=0.05, seg=6, x=0.05, y=-0.26)

# Small moored dhow alongside the mole (sits on the water plane), furled
# lateen on a raked mast.
add_box(coll, "TRUNK", 0.075, 0.20, 0.045, z0=0, x=0.16, y=-0.10)
add_cyl(coll, "TRUNK", r=0.008, h=0.15, z0=0.045, seg=6, x=0.16, y=-0.12,
        rot=(radians(7), 0, 0))
add_box(coll, "TRUNK", 0.02, 0.18, 0.02, z0=0.12, x=0.16, y=-0.11,
        rot=(radians(40), 0, 0))

# Gold lantern on a post by the water.
add_box(coll, "TRUNK", 0.012, 0.012, 0.11, z0=0.05, x=-0.10, y=-0.24)
add_box(coll, "GOLD", 0.032, 0.032, 0.04, z0=0.16, x=-0.10, y=-0.24)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.17)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
