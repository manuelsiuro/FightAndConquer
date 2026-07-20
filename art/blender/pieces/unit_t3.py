# UNIT_T3 — Baron: robe, flared mantle, back cape, gold circlet + finial. H 0.48, ~286 tris.
KIND = "UNIT_T3"
PIECE = "unit_t3"
coll = reset_piece(KIND)

add_cyl(coll, "FACTION", r=0.145, h=0.055, z0=0)                  # plinth
add_cyl(coll, "FACTION", r=0.105, h=0.20, z0=0.055, r_top=0.07)   # robe
add_cyl(coll, "FACTION", r=0.095, h=0.075, z0=0.245, r_top=0.05)  # mantle
add_box(coll, "FACTION", 0.15, 0.025, 0.20, z0=0.05, y=0.085,
        rot=(radians(12), 0, 0))                                  # cape (back)
add_sphere(coll, "STONE", r=0.052, z=0.345, seg=8, rings=4, y=-0.005)  # head
add_sphere(coll, "FACTION", r=0.052, z=0.415, seg=8, rings=3, scale=(1, 1, 0.7))  # hat dome
add_cyl(coll, "GOLD", r=0.056, h=0.02, z0=0.385)                  # circlet
add_box(coll, "GOLD", 0.045, 0.014, 0.045, z0=0.178, y=-0.088,
        rot=(0, radians(45), 0))                                  # chest emblem
add_cyl(coll, "GOLD", r=0.016, h=0.045, z0=0.435, seg=6, r_top=0)  # finial, tip 0.48
add_pips(coll, 3, ring_r=0.115, z0=0.055)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.24)
print("exported:", export_piece(PIECE, coll))
