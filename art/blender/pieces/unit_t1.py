# UNIT_T1 — Peasant: hooded robe, face peeking out, sack at foot. H 0.30, ~174 tris.
KIND = "UNIT_T1"
PIECE = "unit_t1"
coll = reset_piece(KIND)

add_cyl(coll, "FACTION", r=0.135, h=0.045, z0=0)                 # plinth
add_cyl(coll, "FACTION", r=0.105, h=0.155, z0=0.045, r_top=0.06)  # robe
add_cyl(coll, "FACTION", r=0.075, h=0.105, z0=0.195, r_top=0, y=0.005)  # hood, apex 0.30
add_sphere(coll, "STONE", r=0.05, z=0.215, seg=8, rings=4, y=-0.02)     # face
add_sphere(coll, "TRUNK", r=0.05, z=0.084, seg=6, rings=4, x=0.095, y=-0.02,
           scale=(1, 1, 0.78))                                    # sack
add_pips(coll, 1, ring_r=0.105, z0=0.045)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.16)
print("exported:", export_piece(PIECE, coll))
