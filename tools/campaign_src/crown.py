"""The Iron Crown — a land war for a contested succession.

Six chapters on one continent. Where the Isles are about logistics, the Crown is
about ground: what it earns, what it costs to hold, and what happens when you
run out of it. Boats are switched off throughout — the coast is a wall, not a
road — so every chapter is decided by economy and position.

    python3 tools/build_campaigns.py crown
"""

ALL_BUILDINGS = [
    "FARM", "TOWER", "STRONG_TOWER", "MINE", "MARKET",
    "LUMBER_CAMP", "WATCHTOWER", "PORT", "FISHERY", "BRIDGE",
]


def buildings(*allowed):
    return [b for b in ALL_BUILDINGS if b not in allowed]


# Landlocked by rule, not by geography: the continent has a shore, but with
# navalEnabled off it is simply the edge of the world.
LANDLOCKED = dict(navalEnabled=False)


# --- 1. The Granary ---------------------------------------------------------
# Before the war, the harvest. Fertile ground is the whole map's argument: farms
# on it earn double, and the mission is a number, not a rival.

GRANARY = dict(
    id="crown_granary",
    seed=301,
    map="""
~  ~  ~   ~  ~  ~   ~  ~   ~  ~   ~  ~  ~
  ~  ~  .   .  .  .   .  .   .  .   ~  ~  ~
~  .  0   0  .  .   .%:loam_a .  .   1  1  ~  ~
  ~  0  0C  0  .  .   .  .%:loam_b 1  1C  1  ~  ~
~  0  0   .  .  .   .  .   .  1   1  ~  ~
  ~  .  .   .  .  .%:loam_c .  .   .  .   .  ~  ~
~  ~  .   .  .  .   .  .   .  .   ~  ~  ~
  ~  ~  ~   .  .  .   .  .   .  ~   ~  ~  ~
~  ~  ~   ~  ~  ~   ~  ~   ~  ~   ~  ~  ~
""",
    seats=["player", ("ai", "EASY")],
    rules=dict(
        specialUnitsEnabled=False,
        diplomacyEnabled=False,
        disabledBuildings=buildings("FARM", "TOWER", "MARKET", "LUMBER_CAMP"),
        **LANDLOCKED,
    ),
    treasury=[30, 25],
    objectives=[
        {"type": "income", "coins": 32},
        {"type": "build", "building": "FARM", "count": 3},
    ],
    failures=[{"type": "turnLimit", "rounds": 35}],
    par=16,
    hints=[
        {"id": "loam", "until": {"type": "buildings", "building": "FARM", "count": 1},
         "focus": ["@loam_a", "@loam_b", "@loam_c"]},
        {"id": "market", "until": {"type": "income", "coins": 20}},
        {"id": "harvest", "until": {"type": "objectiveDone", "index": 0}},
    ],
)

# --- 2. The Siege of Ash ----------------------------------------------------
# Two claimants, one of you, and the south corner of the continent. Nothing to
# win here but time: hold on until the levy arrives.

SIEGE_OF_ASH = dict(
    id="crown_siege_of_ash",
    seed=302,
    map="""
~  ~  ~   ~  ~         ~  ~  ~   ~  ~   ~  ~  ~
  ~  ~  1   1  1         .  .  2   2  2   ~  ~  ~
~  1  1   1C 1         .  .  2   2C 2   2  ~  ~
  ~  1  1   .  .         .  .  .   2  2   2  ~  ~
~  .  .   .  .         .  .  .   .  .   .  ~  ~
  ~  0  0   0  0         0  .  .   .  .   .  ~  ~
~  ~  0   0  0C        0  0  .   .  .   ~  ~  ~
  ~  ~  ~   0  0         0:muster .  .   .  ~  ~  ~  ~
~  ~  ~   ~  ~         ~  ~  ~   ~  ~   ~  ~  ~
""",
    seats=["player", ("ai", "EASY"), ("ai", "EASY")],
    rules=dict(
        diplomacyEnabled=False,
        disabledBuildings=buildings("FARM", "TOWER", "STRONG_TOWER", "MARKET", "LUMBER_CAMP"),
        **LANDLOCKED,
    ),
    treasury=[50, 40, 40],
    units=[
        {"seat": 0, "hex": "@capital0", "unitType": "SOLDIER", "tier": 2},
        {"seat": 0, "hex": "@muster", "unitType": "SOLDIER", "tier": 2},
    ],
    objectives=[{"type": "survive", "rounds": 14}],
    failures=[{"type": "turnLimit", "rounds": 45}],
    par=14,
    scripts=[
        {
            "id": "the_levy",
            "condition": {"type": "round", "round": 4},
            "action": {
                "tag": "the_levy",
                "spawns": [{"owner": 0, "hex": "@muster", "unitType": "SOLDIER", "tier": 3}],
                "grants": [{"player": 0, "coins": 40}],
            },
        },
    ],
    hints=[
        {"id": "outnumbered", "until": {"type": "round", "round": 2}},
        {"id": "levy", "until": {"type": "round", "round": 5}, "focus": ["@muster"]},
        {"id": "hold_on", "until": {"type": "objectiveDone", "index": 0}},
    ],
)

