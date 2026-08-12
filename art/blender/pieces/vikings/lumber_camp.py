# VIKINGS_LUMBER_CAMP — log pile along X under a faction tarp + chopping stump
# with ink axe + loose log + split firewood. H ~0.14, ~224 tris.
KIND = "VIKINGS_LUMBER_CAMP"
PIECE = "lumber_camp"
coll = reset_piece(KIND)

# Log pile: 3 + 2 pyramid, log axes along X (rotation bakes into the mesh).
for (y, z) in ((-0.01, 0.035), (0.06, 0.035), (0.13, 0.035)):
    add_cyl(coll, "TRUNK", r=0.035, h=0.22, z0=0, seg=7, x=-0.03, y=y,
            rot=(0, radians(90), 0), z_center=z)
for (y, z) in ((0.025, 0.095), (0.095, 0.095)):
    add_cyl(coll, "TRUNK", r=0.035, h=0.20, z0=0, seg=7, x=-0.03, y=y,
            rot=(0, radians(90), 0), z_center=z)

# Faction tarp strip lashed over the +X end of the pile.
add_box(coll, "FACTION", 0.10, 0.17, 0.014, z0=0.126, x=0.05, y=0.06,
        rot=(0, radians(12), 0))

# Chopping stump with an ink axe sunk into it.
add_cyl(coll, "TRUNK", r=0.05, h=0.07, z0=0, x=0.16, y=-0.13)
add_box(coll, "TRUNK", 0.014, 0.014, 0.13, z0=0.06, x=0.145, y=-0.145,
        rot=(radians(18), radians(-14), 0))
add_box(coll, "PIP", 0.05, 0.018, 0.045, z0=0.155, x=0.125, y=-0.165)

# Loose log on the ground + split firewood by the stump.
add_cyl(coll, "TRUNK", r=0.03, h=0.18, z0=0, seg=7, x=0.14, y=0.10,
        rot=(radians(90), 0, 0), z_center=0.03)
add_wedge(coll, "TRUNK", 0.05, 0.05, 0.05, z0=0, x=0.05, y=-0.17)
add_wedge(coll, "TRUNK", 0.05, 0.05, 0.05, z0=0, x=-0.03, y=-0.15, rot_z=radians(40))

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.10)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
