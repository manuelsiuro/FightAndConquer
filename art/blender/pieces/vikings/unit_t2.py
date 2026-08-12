# VIKINGS UNIT_T2 — Raider: winged helm, FACTION tunic, shield facing out on
# the arm, raised hand axe (PIP head, tip 0.41). H 0.41, ~244 tris.
KIND = "VIKINGS_UNIT_T2"
PIECE = "unit_t2"
coll = reset_piece(KIND)

add_cyl(coll, "FACTION", r=0.14, h=0.05, z0=0)                    # plinth (matches Kingdom T2)
add_cyl(coll, "FACTION", r=0.095, h=0.16, z0=0.05, r_top=0.06)    # tunic
add_sphere(coll, "STONE", r=0.048, z=0.245, seg=8, rings=4, y=-0.005)  # head
add_sphere(coll, "STONE", r=0.052, z=0.266, seg=8, rings=4, scale=(1, 1, 0.62))  # helm dome (snug)
add_wedge(coll, "STONE", 0.04, 0.018, 0.045, z0=0.272, x=0.052)   # helm wing (right)
add_wedge(coll, "STONE", 0.04, 0.018, 0.045, z0=0.272, x=-0.052)  # helm wing (left)
add_cyl(coll, "FACTION", r=0.062, h=0.016, z0=0, seg=8,           # round shield held at the front
        rot=(radians(90), 0, 0), z_center=0.16, y=-0.085)
add_sphere(coll, "GOLD", r=0.014, z=0.16, seg=6, rings=2, y=-0.10)  # boss
add_cyl(coll, "TRUNK", r=0.014, h=0.33, z0=0.05, seg=6, x=0.105)  # axe haft, raised
add_box(coll, "PIP", 0.018, 0.075, 0.06, z0=0.35, x=0.105, y=-0.028)  # axe head, tip 0.41
add_pips(coll, 2, ring_r=0.11, z0=0.05)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.2)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
