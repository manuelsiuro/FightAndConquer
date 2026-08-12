# SULTANATE CATAPULT — torsion mangonel: STONE base frame + uprights, TRUNK
# torsion skein + throwing arm angled up with a FACTION sling, GOLD counterweight
# dome at the pivot, PIP stone. Kingdom catapult footprint; top ~0.39. ~268 tris.
KIND = "SULTANATE_CATAPULT"
PIECE = "catapult"
coll = reset_piece(KIND)

# Base disc + strength pip (matches Kingdom catapult).
add_cyl(coll, "FACTION", r=0.155, h=0.03, z0=0)
add_pips(coll, 1, ring_r=0.145, z0=0.03)

# STONE base frame: two side rails along Y + front/back cross members.
add_box(coll, "STONE", 0.05, 0.30, 0.06, z0=0.03, x=0.10)
add_box(coll, "STONE", 0.05, 0.30, 0.06, z0=0.03, x=-0.10)
add_box(coll, "STONE", 0.25, 0.05, 0.05, z0=0.03, y=-0.10)
add_box(coll, "STONE", 0.25, 0.05, 0.05, z0=0.03, y=0.10)

# Torsion skein across X at the pivot + STONE uprights with a padded stop bar.
add_cyl(coll, "TRUNK", r=0.03, h=0.22, z0=0, seg=6, y=0.02,
        rot=(0, radians(90), 0), z_center=0.115)
add_box(coll, "STONE", 0.03, 0.03, 0.12, z0=0.06, x=0.095, y=0.02)
add_box(coll, "STONE", 0.03, 0.03, 0.12, z0=0.06, x=-0.095, y=0.02)
add_box(coll, "STONE", 0.22, 0.04, 0.04, z0=0.175, y=0.02)

# Throwing arm angled up toward the back (+Y): a 0.40 beam at 50 deg from center
# z=0.16 puts the raised tip at (y ~0.15, z ~0.31) — sling + stone sit there.
add_box(coll, "TRUNK", 0.035, 0.40, 0.035, z0=0.1425, y=0.02, rot=(radians(50), 0, 0))
add_cyl(coll, "FACTION", r=0.045, h=0.02, z0=0.30, y=0.15, seg=7)   # faction sling cup
add_sphere(coll, "PIP", r=0.036, z=0.35, y=0.15, seg=8, rings=4)    # stone payload

# GOLD counterweight dome straddling the pivot.
add_sphere(coll, "GOLD", r=0.055, z=0.16, seg=8, rings=3, y=0.02,
           scale=(1, 1, 0.65))

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.20)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
