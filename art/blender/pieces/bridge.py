# BRIDGE — timber deck on stone pylons spanning the hex, low railings and a
# faction pennant to read ownership. Authored along Y (runtime Y-rotates toward
# the connected shores). H 0.30, ~180 tris.
KIND = "BRIDGE"
PIECE = "bridge"
coll = reset_piece(KIND)

# Deck: near flat-to-flat span with a slight camber step.
add_box(coll, "TRUNK", 0.16, 0.84, 0.035, z0=0.085)
add_box(coll, "TRUNK", 0.18, 0.30, 0.045, z0=0.08)

# Stone pylon pairs.
for yy in (-0.26, 0.26):
    for xx in (-0.07, 0.07):
        add_box(coll, "STONE", 0.07, 0.07, 0.085, z0=0, x=xx, y=yy)

# Railing rails + posts along both edges.
for side in (-1, 1):
    add_box(coll, "PIP", 0.012, 0.78, 0.014, z0=0.155, x=side * 0.07)
    for yy in (-0.36, -0.12, 0.12, 0.36):
        add_box(coll, "PIP", 0.014, 0.014, 0.045, z0=0.12, x=side * 0.07, y=yy)

# Faction pennant on a short mast at mid-span.
add_cyl(coll, "TRUNK", r=0.008, h=0.14, z0=0.12, seg=6)
add_pennant(coll, "FACTION", pole_x=0.008, top_z=0.26, drop=0.04, length=0.08)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.14)
print("exported:", export_piece(PIECE, coll))
