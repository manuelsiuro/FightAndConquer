# SHOGUNATE UNIT_T1 — Ashigaru: FACTION robe, STONE face peeking under a wide
# flat TRUNK straw hat (apex 0.30), bamboo staff at +X. H 0.30, ~198 tris.
KIND = "SHOGUNATE_UNIT_T1"
PIECE = "unit_t1"
coll = reset_piece(KIND)

add_cyl(coll, "FACTION", r=0.135, h=0.045, z0=0)                  # plinth (matches Kingdom T1)
add_cyl(coll, "FACTION", r=0.10, h=0.15, z0=0.045, r_top=0.06)    # robe (tinted = ownership)
add_sphere(coll, "STONE", r=0.048, z=0.21, seg=8, rings=4, y=-0.018)  # face, forward under the brim
add_cyl(coll, "TRUNK", r=0.115, h=0.055, z0=0.245, r_top=0)       # straw hat, wider than the head, apex 0.30
add_cyl(coll, "TRUNK", r=0.012, h=0.26, z0=0.02, seg=6, x=0.105)  # bamboo staff at +X (top 0.28)
add_cyl(coll, "TRUNK", r=0.016, h=0.012, z0=0.10, seg=6, x=0.105)  # bamboo node ring
add_cyl(coll, "TRUNK", r=0.016, h=0.012, z0=0.19, seg=6, x=0.105)  # bamboo node ring
add_pips(coll, 1, ring_r=0.105, z0=0.045)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.16)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
