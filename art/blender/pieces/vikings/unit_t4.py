# VIKINGS UNIT_T4 — Berserker: bear-pelt hood + fur mantle (TRUNK), broad
# flared torso, twin raised axes (tips 0.54), gold arm rings. H ~0.54, ~396 tris.
KIND = "VIKINGS_UNIT_T4"
PIECE = "unit_t4"
coll = reset_piece(KIND)

add_cyl(coll, "GOLD", r=0.19, h=0.025, z0=0)                      # gold base ring (T4 broad base)
add_cyl(coll, "FACTION", r=0.155, h=0.05, z0=0.025)               # plinth
add_cyl(coll, "FACTION", r=0.098, h=0.19, z0=0.075, r_top=0.108)  # torso, flares to broad shoulders
add_cyl(coll, "TRUNK", r=0.115, h=0.05, z0=0.25, r_top=0.075)     # fur mantle over shoulders
add_box(coll, "TRUNK", 0.11, 0.03, 0.12, z0=0.22, y=0.09,
        rot=(radians(10), 0, 0))                                  # pelt draping down the back
add_sphere(coll, "STONE", r=0.05, z=0.30, seg=8, rings=4, y=-0.008)  # face
add_sphere(coll, "TRUNK", r=0.062, z=0.318, seg=8, rings=4,
           scale=(1, 1, 0.88))                                    # bear-pelt hood
add_sphere(coll, "TRUNK", r=0.017, z=0.368, seg=6, rings=3, x=0.042)   # bear ear
add_sphere(coll, "TRUNK", r=0.017, z=0.368, seg=6, rings=3, x=-0.042)  # bear ear
for sx in (-1, 1):
    add_cyl(coll, "TRUNK", r=0.014, h=0.375, z0=0.085, seg=6, x=sx * 0.115)  # axe haft
    add_box(coll, "PIP", 0.018, 0.085, 0.09, z0=0.45, x=sx * 0.115, y=-0.02)  # axe head, tip 0.54
    add_cyl(coll, "GOLD", r=0.024, h=0.022, z0=0.21, seg=6, x=sx * 0.115)     # gold arm ring
add_pips(coll, 4, ring_r=0.115, z0=0.075)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.27)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
