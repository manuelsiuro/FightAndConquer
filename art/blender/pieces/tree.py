# TREE — juniper, three staggered cones on a tapered trunk. H 0.52, ~66 tris.
KIND = "TREE"
PIECE = "tree"
coll = reset_piece(KIND)

add_cyl(coll, "TRUNK", r=0.05, h=0.16, z0=0, seg=7)
add_cyl(coll, "TREE_FOLIAGE", r=0.20, h=0.22, z0=0.12, r_top=0)
add_cyl(coll, "TREE_FOLIAGE", r=0.15, h=0.20, z0=0.26, r_top=0, rot=(0, 0, radians(22.5)))
add_cyl(coll, "TREE_FOLIAGE", r=0.10, h=0.16, z0=0.36, r_top=0)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.24)
path = export_piece(PIECE, coll)
print("exported:", path)
for obj in coll.objects:
    print(obj.name, "polys:", len(obj.data.polygons))
