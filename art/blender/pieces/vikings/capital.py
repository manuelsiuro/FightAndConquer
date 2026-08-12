# VIKINGS_CAPITAL — mead-hall: faction plinth + timber longhouse + crossed gable
# beams + wall shields + gold dragon finials + center spike. H 0.67, ~226 tris.
KIND = "VIKINGS_CAPITAL"
PIECE = "capital"
coll = reset_piece(KIND)

# Low faction plinth — the big "this is mine" surface.
add_box(coll, "FACTION", 0.40, 0.32, 0.05, z0=0)

# Longhouse body + gabled roof, ridge along Y (front gable faces -Y).
add_box(coll, "TRUNK", 0.20, 0.36, 0.24, z0=0.05)
add_wedge(coll, "TRUNK", 0.24, 0.40, 0.26, z0=0.29)

# Crossed gable-end beams (X above each gable).
for gy in (-0.205, 0.205):
    for tilt in (radians(22), -radians(22)):
        add_box(coll, "PIP", 0.02, 0.02, 0.26, z0=0.36, y=gy, rot=(0, tilt, 0))

# Faction door frame + ink door + banner on the front gable.
add_box(coll, "FACTION", 0.10, 0.02, 0.15, z0=0.05, y=-0.185)
add_box(coll, "PIP", 0.06, 0.02, 0.11, z0=0.05, y=-0.19)
add_box(coll, "FACTION", 0.09, 0.014, 0.16, z0=0.32, y=-0.204)

# Round faction shields hung along both side walls.
for sx in (-0.106, 0.106):
    for sy in (-0.08, 0.06):
        add_cyl(coll, "FACTION", r=0.035, h=0.012, z0=0, seg=6,
                x=sx, y=sy, rot=(0, radians(90), 0), z_center=0.17)

# Gold dragon-head finials at the ridge ends, curling up and outward.
for (gy, tilt) in ((-0.19, -radians(50)), (0.19, radians(50))):
    add_cyl(coll, "GOLD", r=0.032, h=0.09, z0=0, seg=6, r_top=0,
            y=gy, rot=(tilt, 0, 0), z_center=0.57)

# Center ridge spike — tallest point of the civilization.
add_cyl(coll, "GOLD", r=0.020, h=0.12, z0=0.55, seg=6, r_top=0)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.34)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
