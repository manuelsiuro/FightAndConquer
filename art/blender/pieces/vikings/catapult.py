# VIKINGS CATAPULT — ballista-sled: two TRUNK skid rails with upturned prows,
# crossbar frame, angled throwing spar with a PIP stone in a sling cup, winch
# drum, FACTION lashings. Matches Kingdom catapult footprint; top ~0.41. ~290 tris.
KIND = "VIKINGS_CATAPULT"
PIECE = "catapult"
coll = reset_piece(KIND)

# Base disc + strength pip (matches Kingdom catapult).
add_cyl(coll, "FACTION", r=0.155, h=0.03, z0=0)
add_pips(coll, 1, ring_r=0.145, z0=0.03)

# Two skid rails along Y with upturned prow tips at the front (-Y).
for sx in (-1, 1):
    add_box(coll, "TRUNK", 0.045, 0.30, 0.045, z0=0.03, x=sx * 0.10)
    add_box(coll, "TRUNK", 0.045, 0.08, 0.035, z0=0.045, x=sx * 0.10, y=-0.165,
            rot=(radians(-35), 0, 0))

# Cross-ties binding the sled together.
add_box(coll, "TRUNK", 0.245, 0.05, 0.035, z0=0.07, y=-0.085)
add_box(coll, "TRUNK", 0.245, 0.05, 0.035, z0=0.07, y=0.085)

# A-frame: two uprights + top crossbar the spar rests on (spar passes z~0.23 here).
add_box(coll, "TRUNK", 0.026, 0.026, 0.19, z0=0.03, x=0.07, y=0.06)
add_box(coll, "TRUNK", 0.026, 0.026, 0.19, z0=0.03, x=-0.07, y=0.06)
add_box(coll, "TRUNK", 0.185, 0.03, 0.03, z0=0.215, y=0.06)

# Throwing spar angled up toward the back (+Y): 0.42 beam at 52 deg from center
# z=0.1775 puts the raised tip at (y ~0.15, z ~0.34) — sling cup + stone sit there.
add_box(coll, "TRUNK", 0.035, 0.42, 0.035, z0=0.16, y=0.02, rot=(radians(52), 0, 0))
add_cyl(coll, "FACTION", r=0.045, h=0.02, z0=0.33, y=0.15, seg=7)   # sling cup
add_sphere(coll, "PIP", r=0.036, z=0.375, y=0.15, seg=8, rings=4)   # stone payload

# Winch drum across the front, between the rails.
add_cyl(coll, "TRUNK", r=0.032, h=0.15, z0=0, seg=6, y=-0.10,
        rot=(0, radians(90), 0), z_center=0.115)

# FACTION lashings — rope wraps on each rail + at the spar/crossbar junction.
add_box(coll, "FACTION", 0.055, 0.03, 0.055, z0=0.025, x=0.10, y=0.04)
add_box(coll, "FACTION", 0.055, 0.03, 0.055, z0=0.025, x=-0.10, y=0.04)
add_box(coll, "FACTION", 0.05, 0.05, 0.05, z0=0.205, y=0.06)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.20)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
