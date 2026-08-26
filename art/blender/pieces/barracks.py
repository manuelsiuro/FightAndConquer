# BARRACKS — drill hall: stone-footed timber hall, faction gable roof, ridge
# pennant, spear rack and training pell in the front yard. Musters the soldier
# ladder — martial but unfortified, so it stays below the defense band
# (tower 0.465). H ~0.42, ~160 tris.
KIND = "BARRACKS"
PIECE = "barracks"
coll = reset_piece(KIND)

# Stone footing + timber drill hall (set back to leave a front yard at -Y).
add_box(coll, "STONE", 0.30, 0.20, 0.05, z0=0, y=0.04)
add_box(coll, "TRUNK", 0.28, 0.18, 0.15, z0=0.05, y=0.04)

# Faction gable roof — the ownership read.
add_wedge(coll, "FACTION", 0.32, 0.22, 0.10, z0=0.20, y=0.04)

# Ridge pennant pole (kept at y=0 — add_pennant lives in the y=0 plane).
add_cyl(coll, "TRUNK", r=0.008, h=0.12, z0=0.30, seg=6)
add_pennant(coll, "FACTION", 0.0, 0.42, 0.045, 0.09)

# Ink door proud of the front face.
add_box(coll, "PIP", 0.06, 0.012, 0.09, z0=0.05, y=-0.052)

# Spear rack leaned on the front wall.
for px in (-0.10, -0.065, -0.03):
    add_box(coll, "TRUNK", 0.008, 0.008, 0.17, z0=0.0, x=px, y=-0.07,
            rot=(radians(-12), 0, 0))

# Training pell in the yard: stone base, oak post, crossbar.
add_cyl(coll, "STONE", r=0.032, h=0.020, z0=0, seg=8, x=0.10, y=-0.13)
add_cyl(coll, "TRUNK", r=0.012, h=0.115, z0=0.02, seg=6, x=0.10, y=-0.13)
add_box(coll, "TRUNK", 0.09, 0.014, 0.014, z0=0.10, x=0.10, y=-0.13)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.20)
print("exported:", export_piece(PIECE, coll))