# --- 3. The Iron Veins ------------------------------------------------------
# Three gold veins in the middle of the map, and a mine is the only thing that
# gets anything out of one. Whoever banks the ore pays for the next chapter.

IRON_VEINS = dict(
    id="crown_iron_veins",
    seed=303,
    map="""
~  ~  ~   ~  ~   ~  ~   ~  ~   ~  ~  ~  ~
  ~  ~  .   .  .   .  .   .  .   .  ~  ~  ~
~  .  0   0  .   .  .$:vein_a .  .   1  1  ~  ~
  ~  0  0C  0  .   .  .   .  .   1  1C  1  ~  ~
~  0  0   .  .   .$:vein_b .  .   .  1   1  ~  ~
  ~  .  .   .  .   .  .   .$:vein_c .   .  .  ~  ~
~  ~  .   .  .   .  .   .  .   .  ~  ~  ~
  ~  ~  ~   .  .   .  .   .  .   ~  ~  ~  ~
~  ~  ~   ~  ~   ~  ~   ~  ~   ~  ~  ~  ~
""",
    seats=["player", ("ai", "NORMAL")],
    rules=dict(
        diplomacyEnabled=False,
        disabledBuildings=buildings("FARM", "TOWER", "STRONG_TOWER", "MINE", "MARKET"),
        **LANDLOCKED,
    ),
    treasury=[45, 40],
    objectives=[
        {"type": "captureHexes", "hexes": ["@vein_a", "@vein_b", "@vein_c"]},
        {"type": "build", "building": "MINE", "count": 3},
    ],
    failures=[{"type": "turnLimit", "rounds": 45}],
    par=22,
    aiSolvable=False,  # three named hexes, not a land grab
    hints=[
        {"id": "veins", "until": {"type": "ownsHexes", "hexes": ["@vein_b"]},
         "focus": ["@vein_a", "@vein_b", "@vein_c"]},
        {"id": "mine", "until": {"type": "buildings", "building": "MINE", "count": 1}},
        {"id": "bank", "until": {"type": "objectiveDone", "index": 1}},
    ],
)

# --- 4. The Last Wall -------------------------------------------------------
# Everything narrows to one hex: the throne itself. Lose it and the chapter is
# over — no amount of ground elsewhere buys it back.

LAST_WALL = dict(
    id="crown_last_wall",
    seed=304,
    map="""
~  ~  ~   ~   ~  ~   ~  ~   ~  ~   ~  ~  ~
  ~  ~  1   1   1  .   .  2   2  2   ~  ~  ~
~  1  1   1C  1  .   .  2   2C 2   2  ~  ~
  ~  1  1   .   .  .   .  .   2  2   2  ~  ~
~  .  .   .   .  .   .  .   .  .   .  ~  ~
  ~  .  .   0K:bastion 0  0   .  .   .  .   .  ~  ~
~  ~  .   0   0C 0   0  .   .  .   ~  ~  ~
  ~  ~  ~   .   0  0:muster .  .   .  ~   ~  ~  ~
~  ~  ~   ~   ~  ~   ~  ~   ~  ~   ~  ~  ~
""",
    seats=["player", ("ai", "NORMAL"), ("ai", "NORMAL")],
    rules=dict(
        disabledBuildings=buildings(
            "FARM", "TOWER", "STRONG_TOWER", "MINE", "MARKET", "LUMBER_CAMP",
        ),
        **LANDLOCKED,
    ),
    treasury=[60, 45, 45],
    objectives=[{"type": "survive", "rounds": 16}],
    failures=[
        {"type": "loseHexes", "hexes": ["@bastion"]},
        {"type": "turnLimit", "rounds": 50},
    ],
    par=16,
    scripts=[
        {
            "id": "sworn_swords",
            "condition": {"type": "round", "round": 8},
            "action": {
                "tag": "sworn_swords",
                "spawns": [
                    {"owner": 0, "hex": "@muster", "unitType": "SOLDIER", "tier": 3},
                ],
                "grants": [{"player": 0, "coins": 35}],
            },
        },
    ],
    hints=[
        {"id": "bastion", "until": {"type": "uiSignal", "name": "unitSelected"}, "focus": ["@bastion"]},
        {"id": "pact", "until": {"type": "round", "round": 5}},
        {"id": "endure", "until": {"type": "objectiveDone", "index": 0}},
    ],
)

