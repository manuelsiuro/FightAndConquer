# UNIT_T4 — Knight: great helm + plume, heraldic shield, sword, gold crown + base. H 0.55, ~362 tris.
KIND = "UNIT_T4"
PIECE = "unit_t4"
coll = reset_piece(KIND)

add_cyl(coll, "GOLD", r=0.16, h=0.025, z0=0)                      # gold base ring
add_cyl(coll, "FACTION", r=0.145, h=0.05, z0=0.025)               # plinth
add_cyl(coll, "FACTION", r=0.10, h=0.20, z0=0.075, r_top=0.078)   # torso
add_cyl(coll, "STONE", r=0.055, h=0.12, z0=0.285)                 # great helm
add_sphere(coll, "STONE", r=0.04, z=0.275, seg=6, rings=3, x=0.09)   # pauldrons
add_sphere(coll, "STONE", r=0.04, z=0.275, seg=6, rings=3, x=-0.09)
add_box(coll, "PIP", 0.055, 0.012, 0.014, z0=0.345, y=-0.052)     # visor slit
add_cyl(coll, "FACTION", r=0.028, h=0.17, seg=6, r_top=0, z0=0,   # plume (sweeps back)
        z_center=0.468, y=0.045, rot=(radians(-32), 0, 0))
add_cyl(coll, "GOLD", r=0.05, h=0.018, z0=0.395)                  # crown ring
for deg in (0, 90, 180, 270):
    a = radians(deg)
    add_cyl(coll, "GOLD", r=0.012, h=0.03, z0=0.413, seg=4, r_top=0,
            x=0.038 * math.cos(a), y=0.038 * math.sin(a))         # crown spikes
add_box(coll, "FACTION", 0.11, 0.022, 0.15, z0=0.13, x=-0.095, y=-0.05,
        rot=(0, radians(45), 0))                                  # heraldic shield
add_box(coll, "STONE", 0.016, 0.016, 0.16, z0=0.06, x=0.105, y=-0.01)  # sword blade
add_box(coll, "GOLD", 0.05, 0.016, 0.016, z0=0.215, x=0.105, y=-0.01)  # crossguard
add_box(coll, "TRUNK", 0.02, 0.02, 0.05, z0=0.231, x=0.105, y=-0.01)   # grip
add_pips(coll, 4, ring_r=0.115, z0=0.075)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.27)
print("exported:", export_piece(PIECE, coll))
