# SHOGUNATE WARSHIP — atakebune: boxy armored gunship, full-length stone-plated
# "turtle house" with pip gun/oar slits, battened faction square sail, gold bow
# crest, command banner at the stern. Front faces -Y. H 0.56, ~244 tris.
KIND = "SHOGUNATE_WARSHIP"
PIECE = "warship"
coll = reset_piece(KIND)

# Hull: longer and wider than the junk, boxy keel + gunwale.
add_box(coll, "TRUNK", 0.20, 0.52, 0.06, z0=0)
add_box(coll, "TRUNK", 0.24, 0.56, 0.05, z0=0.06)

# Turtle house: full-length armored plating box + low plated ridge roof.
add_box(coll, "STONE", 0.20, 0.40, 0.09, z0=0.11)
add_wedge(coll, "STONE", 0.22, 0.42, 0.045, z0=0.20)

# Gun/oar slits: three thin dark boxes per side, punched through the plating.
for side in (-1, 1):
    for yy in (-0.13, 0.0, 0.13):
        add_box(coll, "PIP", 0.012, 0.06, 0.025, z0=0.14, x=side * 0.103, y=yy)

# Center mast + battened square faction sail + yard.
add_cyl(coll, "TRUNK", r=0.013, h=0.36, z0=0.20, seg=6)
add_box(coll, "TRUNK", 0.22, 0.014, 0.014, z0=0.465)
add_box(coll, "FACTION", 0.20, 0.008, 0.15, z0=0.31, y=0.012)
add_box(coll, "PIP", 0.205, 0.006, 0.016, z0=0.35, y=0.017)
add_box(coll, "PIP", 0.205, 0.006, 0.016, z0=0.41, y=0.017)

# Gold crest disc at the bow, facing forward (-Y).
add_cyl(coll, "GOLD", r=0.028, h=0.014, z0=0, seg=8, y=-0.285,
        rot=(radians(90), 0, 0), z_center=0.10)

# Command banner at the stern: short pole + small faction banner.
add_cyl(coll, "TRUNK", r=0.008, h=0.16, z0=0.11, seg=6, y=0.25)
add_box(coll, "FACTION", 0.045, 0.008, 0.08, z0=0.18, x=0.030, y=0.25)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.26)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
