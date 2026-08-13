# SULTANATE UNIT_T2 — Spearman: FACTION robe, pointed STONE helm with GOLD tip,
# round shield at the front with GOLD boss, spear with PIP leaf blade (tip 0.41).
# H 0.41, ~252 tris.
KIND = "SULTANATE_UNIT_T2"
PIECE = "unit_t2"
coll = reset_piece(KIND)

add_cyl(coll, "FACTION", r=0.14, h=0.05, z0=0)                    # plinth (matches Kingdom T2)
add_cyl(coll, "FACTION", r=0.095, h=0.16, z0=0.05, r_top=0.06)    # robe
add_sphere(coll, "STONE", r=0.048, z=0.245, seg=8, rings=4, y=-0.005)  # head
add_cyl(coll, "STONE", r=0.055, h=0.07, z0=0.278, r_top=0)        # pointed helm, apex 0.348
add_cyl(coll, "GOLD", r=0.012, h=0.03, z0=0.34, seg=6, r_top=0)   # gold tip cone, 0.37
add_cyl(coll, "FACTION", r=0.062, h=0.016, z0=0, seg=8,           # round shield at the front (-Y)
        rot=(radians(90), 0, 0), z_center=0.16, y=-0.085)
add_sphere(coll, "GOLD", r=0.014, z=0.16, seg=6, rings=2, y=-0.10)  # shield boss
add_cyl(coll, "TRUNK", r=0.014, h=0.30, z0=0.05, seg=6, x=0.105)  # spear shaft at +X
add_sphere(coll, "PIP", r=0.03, z=0.35, seg=6, rings=3, x=0.105,
           scale=(1, 0.45, 2.0))                                  # leaf blade, tip 0.41
add_pips(coll, 2, ring_r=0.11, z0=0.05)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.2)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
