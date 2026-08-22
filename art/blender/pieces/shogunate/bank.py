# SHOGUNATE BANK — kura storehouse: thick white-plaster box on a raised timber
# sill, faction gable, banded ink door, koban gold stacked on a pallet.
# H ~0.32, ~260 tris.
KIND = "SHOGUNATE_BANK"
PIECE = "bank"
coll = reset_piece(KIND)

# Raised sill + plaster storehouse.
add_box(coll, "TRUNK", 0.26, 0.20, 0.03, z0=0)
add_box(coll, "STONE", 0.22, 0.16, 0.20, z0=0.03)

# Faction gable + timber ridge cap.
add_wedge(coll, "FACTION", 0.26, 0.20, 0.08, z0=0.23)
add_box(coll, "TRUNK", 0.02, 0.21, 0.014, z0=0.31)

# Banded ink door + namako-wall bands (front, -Y).
add_box(coll, "PIP", 0.06, 0.012, 0.13, z0=0.05, y=-0.083)
add_box(coll, "PIP", 0.20, 0.008, 0.012, z0=0.10, y=-0.083)
add_box(coll, "PIP", 0.20, 0.008, 0.012, z0=0.16, y=-0.083)

# Koban gold on a pallet by the door.
add_box(coll, "TRUNK", 0.07, 0.05, 0.015, z0=0, x=0.16, y=-0.09)
add_sphere(coll, "GOLD", r=0.022, z=0.026, seg=8, rings=3, x=0.15, y=-0.095, scale=(1.5, 1, 0.4))
add_sphere(coll, "GOLD", r=0.022, z=0.043, seg=8, rings=3, x=0.165, y=-0.085, scale=(1.5, 1, 0.4))

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.15)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