# --- 5. The Breach ----------------------------------------------------------
# A castle line across the waist of the continent. Soldiers break on it; the
# catapult walks through it. The mission names the two keeps so there is no
# doubt about what "through" means.

BREACH = dict(
    id="crown_breach",
    seed=305,
    map="""
~  ~  ~   ~  ~  ~   ~          ~  ~   ~  ~  ~  ~
  ~  ~  .   .  .  .   1K:keep_a 1  .   .  .  ~  ~
~  .  0   0  .  .   1          1C 1   .  .  ~  ~
  ~  0  0C  0  .  .   1          1  1   .  .  ~  ~
~  0  0   .  .  .   1K:keep_b 1  .   .  .  ~  ~
  ~  .  .   .  .  .   .          .  .   .  .  ~  ~
~  ~  .   .  .  .   .          .  .   .  ~  ~  ~
  ~  ~  ~   .  .  .   .          .  .   ~  ~  ~  ~
~  ~  ~   ~  ~  ~   ~          ~  ~   ~  ~  ~  ~
""",
    seats=["player", ("ai", "EASY")],
    rules=dict(
        diplomacyEnabled=False,
        disabledBuildings=buildings(
            "FARM", "TOWER", "STRONG_TOWER", "MINE", "MARKET", "LUMBER_CAMP",
        ),
        **LANDLOCKED,
    ),
    treasury=[55, 45],
    objectives=[{"type": "captureHexes", "hexes": ["@keep_a", "@keep_b"]}],
    failures=[{"type": "turnLimit", "rounds": 45}],
    par=22,
    hints=[
        {"id": "wall", "until": {"type": "uiSignal", "name": "unitSelected"}, "focus": ["@keep_a", "@keep_b"]},
        {"id": "siege", "until": {"type": "units", "unitType": "CATAPULT", "count": 1}},
        {"id": "through", "until": {"type": "objectiveDone", "index": 0}},
    ],
)

# --- 6. Three Thrones -------------------------------------------------------
# The succession, settled. Everything on, nothing granted, no clock.

THREE_THRONES = dict(
    id="crown_three_thrones",
    seed=306,
    map="""
~  ~  ~   ~  ~   ~  ~   ~  ~   ~  ~  ~  ~
  ~  ~  0   0  0   .  .   1  1   1  ~  ~  ~
~  0  0C  0  .   .  .%  .  1C  1   1  ~  ~
  ~  0  0   .  .   .  .   .  .   1  1   ~  ~
~  .  .   .  .$  .  .   .$ .   .  .  ~  ~
  ~  .  .   .  .   .  .   .  .   .  .  ~  ~
~  ~  .   .  2  2   2  2   .  .   ~  ~  ~
  ~  ~  ~   2  2  2C  2  2   .  ~   ~  ~  ~
~  ~  ~   ~  ~   ~  ~   ~  ~   ~  ~  ~  ~
""",
    seats=["player", ("ai", "NORMAL"), ("ai", "NORMAL")],
    rules=dict(disabledBuildings=["WATCHTOWER", "PORT", "FISHERY", "BRIDGE"], **LANDLOCKED),
    treasury=[55, 55, 55],
    objectives=[{"type": "conquerAll"}],
    par=40,
    # A genuine three-way: the stand-in is as likely to be the one eliminated.
    aiSolvable=False,
    hints=[
        {"id": "succession", "until": {"type": "round", "round": 2}},
    ],
)


CAMPAIGN = dict(
    id="crown",
    order=2,
    levels=[GRANARY, SIEGE_OF_ASH, IRON_VEINS, LAST_WALL, BREACH, THREE_THRONES],
)
