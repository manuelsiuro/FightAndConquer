#!/usr/bin/env python3
"""Bake the readable campaign sources into the JSON assets the game ships.

    python3 tools/build_campaigns.py            # rebuild every campaign
    python3 tools/build_campaigns.py academy    # rebuild one

Sources live in ``tools/campaign_src/<id>.py`` and export a ``CAMPAIGN`` dict.
Output goes to ``app/src/main/assets/campaigns/<id>.json`` in the exact shape
``core/campaign/CampaignCodec`` decodes.

Why a build step at all: a level is mostly a *map*, and a map written as raw
JSON tile records is unreadable and unreviewable. Here a map is ASCII art and
every interesting hex has a *name*, so objectives, hints and story beats refer
to "@ridge" instead of a packed integer nobody can check by eye.

Map grammar
-----------
Whitespace-separated tokens, one text line per hex row. Row ``r``, column ``c``
maps to axial ``q = c - r // 2``, which makes the printed grid line up with the
board when odd rows are indented by one half-space.

    -       off-map void (no tile at all)
    ~       open sea                       ~*  sea with a fish shoal
    B0      bridge on sea, owned by seat 0
    .       neutral land
    .t      tree            .g  gravestone
    .$      gold vein       .%  fertile ground
    0       land owned by seat 0 (any digit is a seat)
    0C      capital     0T tower    0K castle     0F farm    0M mine
    0R      market      0L lumber camp           0W watchtower
    0P      port        0Y fishery
    0$ / 0% owned land carrying a deposit

Any token may carry a ``:name`` suffix to declare an anchor:

    .$:north_vein   0C:home   ~:landing_beach

Anywhere in the level dict, the string ``"@north_vein"`` is replaced by that
hex. Capitals additionally get automatic anchors ``@capital0``, ``@capital1``…

Rules
-----
A level writes only the ``RuleConstants`` it *overrides*; everything else takes
the engine default. Shipped levels are therefore small and diffable, and the
guard against a rules-default change quietly unbalancing a mission is the
campaign test suite (``:core:test``), not a frozen copy of every constant.
"""

from __future__ import annotations

import importlib.util
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC_DIR = ROOT / "tools" / "campaign_src"
OUT_DIR = ROOT / "app" / "src" / "main" / "assets" / "campaigns"

# --- map tokens -------------------------------------------------------------

BUILDINGS = {
    "C": "CAPITAL",
    "T": "TOWER",
    "K": "STRONG_TOWER",
    "F": "FARM",
    "M": "MINE",
    "R": "MARKET",
    "L": "LUMBER_CAMP",
    "W": "WATCHTOWER",
    "P": "PORT",
    "Y": "FISHERY",
}
DEPOSITS = {"$": "GOLD_VEIN", "%": "FERTILE", "*": "FISH_SHOAL"}
FLORA = {"t": "tree", "g": "grave"}


class BuildError(Exception):
    pass


def pack(q: int, r: int) -> int:
    """Axial (q, r) -> the single signed Int `Hex` serializes as."""
    if not (-32768 <= q <= 32767 and -32768 <= r <= 32767):
        raise BuildError(f"coordinate out of range: ({q}, {r})")
    packed = ((q & 0xFFFF) << 16) | (r & 0xFFFF)
    return packed - (1 << 32) if packed >= (1 << 31) else packed


