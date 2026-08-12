# SHOGUNATE_LUMBER_CAMP — beam yard: crossed layers of squared trunk beams
# under a faction tarp strip + saw trestle with a beam and sunk saw blade +
# round offcuts. H ~0.17, ~208 tris.
KIND = "SHOGUNATE_LUMBER_CAMP"
PIECE = "lumber_camp"
coll = reset_piece(KIND)

# Beam stack: bottom layer along Y, crossed top layer along X.
for x in (-0.07, 0.0, 0.07):
    add_box(coll, "TRUNK", 0.045, 0.26, 0.045, z0=0, x=x, y=0.08)
for y in (0.0, 0.08, 0.16):
    add_box(coll, "TRUNK", 0.24, 0.045, 0.045, z0=0.045, y=y)

# Faction tarp strip draped over the stack.
add_box(coll, "FACTION", 0.20, 0.16, 0.02, z0=0.095, y=0.12, rot=(radians(-10), 0, 0))

# Saw trestle: two A-frames + cross-bar + beam being cut.
for sy in (-0.10, -0.20):
    add_box(coll, "TRUNK", 0.02, 0.02, 0.13, z0=0, x=0.14, y=sy, rot=(0, radians(14), 0))
    add_box(coll, "TRUNK", 0.02, 0.02, 0.13, z0=0, x=0.22, y=sy, rot=(0, -radians(14), 0))
add_box(coll, "TRUNK", 0.02, 0.12, 0.02, z0=0.06, x=0.18, y=-0.15)
add_box(coll, "TRUNK", 0.05, 0.24, 0.045, z0=0.125, x=0.18, y=-0.15)

# Saw blade sunk into the trestle beam.
add_box(coll, "PIP", 0.012, 0.12, 0.09, z0=0.10, x=0.18, y=-0.15,
        rot=(radians(22), 0, 0))

# Round offcuts by the yard.
add_cyl(coll, "TRUNK", r=0.040, h=0.05, z0=0, seg=6, x=-0.20, y=-0.15)
add_cyl(coll, "TRUNK", r=0.032, h=0.04, z0=0, seg=6, x=-0.12, y=-0.20)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.09)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
