# VIKINGS_WATCHTOWER — stilt platform: 4 timber legs + cross braces + open
# faction rail + brazier with gold flame. H ~0.63, ~162 tris.
KIND = "VIKINGS_WATCHTOWER"
PIECE = "watchtower"
coll = reset_piece(KIND)

# Four legs leaning slightly inward.
for (sx, sy) in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
    add_box(coll, "TRUNK", 0.024, 0.024, 0.50, z0=0, x=sx * 0.085, y=sy * 0.085,
            rot=(sy * radians(4), sx * radians(-4), 0))

# Cross braces on the front and back faces.
add_box(coll, "TRUNK", 0.16, 0.016, 0.016, z0=0.18, y=-0.088, rot=(0, radians(30), 0))
add_box(coll, "TRUNK", 0.16, 0.016, 0.016, z0=0.18, y=0.088, rot=(0, -radians(30), 0))

# Platform deck.
add_box(coll, "TRUNK", 0.18, 0.18, 0.025, z0=0.50)

# Open faction rail: four low planks around the edge, open above.
add_box(coll, "FACTION", 0.19, 0.016, 0.035, z0=0.525, y=-0.082)
add_box(coll, "FACTION", 0.19, 0.016, 0.035, z0=0.525, y=0.082)
add_box(coll, "FACTION", 0.016, 0.19, 0.035, z0=0.525, x=-0.082)
add_box(coll, "FACTION", 0.016, 0.19, 0.035, z0=0.525, x=0.082)

# Brazier bowl + gold flame cone.
add_cyl(coll, "STONE", r=0.034, h=0.032, z0=0.525, seg=6)
add_cyl(coll, "GOLD", r=0.027, h=0.075, z0=0.557, seg=6, r_top=0)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.30)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
