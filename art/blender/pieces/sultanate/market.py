# SULTANATE MARKET — bazaar stall: sandstone counter under a scalloped striped
# canopy (alternating faction/stone wedges) on four trunk poles, gold coin dish
# on the counter, crates beside, a rolled carpet at the front. H 0.26, ~184 tris.
KIND = "SULTANATE_MARKET"
PIECE = "market"
coll = reset_piece(KIND)

# Sandstone counter.
add_box(coll, "STONE", 0.24, 0.16, 0.09, z0=0)

# Corner poles holding the canopy.
for (x, y) in ((-0.13, -0.09), (0.13, -0.09), (-0.13, 0.09), (0.13, 0.09)):
    add_box(coll, "TRUNK", 0.022, 0.022, 0.235, z0=0, x=x, y=y)

# Striped scalloped awning: alternating faction/stone wedges side by side,
# ridges along Y, forming one canopy with a slight overhang.
for i, role in enumerate(("FACTION", "STONE", "FACTION", "STONE", "FACTION")):
    add_wedge(coll, role, 0.062, 0.24, 0.028, z0=0.235, x=-0.124 + 0.062 * i)

# Gold coin dish + coin pile on the counter.
add_cyl(coll, "GOLD", r=0.036, h=0.010, z0=0.09, seg=8, x=0.05, y=0.02)
add_cyl(coll, "GOLD", r=0.018, h=0.014, z0=0.10, seg=6, x=0.05, y=0.02)

# Crates by the stall.
add_box(coll, "TRUNK", 0.06, 0.06, 0.06, z0=0, x=-0.18, y=-0.12)
add_box(coll, "TRUNK", 0.05, 0.05, 0.05, z0=0, x=-0.11, y=-0.17,
        rot=(0, 0, radians(20)))

# Rolled carpet at the front.
add_box(coll, "FACTION", 0.11, 0.035, 0.035, z0=0, x=0.04, y=-0.14,
        rot=(0, 0, radians(8)))

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.14)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
