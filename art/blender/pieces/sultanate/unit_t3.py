# SULTANATE UNIT_T3 — Emir: layered FACTION robe (two frustums), tall STONE wrap
# turban (two squashed spheres, top 0.48) with GOLD crescent pin, curved scimitar
# at the hip (chained PIP boxes). H 0.48, ~328 tris.
KIND = "SULTANATE_UNIT_T3"
PIECE = "unit_t3"
coll = reset_piece(KIND)

add_cyl(coll, "FACTION", r=0.145, h=0.055, z0=0)                  # plinth (matches Kingdom T3)
add_cyl(coll, "FACTION", r=0.105, h=0.20, z0=0.055, r_top=0.07)   # lower robe
add_cyl(coll, "FACTION", r=0.095, h=0.075, z0=0.245, r_top=0.05)  # layered over-robe
add_sphere(coll, "STONE", r=0.052, z=0.345, seg=8, rings=4, y=-0.005)  # head
add_sphere(coll, "STONE", r=0.065, z=0.395, seg=8, rings=3,
           scale=(1, 1, 0.6))                                     # turban wrap, lower tier
add_sphere(coll, "STONE", r=0.05, z=0.45, seg=8, rings=3,
           scale=(1, 1, 0.6))                                     # turban upper tier, apex 0.48
add_box(coll, "GOLD", 0.008, 0.008, 0.028, z0=0.40, x=0.012, y=-0.06,
        rot=(0, radians(35), 0))                                  # crescent pin arm (right)
add_box(coll, "GOLD", 0.008, 0.008, 0.028, z0=0.40, x=-0.012, y=-0.06,
        rot=(0, radians(-35), 0))                                 # crescent pin arm (left)
add_box(coll, "TRUNK", 0.02, 0.02, 0.045, z0=0.245, x=0.112, y=-0.01)  # scimitar grip
add_sphere(coll, "GOLD", r=0.012, z=0.295, seg=6, rings=2, x=0.112, y=-0.01)  # pommel
add_box(coll, "PIP", 0.016, 0.016, 0.115, z0=0.135, x=0.112, y=-0.01,
        rot=(radians(8), 0, 0))                                   # blade, upper section
add_box(coll, "PIP", 0.016, 0.016, 0.095, z0=0.055, x=0.112, y=0.02,
        rot=(radians(30), 0, 0))                                  # blade tip, sweeps back
add_pips(coll, 3, ring_r=0.115, z0=0.055)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.24)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
