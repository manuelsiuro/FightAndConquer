# VIKINGS BRIDGE — log causeway: three lying trunk logs on the Kingdom bridge
# footprint (same 0.84 span along Y, deck top ~0.12, stone footings at the same
# spots), pip rope rails, faction pennant. Authored along Y (runtime Y-rotates
# toward the connected shores). H 0.30, ~220 tris.
KIND = "VIKINGS_BRIDGE"
PIECE = "bridge"
coll = reset_piece(KIND)

# Deck: three logs spanning flat-to-flat; tops at z~0.12 like the Kingdom deck.
for xx in (-0.058, 0.0, 0.058):
    add_cyl(coll, "TRUNK", r=0.028, h=0.84, z0=0, seg=6, x=xx,
            rot=(radians(90), 0, 0), z_center=0.092)
# Mid-span lashed plank (matches the Kingdom camber step).
add_box(coll, "TRUNK", 0.17, 0.28, 0.014, z0=0.118)

# Stone footings — same positions as the Kingdom pylons.
for yy in (-0.26, 0.26):
    for xx in (-0.07, 0.07):
        add_box(coll, "STONE", 0.07, 0.07, 0.075, z0=0, x=xx, y=yy)

# Low rope rails: posts + very thin pip rope lines along both edges.
for side in (-1, 1):
    for yy in (-0.32, 0.32):
        add_box(coll, "TRUNK", 0.016, 0.016, 0.07, z0=0.11, x=side * 0.075, y=yy)
    add_cyl(coll, "PIP", r=0.005, h=0.70, z0=0, seg=4, x=side * 0.075,
            rot=(radians(90), 0, 0), z_center=0.168)

# Faction pennant on a short mast at mid-span (ownership read).
add_cyl(coll, "TRUNK", r=0.008, h=0.14, z0=0.12, seg=6)
add_pennant(coll, "FACTION", pole_x=0.008, top_z=0.26, drop=0.04, length=0.08)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.14)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
