# UNIVERSITY — cloistered stone hall: steep gable, ridge bell arch with a GOLD
# bell, faction door banner, ink window bands. Civic band between economy and
# defense — reads important, not military. H ~0.43, ~300 tris.
KIND = "UNIVERSITY"
PIECE = "university"
coll = reset_piece(KIND)

# Stone hall + entry steps.
add_box(coll, "STONE", 0.30, 0.22, 0.20, z0=0)
add_box(coll, "STONE", 0.12, 0.05, 0.02, z0=0, y=-0.13)

# Steep scriptorium gable (ridge along Y).
add_wedge(coll, "TRUNK", 0.34, 0.26, 0.15, z0=0.20)

# Ridge bell arch: two posts, a lintel, the gold bell between.
add_box(coll, "STONE", 0.02, 0.03, 0.055, z0=0.35, x=-0.033)
add_box(coll, "STONE", 0.02, 0.03, 0.055, z0=0.35, x=0.033)
add_box(coll, "STONE", 0.09, 0.035, 0.02, z0=0.405)
add_sphere(coll, "GOLD", r=0.024, z=0.385, seg=8, rings=4)

# Faction door banner on the front (-Y) — the ownership read.
add_box(coll, "FACTION", 0.09, 0.012, 0.13, z0=0.03, y=-0.115)

# Ink window bands along both long walls.
add_box(coll, "PIP", 0.24, 0.006, 0.03, z0=0.12, y=-0.111)
add_box(coll, "PIP", 0.24, 0.006, 0.03, z0=0.12, y=0.111)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.21)
print("exported:", export_piece(PIECE, coll))
