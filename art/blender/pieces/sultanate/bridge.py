# SULTANATE BRIDGE — pointed-arch stone span on the Kingdom bridge footprint
# (same 0.84 span along Y, deck top z=0.12, stone footings at the same spots):
# two arch half-jambs angle up to a pip keystone under the cambered sandstone
# deck, low stone parapets, faction pennant. Authored along Y (runtime Y-rotates
# toward the connected shores). H 0.30, ~208 tris.
KIND = "SULTANATE_BRIDGE"
PIECE = "bridge"
coll = reset_piece(KIND)

# Sandstone deck: flat-to-flat span (top z=0.12) with a slight camber step.
add_box(coll, "STONE", 0.16, 0.84, 0.035, z0=0.085)
add_box(coll, "STONE", 0.18, 0.30, 0.045, z0=0.08)

# Stone footings — same positions as the Kingdom pylons.
for yy in (-0.26, 0.26):
    for xx in (-0.07, 0.07):
        add_box(coll, "STONE", 0.07, 0.07, 0.085, z0=0, x=xx, y=yy)

# Pointed arch under the deck: two half-jambs angling up from the footings to
# meet at mid-span, pip keystone at the apex.
add_box(coll, "STONE", 0.13, 0.30, 0.045, z0=0.0225, y=-0.135,
        rot=(radians(9), 0, 0))
add_box(coll, "STONE", 0.13, 0.30, 0.045, z0=0.0225, y=0.135,
        rot=(radians(-9), 0, 0))
add_box(coll, "PIP", 0.15, 0.06, 0.05, z0=0.035)

# Low stone parapet rails (tops at z=0.168) with end blocks.
for side in (-1, 1):
    add_box(coll, "STONE", 0.02, 0.78, 0.048, z0=0.12, x=side * 0.072)
    for yy in (-0.38, 0.38):
        add_box(coll, "STONE", 0.03, 0.05, 0.06, z0=0.12, x=side * 0.072, y=yy)

# Faction pennant on a short mast at mid-span (ownership read).
add_cyl(coll, "TRUNK", r=0.008, h=0.14, z0=0.12, seg=6)
add_pennant(coll, "FACTION", pole_x=0.008, top_z=0.26, drop=0.04, length=0.08)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.14)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
