# SHOGUNATE UNIT_T4 — Daimyo: three stacked FACTION armor skirts, STONE kabuto
# with dramatic GOLD crescent horns (tips exactly 0.54), FACTION war fan in hand
# at -Y. H 0.54, ~388 tris.
KIND = "SHOGUNATE_UNIT_T4"
PIECE = "unit_t4"
coll = reset_piece(KIND)

add_cyl(coll, "GOLD", r=0.19, h=0.025, z0=0)                      # gold base ring (T4 broad base)
add_cyl(coll, "FACTION", r=0.155, h=0.05, z0=0.025)               # plinth
add_cyl(coll, "FACTION", r=0.13, h=0.09, z0=0.075, r_top=0.10)    # armor skirt, bottom layer
add_cyl(coll, "FACTION", r=0.115, h=0.09, z0=0.165, r_top=0.085)  # armor skirt, middle layer
add_cyl(coll, "FACTION", r=0.10, h=0.095, z0=0.255, r_top=0.068)  # armor skirt, top layer (0.35)
add_sphere(coll, "STONE", r=0.05, z=0.38, seg=8, rings=4, y=-0.008)  # head
add_cyl(coll, "STONE", r=0.075, h=0.022, z0=0.395, r_top=0.06)    # kabuto neck-guard flare
add_cyl(coll, "STONE", r=0.06, h=0.05, z0=0.412, r_top=0.04)      # kabuto bowl, top 0.462

# Crescent horns: angled GOLD boxes rooted in the bowl; z0 solved so the highest
# rotated corner (zc + sz/2*cos(a) + sx/2*sin(a)) lands exactly at 0.54.
horn_sx, horn_sy, horn_sz, horn_a = 0.018, 0.04, 0.125, radians(20)
horn_z0 = 0.54 - horn_sz / 2 * math.cos(horn_a) - horn_sx / 2 * math.sin(horn_a) - horn_sz / 2
add_box(coll, "GOLD", horn_sx, horn_sy, horn_sz, z0=horn_z0, x=0.026, y=-0.028,
        rot=(0, horn_a, 0))                                       # horn (right), tip 0.54
add_box(coll, "GOLD", horn_sx, horn_sy, horn_sz, z0=horn_z0, x=-0.026, y=-0.028,
        rot=(0, -horn_a, 0))                                      # horn (left), tip 0.54

add_cyl(coll, "FACTION", r=0.012, h=0.06, z0=0.20, r_top=0.05,
        x=0.06, y=-0.095)                                         # war fan, flares open upward
add_box(coll, "TRUNK", 0.014, 0.014, 0.06, z0=0.145, x=0.06, y=-0.095)  # fan handle
add_pips(coll, 4, ring_r=0.115, z0=0.075)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.27)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
