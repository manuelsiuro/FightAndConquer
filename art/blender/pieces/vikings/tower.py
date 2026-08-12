# VIKINGS_TOWER — rune stone: faction base ring + stone step + tapered monolith
# + spiral of dark rune bands. H ~0.505, ~132 tris.
KIND = "VIKINGS_TOWER"
PIECE = "tower"
coll = reset_piece(KIND)

# Faction base ring + stone step.
add_cyl(coll, "FACTION", r=0.15, h=0.035, z0=0)
add_cyl(coll, "STONE", r=0.13, h=0.04, z0=0.035)

# Tapered monolith with a rounded crown.
add_cyl(coll, "STONE", r=0.11, h=0.40, z0=0.075, seg=6, r_top=0.06)
add_cyl(coll, "STONE", r=0.06, h=0.03, z0=0.475, seg=6, r_top=0.035)

# Rune bands on the front face, tilted alternately — a spiraling suggestion.
for (bz, by, tilt) in ((0.14, -0.082, radians(10)),
                       (0.22, -0.075, -radians(10)),
                       (0.30, -0.067, radians(10))):
    add_box(coll, "PIP", 0.12, 0.022, 0.02, z0=bz, y=by, rot=(0, tilt, 0))

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.25)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
