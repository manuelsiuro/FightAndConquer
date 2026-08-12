# SHOGUNATE MARKET — machiya stall row: three narrow gabled shops at staggered
# heights with faction roofs, noren curtain strips over the fronts, gold koban
# coin, paper lantern on a post. Front faces -Y. H ~0.25, ~192 tris.
KIND = "SHOGUNATE_MARKET"
PIECE = "market"
coll = reset_piece(KIND)

# Three narrow stalls side by side, roofs at slightly different heights.
for x, wall_h in ((-0.105, 0.10), (0.0, 0.13), (0.105, 0.11)):
    add_box(coll, "TRUNK", 0.10, 0.16, wall_h, z0=0, x=x, y=0.0)
    add_wedge(coll, "FACTION", 0.12, 0.18, 0.05, z0=wall_h, x=x, y=0.0)
    # Noren curtain strip hanging over the front.
    add_box(coll, "FACTION", 0.07, 0.008, 0.045, z0=wall_h - 0.05, x=x, y=-0.085)

# Gold koban coin disc laid out front.
add_cyl(coll, "GOLD", r=0.028, h=0.012, z0=0, seg=8, x=0.06, y=-0.15)

# Paper lantern on a post by the row's corner.
add_cyl(coll, "TRUNK", r=0.010, h=0.22, z0=0, seg=6, x=-0.20, y=-0.11)
add_sphere(coll, "STONE", r=0.030, z=0.185, seg=6, rings=4, x=-0.20, y=-0.11,
           scale=(1.0, 1.0, 1.25))

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.13)
print("exported:", export_piece(PIECE, coll, subdir="shogunate"))
