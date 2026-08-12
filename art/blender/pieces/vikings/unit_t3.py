# VIKINGS UNIT_T3 — Jarl: FACTION cloak mantle, gold torc, horned helm
# (horn tips 0.48), sword at the hip. H 0.48, ~288 tris.
KIND = "VIKINGS_UNIT_T3"
PIECE = "unit_t3"
coll = reset_piece(KIND)

add_cyl(coll, "FACTION", r=0.145, h=0.055, z0=0)                  # plinth (matches Kingdom T3)
add_cyl(coll, "FACTION", r=0.105, h=0.20, z0=0.055, r_top=0.07)   # robe
add_cyl(coll, "FACTION", r=0.098, h=0.08, z0=0.24, r_top=0.055)   # cloak mantle over shoulders
add_cyl(coll, "GOLD", r=0.061, h=0.02, z0=0.318)                  # neck torc (short wide ring)
add_sphere(coll, "STONE", r=0.052, z=0.345, seg=8, rings=4, y=-0.005)  # head
add_cyl(coll, "STONE", r=0.05, h=0.055, z0=0.385)                 # helm bowl
add_cyl(coll, "STONE", r=0.016, h=0.10, z0=0, seg=6, r_top=0,     # horn (right), tip 0.48
        z_center=0.437, x=0.05, rot=(0, radians(30), 0))
add_cyl(coll, "STONE", r=0.016, h=0.10, z0=0, seg=6, r_top=0,     # horn (left), tip 0.48
        z_center=0.437, x=-0.05, rot=(0, radians(-30), 0))
add_box(coll, "STONE", 0.016, 0.016, 0.20, z0=0.07, x=0.11, y=-0.01)   # sword blade
add_box(coll, "GOLD", 0.05, 0.016, 0.015, z0=0.27, x=0.11, y=-0.01)    # crossguard
add_box(coll, "TRUNK", 0.02, 0.02, 0.045, z0=0.285, x=0.11, y=-0.01)   # grip
add_pips(coll, 3, ring_r=0.115, z0=0.055)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.24)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
