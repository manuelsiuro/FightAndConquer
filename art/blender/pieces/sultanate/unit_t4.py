# SULTANATE UNIT_T4 — Royal Guard: broad FACTION cloak, GOLD dome helm + spike,
# crescent standard behind the +X shoulder (tips 0.54), twin raised scimitars.
# H 0.54, ~434 tris.
KIND = "SULTANATE_UNIT_T4"
PIECE = "unit_t4"
coll = reset_piece(KIND)

add_cyl(coll, "GOLD", r=0.19, h=0.025, z0=0)                      # gold base ring (T4 broad base)
add_cyl(coll, "FACTION", r=0.155, h=0.05, z0=0.025)               # plinth
add_cyl(coll, "FACTION", r=0.115, h=0.20, z0=0.075, r_top=0.075)  # broad A-line cloak
add_box(coll, "FACTION", 0.16, 0.028, 0.18, z0=0.08, y=0.095,
        rot=(radians(10), 0, 0))                                  # cape draping down the back
add_sphere(coll, "STONE", r=0.05, z=0.30, seg=8, rings=4, y=-0.008)  # face
add_sphere(coll, "GOLD", r=0.06, z=0.325, seg=8, rings=3,
           scale=(1, 1, 0.7))                                     # gold dome helm
add_cyl(coll, "GOLD", r=0.013, h=0.045, z0=0.36, seg=6, r_top=0)  # helm spike, tip 0.405
add_cyl(coll, "TRUNK", r=0.012, h=0.40, z0=0.075, seg=6,          # standard pole behind +X shoulder
        x=0.115, y=0.065)
add_sphere(coll, "GOLD", r=0.016, z=0.47, seg=6, rings=2, x=0.115, y=0.065)  # crescent hub
add_box(coll, "GOLD", 0.018, 0.012, 0.085, z0=0.4562, x=0.135, y=0.065,
        rot=(0, radians(30), 0))                                  # crescent arm, tip 0.54
add_box(coll, "GOLD", 0.018, 0.012, 0.085, z0=0.4562, x=0.095, y=0.065,
        rot=(0, radians(-30), 0))                                 # crescent arm, tip 0.54
for sx in (-1, 1):
    add_cyl(coll, "TRUNK", r=0.013, h=0.10, z0=0.12, seg=6, x=sx * 0.118)   # scimitar grip
    add_box(coll, "GOLD", 0.036, 0.016, 0.012, z0=0.218, x=sx * 0.118)      # crossguard
    add_box(coll, "PIP", 0.014, 0.02, 0.13, z0=0.232, x=sx * 0.118,
            rot=(0, sx * radians(8), 0))                                    # blade, lower section
    add_box(coll, "PIP", 0.014, 0.02, 0.10, z0=0.35, x=sx * 0.135,
            rot=(0, sx * radians(30), 0))                                   # blade tip, curves out
add_pips(coll, 4, ring_r=0.115, z0=0.075)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.27)
print("exported:", export_piece(PIECE, coll, subdir="sultanate"))
