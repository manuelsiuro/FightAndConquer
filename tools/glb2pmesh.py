#!/usr/bin/env python3
"""Convert Blender-exported .glb piece models into the game's .pmesh format.

The game bakes glTF geometry into its existing procedural-mesh pipeline instead of
shipping a glTF runtime. Mesh OBJECT names carry the color role via the suffix
`__FACTION|__GOLD|__TREE_FOLIAGE|__TRUNK|__STONE|__PIP`; all objects sharing a role
are merged into one part.

.pmesh layout (little-endian):
  magic  "PMSH"                     4 bytes
  version u8 = 1
  partCount u8
  per part:
    role u8       (0 FACTION, 1 GOLD, 2 TREE_FOLIAGE, 3 TRUNK, 4 STONE, 5 PIP)
    triCount u16
    triCount * 9 * float32          (ax ay az bx by bz cx cy cz per triangle)

Usage: glb2pmesh.py <in.glb> <out.pmesh>
       glb2pmesh.py --all <models_dir> <assets_dir>
"""

import json
import struct
import sys
from pathlib import Path

ROLES = ["FACTION", "GOLD", "TREE_FOLIAGE", "TRUNK", "STONE", "PIP"]
MAX_TRIS = 600
MAX_RADIUS = 0.45
MAX_HEIGHT = 0.75
MIN_Y = -0.01


def parse_glb(path):
    data = Path(path).read_bytes()
    magic, version, _length = struct.unpack_from("<4sII", data, 0)
    if magic != b"glTF" or version != 2:
        raise ValueError(f"{path}: not a glTF 2.0 binary")
    offset = 12
    gltf = None
    binary = b""
    while offset < len(data):
        chunk_len, chunk_type = struct.unpack_from("<II", data, offset)
        chunk = data[offset + 8 : offset + 8 + chunk_len]
        if chunk_type == 0x4E4F534A:
            gltf = json.loads(chunk)
        elif chunk_type == 0x004E4942:
            binary = chunk
        offset += 8 + chunk_len + (-chunk_len % 4)
    if gltf is None:
        raise ValueError(f"{path}: missing JSON chunk")
    return gltf, binary


def read_accessor(gltf, binary, index):
    accessor = gltf["accessors"][index]
    view = gltf["bufferViews"][accessor["bufferView"]]
    base = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
    count = accessor["count"]
    ctype = accessor["componentType"]
    atype = accessor["type"]
    ncomp = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}[atype]
    fmt, size = {
        5120: ("b", 1), 5121: ("B", 1), 5122: ("h", 2),
        5123: ("H", 2), 5125: ("I", 4), 5126: ("f", 4),
    }[ctype]
    stride = view.get("byteStride") or ncomp * size
    out = []
    for i in range(count):
        pos = base + i * stride
        out.append(struct.unpack_from("<" + fmt * ncomp, binary, pos))
    return out


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def node_matrix(node):
    if "matrix" in node:
        m = node["matrix"]  # column-major
        return [[m[c * 4 + r] for c in range(4)] for r in range(4)]
    t = node.get("translation", [0, 0, 0])
    q = node.get("rotation", [0, 0, 0, 1])
    s = node.get("scale", [1, 1, 1])
    x, y, z, w = q
    rot = [
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ]
    m = [[rot[r][c] * s[c] for c in range(3)] + [t[r]] for r in range(3)]
    m.append([0.0, 0.0, 0.0, 1.0])
    return m


def transform(matrix, p):
    x, y, z = p
    return tuple(
        matrix[r][0] * x + matrix[r][1] * y + matrix[r][2] * z + matrix[r][3]
        for r in range(3)
    )


def role_of(name):
    if "__" not in name:
        return None
    suffix = name.rsplit("__", 1)[1].split(".")[0]
    return suffix if suffix in ROLES else None


def collect_triangles(gltf, binary):
    """Walk the scene graph; returns {role: [tri, ...]} with world-space vertices."""
    by_role = {}
    identity = [[1.0 if r == c else 0.0 for c in range(4)] for r in range(4)]

    def visit(node_index, parent):
        node = gltf["nodes"][node_index]
        world = mat_mul(parent, node_matrix(node))
        role = role_of(node.get("name", ""))
        if "mesh" in node and role is not None:
            mesh = gltf["meshes"][node["mesh"]]
            for prim in mesh.get("primitives", []):
                if prim.get("mode", 4) != 4:
                    continue
                positions = read_accessor(gltf, binary, prim["attributes"]["POSITION"])
                if "indices" in prim:
                    idx = [i[0] for i in read_accessor(gltf, binary, prim["indices"])]
                else:
                    idx = list(range(len(positions)))
                for k in range(0, len(idx), 3):
                    tri = tuple(transform(world, positions[idx[k + j]]) for j in range(3))
                    by_role.setdefault(role, []).append(tri)
        for child in node.get("children", []):
            visit(child, world)

    scene = gltf["scenes"][gltf.get("scene", 0)]
    for root in scene.get("nodes", []):
        visit(root, identity)
    return by_role


def validate(name, by_role):
    total = sum(len(t) for t in by_role.values())
    if total == 0:
        raise ValueError(f"{name}: no role-named triangles found")
    if total > MAX_TRIS:
        raise ValueError(f"{name}: {total} tris exceeds budget {MAX_TRIS}")
    xs, ys, zs = [], [], []
    for tris in by_role.values():
        for tri in tris:
            for x, y, z in tri:
                xs.append(x); ys.append(y); zs.append(z)
    radius = max(max(abs(v) for v in xs), max(abs(v) for v in zs))
    if radius > MAX_RADIUS:
        raise ValueError(f"{name}: radius {radius:.3f} exceeds {MAX_RADIUS}")
    if max(ys) > MAX_HEIGHT:
        raise ValueError(f"{name}: height {max(ys):.3f} exceeds {MAX_HEIGHT}")
    if min(ys) < MIN_Y:
        raise ValueError(f"{name}: geometry {min(ys):.3f} below tile top")
    return total, radius, max(ys)


def write_pmesh(by_role, out_path):
    Path(out_path).parent.mkdir(parents=True, exist_ok=True)
    parts = sorted(by_role.items(), key=lambda kv: ROLES.index(kv[0]))
    blob = bytearray()
    blob += b"PMSH"
    blob += struct.pack("<BB", 1, len(parts))
    for role, tris in parts:
        blob += struct.pack("<BH", ROLES.index(role), len(tris))
        for tri in tris:
            for vert in tri:
                blob += struct.pack("<fff", *vert)
    Path(out_path).write_bytes(bytes(blob))
    return len(blob)


def convert(src, dst):
    gltf, binary = parse_glb(src)
    by_role = collect_triangles(gltf, binary)
    name = Path(src).stem
    total, radius, height = validate(name, by_role)
    size = write_pmesh(by_role, dst)
    roles = ", ".join(f"{r}:{len(t)}" for r, t in sorted(by_role.items()))
    print(f"{name}: {total} tris ({roles}) r={radius:.2f} h={height:.2f} -> {size} bytes")


def main():
    args = sys.argv[1:]
    if len(args) == 3 and args[0] == "--all":
        models = sorted(Path(args[1]).glob("*.glb"))
        if not models:
            print(f"no .glb files in {args[1]}", file=sys.stderr)
            return 1
        Path(args[2]).mkdir(parents=True, exist_ok=True)
        for glb in models:
            convert(glb, Path(args[2]) / (glb.stem + ".pmesh"))
        return 0
    if len(args) == 2:
        convert(args[0], args[1])
        return 0
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main())
