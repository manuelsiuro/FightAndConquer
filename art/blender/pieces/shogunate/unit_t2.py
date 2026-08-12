# SHOGUNATE UNIT_T2 — Yari samurai: FACTION robe, PIP lacquer shoulder plates,
# simple STONE helmet bowl, vertical yari at +X (TRUNK shaft, slender PIP blade,
# tip 0.41). H 0.41, ~228 tris.
KIND = "SHOGUNATE_UNIT_T2"
PIECE = "unit_t2"
coll = reset_piece(KIND)

add_cyl(coll, "FACTION", r=0.14, h=0.05, z0=0)                    # plinth (matches Kingdom T2)
add_cyl(coll, "FACTION", r=0.095, h=0.16, z0=0.05, r_top=0.06)    # robe
add_box(coll, "PIP", 0.05, 0.05, 0.032, z0=0.19, x=0.062,
        rot=(0, radians(15), 0))                                  # lacquer shoulder plate (right)
add_box(coll, "PIP", 0.05, 0.05, 0.032, z0=0.19, x=-0.062,
        rot=(0, radians(-15), 0))                                 # lacquer shoulder plate (left)
add_sphere(coll, "STONE", r=0.048, z=0.245, seg=8, rings=4, y=-0.005)  # head
add_cyl(coll, "STONE", r=0.056, h=0.045, z0=0.262, r_top=0.036)   # helmet bowl, top 0.307
add_cyl(coll, "TRUNK", r=0.013, h=0.30, z0=0.05, seg=6, x=0.105)  # yari shaft
add_box(coll, "PIP", 0.016, 0.016, 0.06, z0=0.35, x=0.105)        # slender yari blade, tip 0.41
add_pips(coll, 2, ring_r=0.11, z0=0.05)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.2)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
