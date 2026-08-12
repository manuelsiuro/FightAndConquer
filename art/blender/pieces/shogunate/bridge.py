# SHOGUNATE BRIDGE — drum bridge: red-lacquer deck in three segments arching
# gently over the Kingdom bridge footprint (same 0.84 span along Y, deck top
# z=0.12 at the ends, stone footings at the same spots), pip rails with gold
# giboshi caps on the center posts, faction pennant. Authored along Y (runtime
# Y-rotates toward the connected shores). H 0.30, ~228 tris.
KIND = "SHOGUNATE_BRIDGE"
PIECE = "bridge"
coll = reset_piece(KIND)

# Deck: two end segments (tops at z=0.12) + a raised mid segment (drum camber).
add_box(coll, "PIP", 0.16, 0.32, 0.035, z0=0.085, y=-0.26)
add_box(coll, "PIP", 0.16, 0.32, 0.035, z0=0.085, y=0.26)
add_box(coll, "PIP", 0.16, 0.24, 0.035, z0=0.105)  # mid-span rise, top z=0.14

# Stone footings — same positions as the Kingdom pylons.
for yy in (-0.26, 0.26):
    for xx in (-0.07, 0.07):
        add_box(coll, "STONE", 0.07, 0.07, 0.085, z0=0, x=xx, y=yy)

# Lacquered rails: end posts, taller center posts with gold giboshi caps,
# top rails at z~0.168 along both edges.
for side in (-1, 1):
    add_box(coll, "PIP", 0.012, 0.78, 0.014, z0=0.155, x=side * 0.07)
    for yy in (-0.36, 0.36):
        add_box(coll, "PIP", 0.014, 0.014, 0.06, z0=0.12, x=side * 0.07, y=yy)
    add_box(coll, "PIP", 0.016, 0.016, 0.055, z0=0.14, x=side * 0.07, y=0.0)
    add_cyl(coll, "GOLD", r=0.014, h=0.025, z0=0.195, seg=6, r_top=0.0, x=side * 0.07)

# Faction pennant on a short mast at mid-span (ownership read).
add_cyl(coll, "TRUNK", r=0.008, h=0.12, z0=0.14, seg=6)
add_pennant(coll, "FACTION", pole_x=0.008, top_z=0.26, drop=0.04, length=0.08)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.14)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
