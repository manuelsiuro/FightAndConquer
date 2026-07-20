# UNIT_T2 — Spearman: tunic, helmet, round shield, gold-tipped spear. H 0.41, ~226 tris.
KIND = "UNIT_T2"
PIECE = "unit_t2"
coll = reset_piece(KIND)

add_cyl(coll, "FACTION", r=0.14, h=0.05, z0=0)                    # plinth
add_cyl(coll, "FACTION", r=0.095, h=0.16, z0=0.05, r_top=0.06)    # tunic
add_cyl(coll, "FACTION", r=0.058, h=0.075, z0=0.283, r_top=0)     # helmet, apex 0.358
add_sphere(coll, "STONE", r=0.048, z=0.245, seg=8, rings=4, y=-0.005)  # head
add_cyl(coll, "FACTION", r=0.058, h=0.016, z0=0, seg=8,           # round shield (left)
        rot=(radians(90), 0, 0), z_center=0.17, x=-0.075, y=-0.062)
add_sphere(coll, "GOLD", r=0.014, z=0.17, seg=6, rings=2, x=-0.075, y=-0.077)  # boss
add_cyl(coll, "TRUNK", r=0.014, h=0.30, z0=0.05, seg=6, x=0.105)  # spear shaft
add_sphere(coll, "GOLD", r=0.028, z=0.357, seg=4, rings=2, x=0.105,
           scale=(1, 1, 1.9))                                     # spearhead, tip 0.41
add_pips(coll, 2, ring_r=0.11, z0=0.05)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.2)
print("exported:", export_piece(PIECE, coll))
