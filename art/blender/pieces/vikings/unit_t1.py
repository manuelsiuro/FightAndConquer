# VIKINGS UNIT_T1 — Thrall: bare head over a FACTION work tunic with a TRUNK
# belt, round shield held at the side, firewood log at the foot. H 0.30, ~180 tris.
KIND = "VIKINGS_UNIT_T1"
PIECE = "unit_t1"
coll = reset_piece(KIND)

add_cyl(coll, "FACTION", r=0.135, h=0.045, z0=0)                  # plinth (matches Kingdom T1)
add_cyl(coll, "FACTION", r=0.10, h=0.15, z0=0.045, r_top=0.062)   # work tunic (tinted = ownership)
add_cyl(coll, "TRUNK", r=0.088, h=0.028, z0=0.105)                # rope belt
add_sphere(coll, "STONE", r=0.058, z=0.242, seg=8, rings=4)       # bare head, apex 0.30
add_cyl(coll, "FACTION", r=0.06, h=0.014, z0=0, seg=8,            # round shield held at +X side
        rot=(0, radians(90), 0), z_center=0.14, x=0.105, y=-0.02)
add_sphere(coll, "GOLD", r=0.013, z=0.14, seg=6, rings=2, x=0.121, y=-0.02)  # shield boss
add_cyl(coll, "TRUNK", r=0.02, h=0.11, z0=0, seg=6,               # firewood log at the front
        rot=(0, radians(90), 0), z_center=0.02, x=0.0, y=-0.095)
add_pips(coll, 1, ring_r=0.105, z0=0.045)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.16)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
