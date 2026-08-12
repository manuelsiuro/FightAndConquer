# SHOGUNATE_TOWER — yagura: squared timber tower under a top-heavy faction
# eave roof + gold finial + lattice hints + nobori banner at +X. H ~0.52, ~102 tris.
KIND = "SHOGUNATE_TOWER"
PIECE = "tower"
coll = reset_piece(KIND)

# Stone footing + squared timber shaft.
add_box(coll, "STONE", 0.26, 0.26, 0.04, z0=0)
add_box(coll, "TRUNK", 0.19, 0.19, 0.32, z0=0.04)

# Lattice hints flanking the front face.
add_box(coll, "PIP", 0.02, 0.016, 0.22, z0=0.08, x=-0.055, y=-0.097)
add_box(coll, "PIP", 0.02, 0.016, 0.22, z0=0.08, x=0.055, y=-0.097)

# Top-heavy faction eave roof over an overhanging board, gold ridge finial.
add_box(coll, "TRUNK", 0.30, 0.30, 0.02, z0=0.36)
add_wedge(coll, "FACTION", 0.34, 0.30, 0.09, z0=0.38)
add_cyl(coll, "GOLD", r=0.018, h=0.05, z0=0.47, seg=6, r_top=0)

# Vertical nobori banner on a pole at +X.
add_box(coll, "PIP", 0.012, 0.012, 0.30, z0=0.04, x=0.16)
add_box(coll, "FACTION", 0.055, 0.012, 0.16, z0=0.17, x=0.19)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.25)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