def parse_map(art: str, level_id: str) -> tuple[list[dict], list[int], dict[str, int]]:
    """ASCII art -> (tiles, capitals, anchors). Capitals are ordered by seat."""
    tiles: list[dict] = []
    anchors: dict[str, int] = {}
    capitals: dict[int, int] = {}
    seen: set[int] = set()

    for r, line in enumerate(art.strip("\n").splitlines()):
        for c, token in enumerate(line.split()):
            if token == "-":
                continue
            body, _, name = token.partition(":")
            hex_id = pack(c - r // 2, r)
            if hex_id in seen:
                raise BuildError(f"{level_id}: two tokens for row {r} col {c}")
            seen.add(hex_id)
            tile = read_token(body, hex_id, level_id)
            tiles.append(tile)
            if tile.get("building") == "CAPITAL":
                seat = tile["owner"]
                if seat in capitals:
                    raise BuildError(f"{level_id}: seat {seat} has two capitals")
                capitals[seat] = hex_id
            if name:
                if name in anchors:
                    raise BuildError(f"{level_id}: duplicate anchor '{name}'")
                anchors[name] = hex_id

    if not capitals:
        raise BuildError(f"{level_id}: the map has no capital")
    if sorted(capitals) != list(range(len(capitals))):
        raise BuildError(f"{level_id}: capitals must cover seats 0..n, got {sorted(capitals)}")
    for seat, hex_id in capitals.items():
        anchors.setdefault(f"capital{seat}", hex_id)
    return tiles, [capitals[seat] for seat in sorted(capitals)], anchors


def read_token(body: str, hex_id: int, level_id: str) -> dict:
    tile: dict = {"hex": hex_id}
    head, rest = body[0], body[1:]

    if head == "~":
        tile["terrain"] = "SEA"
        if rest == "*":
            tile["deposit"] = "FISH_SHOAL"
        elif rest:
            raise BuildError(f"{level_id}: sea tile cannot carry '{rest}'")
        return tile
    if head == "B":
        # The one building that stands on water, and owns it.
        if not rest.isdigit():
            raise BuildError(f"{level_id}: bridge token needs a seat, got '{body}'")
        tile.update(terrain="SEA", owner=int(rest), building="BRIDGE")
        return tile

    if head == ".":
        pass
    elif head.isdigit():
        tile["owner"] = int(head)
    else:
        raise BuildError(f"{level_id}: unknown token '{body}'")

    if not rest:
        return tile
    if len(rest) != 1:
        raise BuildError(f"{level_id}: token '{body}' has more than one feature")
    if rest in BUILDINGS:
        if "owner" not in tile:
            raise BuildError(f"{level_id}: '{body}' — a building needs an owner")
        tile["building"] = BUILDINGS[rest]
    elif rest in DEPOSITS:
        if DEPOSITS[rest] == "FISH_SHOAL":
            raise BuildError(f"{level_id}: fish shoals belong on sea, not '{body}'")
        tile["deposit"] = DEPOSITS[rest]
    elif rest in FLORA:
        tile["flora"] = (
            {"type": "tree"} if FLORA[rest] == "tree" else {"type": "grave", "createdRound": 0}
        )
    else:
        raise BuildError(f"{level_id}: unknown feature '{rest}' in '{body}'")
    return tile


# --- level assembly ---------------------------------------------------------


def resolve(node, anchors: dict[str, int], level_id: str):
    """Recursively replace every "@anchor" string with the hex it names."""
    if isinstance(node, str) and node.startswith("@"):
        name = node[1:]
        if name not in anchors:
            raise BuildError(f"{level_id}: unknown anchor '@{name}'")
        return anchors[name]
    if isinstance(node, list):
        return [resolve(item, anchors, level_id) for item in node]
    if isinstance(node, dict):
        return {key: resolve(value, anchors, level_id) for key, value in node.items()}
    return node


def build_seat(seat) -> dict:
    if seat == "player":
        return {"type": "player"}
    if isinstance(seat, (tuple, list)) and len(seat) == 2 and seat[0] == "ai":
        return {"type": "ai", "difficulty": seat[1]}
    raise BuildError(f"unknown seat spec: {seat!r}")


def build_level(src: dict) -> dict:
    level_id = src["id"]
    tiles, capitals, anchors = parse_map(src["map"], level_id)

    level = {
        "id": level_id,
        "seed": src["seed"],
        "map": {
            "name": level_id,
            "generatorParams": None,
            "tiles": tiles,
            "capitals": capitals,
        },
        "seats": [build_seat(seat) for seat in src["seats"]],
    }
    if len(level["seats"]) != len(capitals):
        raise BuildError(
            f"{level_id}: {len(level['seats'])} seats but {len(capitals)} capitals"
        )
    if sum(1 for s in level["seats"] if s["type"] == "player") != 1:
        raise BuildError(f"{level_id}: exactly one seat must be the player")

    if src.get("rules"):
        level["rules"] = src["rules"]
    if src.get("treasury"):
        level["startingTreasury"] = src["treasury"]
    if src.get("civs"):
        civs = src["civs"]
        valid = {"KINGDOM", "VIKINGS", "SULTANATE", "SHOGUNATE"}
        if len(civs) != len(level["seats"]):
            raise BuildError(
                f"{level_id}: {len(civs)} civs but {len(level['seats'])} seats"
            )
        unknown = [c for c in civs if c not in valid]
        if unknown:
            raise BuildError(f"{level_id}: unknown civ(s) {unknown}")
        level["civs"] = civs
    for key, field in (
        ("units", "startingUnits"),
        ("objectives", "objectives"),
        ("failures", "failures"),
        ("hints", "hints"),
        ("scripts", "scripts"),
    ):
        if src.get(key):
            level[field] = src[key]
    if src.get("par") is not None:
        level["parRounds"] = src["par"]
    if "aiSolvable" in src:
        level["aiSolvable"] = src["aiSolvable"]

    return resolve(level, anchors, level_id)


def build_campaign(src: dict) -> dict:
    return {
        "version": 1,
        "id": src["id"],
        "order": src.get("order", 0),
        "levels": [build_level(level) for level in src["levels"]],
    }


def load_source(path: Path) -> dict:
    spec = importlib.util.spec_from_file_location(path.stem, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.CAMPAIGN


def main(argv: list[str]) -> int:
    wanted = argv[1:]
    sources = sorted(SRC_DIR.glob("*.py"))
    if wanted:
        sources = [p for p in sources if p.stem in wanted]
        missing = set(wanted) - {p.stem for p in sources}
        if missing:
            print(f"no such campaign source: {', '.join(sorted(missing))}", file=sys.stderr)
            return 2
    if not sources:
        print(f"no campaign sources under {SRC_DIR}", file=sys.stderr)
        return 2

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for path in sources:
        try:
            campaign = build_campaign(load_source(path))
        except BuildError as error:
            print(f"{path.name}: {error}", file=sys.stderr)
            return 1
        out = OUT_DIR / f"{campaign['id']}.json"
        out.write_text(json.dumps(campaign, separators=(",", ":")) + "\n")
        levels = len(campaign["levels"])
        print(f"{path.name} -> {out.relative_to(ROOT)}  ({levels} levels, {out.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
