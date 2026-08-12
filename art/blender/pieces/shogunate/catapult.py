# SHOGUNATE CATAPULT — oyumi siege crossbow: low TRUNK frame on four short legs,
# big bow prod swept across the front (-Y), PIP bolt lying in a rail channel,
# rear winch drum + tension mast with FACTION ropes, PIP lacquer side panels.
# Matches Kingdom catapult footprint; mast top 0.40. ~260 tris.
KIND = "SHOGUNATE_CATAPULT"
PIECE = "catapult"
coll = reset_piece(KIND)

# Base disc + strength pip (matches Kingdom catapult).
add_cyl(coll, "FACTION", r=0.155, h=0.03, z0=0)
add_pips(coll, 1, ring_r=0.145, z0=0.03)

# Four short legs + the low stock beam running front-to-back.
for sx in (-1, 1):
    for sy in (-1, 1):
        add_box(coll, "TRUNK", 0.035, 0.035, 0.06, z0=0.03, x=sx * 0.07, y=sy * 0.10)
add_box(coll, "TRUNK", 0.16, 0.30, 0.06, z0=0.09)

# Bow prod across the front: two TRUNK arms swept back like a great bow.
add_box(coll, "TRUNK", 0.17, 0.03, 0.025, z0=0.13, x=0.095, y=-0.115, rot=(0, 0, radians(20)))
add_box(coll, "TRUNK", 0.17, 0.03, 0.025, z0=0.13, x=-0.095, y=-0.115, rot=(0, 0, radians(-20)))

# Bolt channel rails atop the stock + the PIP bolt between them, tip out front.
add_box(coll, "TRUNK", 0.02, 0.28, 0.035, z0=0.15, x=0.035)
add_box(coll, "TRUNK", 0.02, 0.28, 0.035, z0=0.15, x=-0.035)
add_box(coll, "PIP", 0.02, 0.28, 0.02, z0=0.155, y=-0.03)

# Rear winch drum (axis along X) wrapped with a FACTION rope band.
add_cyl(coll, "TRUNK", r=0.035, h=0.14, z0=0, seg=6, y=0.13,
        rot=(0, radians(90), 0), z_center=0.17)
add_box(coll, "FACTION", 0.06, 0.074, 0.074, z0=0.133, y=0.13)

# Tension mast at the back (top 0.40) with FACTION ropes running down to the prod.
add_box(coll, "TRUNK", 0.028, 0.028, 0.27, z0=0.13, y=0.10)
add_box(coll, "FACTION", 0.012, 0.012, 0.33, z0=0.105, x=0.025, y=-0.0075,
        rot=(radians(-40), 0, 0))
add_box(coll, "FACTION", 0.012, 0.012, 0.33, z0=0.105, x=-0.025, y=-0.0075,
        rot=(radians(-40), 0, 0))

# PIP lacquer panels on the flanks.
add_box(coll, "PIP", 0.012, 0.20, 0.05, z0=0.095, x=0.086)
add_box(coll, "PIP", 0.012, 0.20, 0.05, z0=0.095, x=-0.086)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.20)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
