# MONSTER_WYRM — coiled serpent, neck reared to strike, ember glow at the chest,
# ink spines down the coil. The tier-3 apex of the night bestiary. H 0.56, ~420 tris.
KIND = "MONSTER_WYRM"
PIECE = "monster_wyrm"
coll = reset_piece(KIND)

# The coil: three settling loops.
add_sphere(coll, "STONE", r=0.165, z=0.065, seg=8, rings=4, scale=(1.0, 1.0, 0.42))
add_sphere(coll, "STONE", r=0.135, z=0.155, seg=8, rings=4, x=0.02, y=0.015, scale=(1.0, 1.0, 0.40))
add_sphere(coll, "STONE", r=0.105, z=0.235, seg=8, rings=4, x=-0.015, y=0.03, scale=(1.0, 1.0, 0.38))
# Reared neck, tilted toward the prey.
add_cyl(coll, "STONE", r=0.045, h=0.22, z0=0.26, seg=7, r_top=0.035, y=-0.03,
        rot=(radians(14), 0, 0))
# Head: snouted wedge.
add_sphere(coll, "STONE", r=0.055, z=0.495, seg=8, rings=4, y=-0.095, scale=(1.0, 1.35, 0.75))
add_cyl(coll, "STONE", r=0.02, h=0.05, z0=0.47, seg=5, r_top=0, y=-0.175,
        rot=(radians(105), 0, 0), z_center=0.485)
# Spines climbing the coil and neck.
add_cyl(coll, "PIP", r=0.018, h=0.05, z0=0.12, seg=5, r_top=0, x=0.12, y=0.075)
add_cyl(coll, "PIP", r=0.018, h=0.05, z0=0.205, seg=5, r_top=0, x=-0.07, y=0.09)
add_cyl(coll, "PIP", r=0.016, h=0.045, z0=0.275, seg=5, r_top=0, x=0.015, y=0.075)
add_cyl(coll, "PIP", r=0.014, h=0.04, z0=0.40, seg=5, r_top=0, y=0.035)
# The ember: a gold glow low on the reared chest, and gold eyes.
add_sphere(coll, "GOLD", r=0.03, z=0.345, seg=6, rings=3, y=-0.083)
add_sphere(coll, "GOLD", r=0.012, z=0.515, seg=6, rings=3, x=0.026, y=-0.135)
add_sphere(coll, "GOLD", r=0.012, z=0.515, seg=6, rings=3, x=-0.026, y=-0.135)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.28)
print("exported:", export_piece(PIECE, coll))
