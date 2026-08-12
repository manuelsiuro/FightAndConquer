# SULTANATE_FARM — oasis garden: low STONE wall square around a FACTION
# courtyard + FACTION gate arch + date palm + PIP water basin. H ~0.23, ~188 tris.
KIND = "SULTANATE_FARM"
PIECE = "farm"
coll = reset_piece(KIND)

# Faction courtyard pad — the big readable surface from above.
add_box(coll, "FACTION", 0.30, 0.30, 0.018, z0=0)

# Low sandstone wall square (front wall split around the gate).
add_box(coll, "STONE", 0.11, 0.03, 0.05, z0=0, x=-0.115, y=-0.17)
add_box(coll, "STONE", 0.11, 0.03, 0.05, z0=0, x=0.115, y=-0.17)
add_box(coll, "STONE", 0.37, 0.03, 0.05, z0=0, y=0.17)
add_box(coll, "STONE", 0.03, 0.31, 0.05, z0=0, x=-0.17)
add_box(coll, "STONE", 0.03, 0.31, 0.05, z0=0, x=0.17)

# Faction gate arch on the front (-Y): two posts + beam + pointed wedge cap.
add_box(coll, "FACTION", 0.035, 0.035, 0.10, z0=0, x=-0.055, y=-0.17)
add_box(coll, "FACTION", 0.035, 0.035, 0.10, z0=0, x=0.055, y=-0.17)
add_box(coll, "FACTION", 0.15, 0.035, 0.035, z0=0.10, y=-0.17)
add_wedge(coll, "FACTION", 0.15, 0.035, 0.04, z0=0.135, y=-0.17)

# Date palm: tapered trunk + 4 splayed frond cones.
add_cyl(coll, "TRUNK", r=0.025, h=0.17, z0=0, seg=6, r_top=0.017, x=0.06, y=0.05)
for k in range(4):
    a = radians(45) + 2 * math.pi * k / 4
    add_cyl(coll, "TREE_FOLIAGE", r=0.048, h=0.10, z0=0, seg=5, r_top=0,
            x=0.06 + 0.045 * math.sin(a), y=0.05 - 0.045 * math.cos(a),
            rot=(radians(70), 0, a), z_center=0.18)

# Small dark water basin disc beside the palm.
add_cyl(coll, "PIP", r=0.05, h=0.014, z0=0.018, seg=6, x=-0.08, y=0.02)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.10)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
