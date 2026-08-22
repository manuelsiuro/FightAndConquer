# VIKINGS FORTRESS — trelleborg ring-fort: circular earthen rampart with a
# palisade crown, stave gatehouse with a gold dragon prow, faction shields on
# the slope, tall mead-hall keep inside. H ~0.55, ~430 tris.
KIND = "VIKINGS_FORTRESS"
PIECE = "fortress"
coll = reset_piece(KIND)

# Earthen rampart ring.
add_cyl(coll, "STONE", r=0.235, h=0.09, z0=0, seg=12, r_top=0.20)

# Palisade crown (gap at the front gate).
for deg in (30, 60, 90, 120, 150, 210, 330):
    a = radians(deg)
    add_cyl(coll, "TRUNK", r=0.017, h=0.10, z0=0.09, seg=6,
            x=0.19 * math.cos(a), y=0.19 * math.sin(a))

# Stave gatehouse (front, -Y) with an ink gate and a curling gold dragon prow.
add_box(coll, "TRUNK", 0.11, 0.06, 0.15, z0=0.04, y=-0.195)
add_wedge(coll, "TRUNK", 0.13, 0.08, 0.06, z0=0.19, y=-0.195)
add_box(coll, "PIP", 0.05, 0.02, 0.09, z0=0.04, y=-0.232)
add_cyl(coll, "GOLD", r=0.014, h=0.06, z0=0, seg=6, r_top=0.006, y=-0.225,
        rot=(radians(-35), 0, 0), z_center=0.275)

# Faction round shields leaned on the rampart slope.
for deg in (200, 250, 300):
    a = radians(deg)
    add_cyl(coll, "FACTION", r=0.045, h=0.012, z0=0, seg=10,
            x=0.21 * math.cos(a), y=0.21 * math.sin(a),
            rot=(radians(65), 0, a + radians(90)), z_center=0.06)

# Mead-hall keep: tall timber hall + steep roof + ridge crest + gold finial.
add_box(coll, "TRUNK", 0.16, 0.20, 0.24, z0=0.09)
add_wedge(coll, "FACTION", 0.20, 0.24, 0.16, z0=0.33)
add_box(coll, "TRUNK", 0.02, 0.26, 0.02, z0=0.49)
add_sphere(coll, "GOLD", r=0.018, z=0.525, seg=8, rings=3)

join_roles(coll, PIECE)
stage_for_render(KIND, z_focus=0.27)
print("exported:", export_piece(PIECE, coll, subdir="vikings"))
