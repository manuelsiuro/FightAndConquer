# SULTANATE UNIT_T1 — Fellah: FACTION robe, STONE face under a white-ish STONE
# wrap turban with a small GOLD pin, water jar (TRUNK) at the foot. H 0.30, ~208 tris.
KIND = "SULTANATE_UNIT_T1"
PIECE = "unit_t1"
coll = reset_piece(KIND)

add_cyl(coll, "FACTION", r=0.135, h=0.045, z0=0)                  # plinth (matches Kingdom T1)
add_cyl(coll, "FACTION", r=0.105, h=0.155, z0=0.045, r_top=0.06)  # robe (tinted = ownership)
add_sphere(coll, "STONE", r=0.05, z=0.22, seg=8, rings=4, y=-0.01)  # face
add_sphere(coll, "STONE", r=0.068, z=0.266, seg=8, rings=3,
           scale=(1, 1, 0.5))                                     # turban wrap, apex 0.30
add_sphere(coll, "GOLD", r=0.012, z=0.266, seg=6, rings=2, y=-0.06)  # turban pin (front)
add_cyl(coll, "TRUNK", r=0.032, h=0.06, z0=0.045, seg=6, r_top=0.02,
        x=0.095, y=-0.045)                                        # water jar body at the foot
add_cyl(coll, "TRUNK", r=0.015, h=0.022, z0=0.105, seg=6, x=0.095, y=-0.045)  # jar neck
add_pips(coll, 1, ring_r=0.105, z0=0.045)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.16)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
