# SHOGUNATE UNIT_T3 — Samurai: layered FACTION robe, STONE kabuto with GOLD V
# crest, sashimono back-banner (pole top exactly 0.48), katana at the hip.
# H 0.48, ~340 tris.
KIND = "SHOGUNATE_UNIT_T3"
PIECE = "unit_t3"
coll = reset_piece(KIND)

add_cyl(coll, "FACTION", r=0.145, h=0.055, z0=0)                  # plinth (matches Kingdom T3)
add_cyl(coll, "FACTION", r=0.105, h=0.15, z0=0.055, r_top=0.08)   # under-robe layer
add_cyl(coll, "FACTION", r=0.092, h=0.115, z0=0.205, r_top=0.055)  # over-robe layer
add_sphere(coll, "STONE", r=0.05, z=0.335, seg=8, rings=4, y=-0.005)  # head
add_cyl(coll, "STONE", r=0.072, h=0.022, z0=0.35, r_top=0.058)    # kabuto neck-guard flare
add_cyl(coll, "STONE", r=0.058, h=0.045, z0=0.37, r_top=0.038)    # kabuto bowl, top 0.415
add_box(coll, "GOLD", 0.012, 0.008, 0.05, z0=0.40, x=0.011, y=-0.052,
        rot=(0, radians(25), 0))                                  # crest V (right blade)
add_box(coll, "GOLD", 0.012, 0.008, 0.05, z0=0.40, x=-0.011, y=-0.052,
        rot=(0, radians(-25), 0))                                 # crest V (left blade)
add_box(coll, "TRUNK", 0.012, 0.012, 0.33, z0=0.15, y=0.088)      # sashimono pole (+Y back), top exactly 0.48
add_box(coll, "FACTION", 0.085, 0.008, 0.115, z0=0.35, y=0.088)   # sashimono flag, top 0.465
add_box(coll, "PIP", 0.014, 0.014, 0.17, z0=0.135, x=0.10,
        rot=(radians(35), 0, 0))                                  # katana blade at the hip, hilt-end forward
add_cyl(coll, "GOLD", r=0.02, h=0.012, seg=6, z0=0, z_center=0.285,
        x=0.10, y=-0.045, rot=(radians(35), 0, 0))                # tsuba (guard disc)
add_box(coll, "TRUNK", 0.018, 0.018, 0.05, z0=0.285, x=0.10, y=-0.062,
        rot=(radians(35), 0, 0))                                  # grip
add_pips(coll, 3, ring_r=0.115, z0=0.055)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.24)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
