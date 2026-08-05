"""The Academy — the tutorial campaign.

Eight missions, one new idea each. The teaching lever is `rules`: a level simply
switches off everything it has not taught yet, so the purchase tray narrows
itself and the coach never has to say "ignore that button". Run

    python3 tools/build_campaigns.py academy

to bake this into app/src/main/assets/campaigns/academy.json.
"""

# --- shared rule presets ----------------------------------------------------

ALL_BUILDINGS = [
    "FARM", "TOWER", "STRONG_TOWER", "MINE", "MARKET",
    "LUMBER_CAMP", "WATCHTOWER", "PORT", "FISHERY", "BRIDGE",
]


def buildings(*allowed):
    """The disabledBuildings list that leaves exactly `allowed` on the menu."""
    return [b for b in ALL_BUILDINGS if b not in allowed]


LAND_ONLY = dict(specialUnitsEnabled=False, navalEnabled=False, diplomacyEnabled=False)

# A dormant seat must never go bankrupt — that would wipe the garrison the level
# is built around — so every one of them gets a purse it cannot spend.
DORMANT = ("ai", "PASSIVE")
DORMANT_PURSE = 500


# --- 1. First Steps ---------------------------------------------------------
# Move and capture, nothing else. Income and upkeep are switched off outright,
# which empties the purchase tray (buyableAt filters by affordability) — the
# only thing on screen is two soldiers and some ground to take.

FIRST_STEPS = dict(
    id="academy_first_steps",
    seed=101,
    map="""
~  ~        ~        ~        ~        ~        ~  ~   ~
  ~  .        .:mead_b .:mead_d .        .        .  ~   ~
~  0:shed   0C       .:mead_c .:mead_f .        .  1   ~
  ~  0        .:mead_e .:mead_g .        .        1C 1   ~
~  ~        .        .        .        .        .  ~   ~
  ~  ~        ~        ~        ~        ~        ~  ~   ~
""",
    seats=["player", DORMANT],
    rules=dict(
        maxTier=1,
        hexIncome=0,
        unitUpkeep=[0, 0, 0, 0],
        disabledBuildings=ALL_BUILDINGS,
        **LAND_ONLY,
    ),
    treasury=[0, DORMANT_PURSE],
    units=[
        {"seat": 0, "hex": "@capital0", "unitType": "SOLDIER", "tier": 1},
        {"seat": 0, "hex": "@shed", "unitType": "SOLDIER", "tier": 1},
    ],
    objectives=[
        {
            "type": "captureHexes",
            "hexes": ["@mead_b", "@mead_c", "@mead_d", "@mead_e", "@mead_f", "@mead_g"],
        },
    ],
    par=4,
    hints=[
        {"id": "select", "until": {"type": "uiSignal", "name": "unitSelected"}},
        {
            "id": "capture",
            "until": {"type": "ownHexes", "count": 5},
            "focus": ["@mead_b", "@mead_c", "@mead_e"],
        },
        {"id": "end_turn", "until": {"type": "round", "round": 1}},
        {"id": "finish", "until": {"type": "objectiveDone", "index": 0}},
    ],
)

# --- 2. Coin & Crown --------------------------------------------------------
# Income, treasury, upkeep and the farm. Still peasants only, so the whole
# lesson is "ground pays, soldiers cost".

COIN_AND_CROWN = dict(
    id="academy_coin_and_crown",
    seed=102,
    map="""
~  ~  ~   ~   ~  ~          ~  ~  ~  ~  ~
  ~  .  .   .   .  .          .  .  .  ~  ~
~  .  0   0   .  .          .  .  .  ~  ~
  ~  .  0   0C  0  .          .%:rich .  1  ~  ~
~  .  .   0:shed  0  .          .  .  1C 1  ~
  ~  ~  .   .   .  .          .  .  1  ~  ~
~  ~  ~   .   .  .          .  ~  ~  ~  ~
  ~  ~  ~   ~   ~  ~          ~  ~  ~  ~  ~
""",
    seats=["player", DORMANT],
    rules=dict(maxTier=1, disabledBuildings=buildings("FARM"), **LAND_ONLY),
    treasury=[14, DORMANT_PURSE],
    objectives=[
        {"type": "income", "coins": 14},
        {"type": "build", "building": "FARM", "count": 2},
    ],
    par=10,
    hints=[
        {"id": "coins", "until": {"type": "uiSignal", "name": "economyOpened"}},
        {"id": "farm", "until": {"type": "buildings", "building": "FARM", "count": 1}},
        {"id": "expand", "until": {"type": "ownHexes", "count": 11}},
        {"id": "upkeep", "until": {"type": "objectiveDone", "index": 0}},
    ],
)

# --- 3. Shoulder to Shoulder ------------------------------------------------
# Merging and the defense number. Both outposts are garrisoned by a dormant
# peasant (defense 1), and the purse is deliberately too thin for a spearman —
# the only way through is to walk two peasants into one.

SHOULDER_TO_SHOULDER = dict(
    id="academy_shoulder",
    seed=103,
    map="""
~  ~   ~  ~  ~          ~   ~  ~  ~  ~
  ~  .   .  .  .          .   .  .  ~  ~
~  0   0  .  .          1   1  .  ~  ~
  ~  0C  0  .  1:post_a  1C  1  .  ~  ~
~  0:shed   .  .  .          1:post_b  1  .  ~  ~
  ~  .   .  .  .          .   .  ~  ~  ~
~  ~   ~  ~  ~          ~   ~  ~  ~  ~
""",
    seats=["player", DORMANT],
    rules=dict(maxTier=2, disabledBuildings=buildings("FARM"), **LAND_ONLY),
    treasury=[12, DORMANT_PURSE],
    units=[
        {"seat": 0, "hex": "@capital0", "unitType": "SOLDIER", "tier": 1},
        {"seat": 0, "hex": "@shed", "unitType": "SOLDIER", "tier": 1},
        {"seat": 1, "hex": "@post_a", "unitType": "SOLDIER", "tier": 1},
        {"seat": 1, "hex": "@post_b", "unitType": "SOLDIER", "tier": 1},
    ],
    objectives=[{"type": "captureHexes", "hexes": ["@post_a", "@post_b"]}],
    par=8,
    hints=[
        {"id": "defense", "until": {"type": "uiSignal", "name": "unitSelected"}, "focus": ["@post_a"]},
        {"id": "merge", "until": {"type": "units", "unitType": "SOLDIER", "count": 1, "tier": 2}},
        {"id": "storm", "until": {"type": "objectiveDone", "index": 0}, "focus": ["@post_a", "@post_b"]},
    ],
)

# --- 4. Stone and Timber ----------------------------------------------------
# Towers, castles, trees and the lumber camp — and the first opponent that
# fights back. Holding the pass for four rounds is what forces a tower.

STONE_AND_TIMBER = dict(
    id="academy_stone_and_timber",
    seed=104,
    map="""
~  ~   ~  ~   ~   ~          ~          ~   ~  ~   ~  ~
  ~  .   .  .   .t  .          .          .   .  .   ~  ~
~  .   0  0   .   .t         .          .   .  1   .  ~
  ~  0  0C  0   .:pass_a .          .:pass_c 1   1C 1   .  ~
~  0   0  .t  .   .:pass_b .          .   1  1   .  ~
  ~  .   .  .   .t  .          .          .   .  .   ~  ~
~  ~   .  .   .   .          .          .   .  ~   ~  ~
  ~  ~   ~  ~   ~   ~          ~          ~   ~  ~   ~  ~
""",
    seats=["player", ("ai", "EASY")],
    rules=dict(
        maxTier=3,
        disabledBuildings=buildings("FARM", "TOWER", "STRONG_TOWER", "LUMBER_CAMP"),
        **LAND_ONLY,
    ),
    treasury=[30, 30],
    objectives=[
        {"type": "build", "building": "TOWER", "count": 1},
        {"type": "holdHexes", "hexes": ["@pass_a", "@pass_b", "@pass_c"], "rounds": 4},
    ],
    failures=[{"type": "turnLimit", "rounds": 25}],
    par=14,
    hints=[
        {"id": "trees", "until": {"type": "treasury", "coins": 40}},
        {"id": "tower", "until": {"type": "buildings", "building": "TOWER", "count": 1}},
        {
            "id": "hold",
            "until": {"type": "objectiveDone", "index": 1},
            "focus": ["@pass_a", "@pass_b", "@pass_c"],
        },
    ],
)

# --- 5. Cut the Line --------------------------------------------------------
# Slicing and starvation. The opponent's territory is a long limb hanging off a
# single hex; take the neck and the whole arm stops paying and starves.

CUT_THE_LINE = dict(
    id="academy_cut_the_line",
    seed=105,
    map="""
~  ~  ~   ~   ~  ~   ~          ~  ~   ~  ~  ~
  ~  .  .   .   .  .   .          1  1   1  ~  ~
~  .  0   0   .  .   .          1  1   1  ~  ~
  ~  0  0C  0   .  .   .:neck    1  1C  1  .  ~
~  0  0   .   .  .   .          1  1   1  ~  ~
  ~  .  .   .   .  1  1          1  1   .  ~  ~
~  ~  .   .   .  .   .          .  ~   ~  ~  ~
  ~  ~  ~   ~   ~  ~   ~          ~  ~   ~  ~  ~
""",
    seats=["player", ("ai", "EASY")],
    rules=dict(
        maxTier=4,
        disabledBuildings=buildings("FARM", "TOWER", "STRONG_TOWER", "MINE", "MARKET", "LUMBER_CAMP"),
        **LAND_ONLY,
    ),
    treasury=[40, 25],
    objectives=[{"type": "eliminate", "seat": 1}],
    failures=[{"type": "turnLimit", "rounds": 30}],
    par=18,
    hints=[
        {"id": "slicing", "until": {"type": "round", "round": 2}},
        {"id": "loot", "until": {"type": "objectiveDone", "index": 0}},
    ],
)

# --- 6. Ranged and Siege ----------------------------------------------------
# Archers and catapults against a castle line no soldier ladder can crack in
# time. The catapult objective comes first on purpose: the level is a lesson
# about the right tool, not a grind.

RANGED_AND_SIEGE = dict(
    id="academy_ranged_and_siege",
    seed=106,
    # Deliberately cramped. A siege lesson is a tactical puzzle: leave room to
    # expand and the mission turns into an economy race that never reaches the walls.
    map="""
~  ~  ~   ~  ~  ~          ~  ~  ~  ~
  ~  .  0   0  .  1K:keep_a 1  .  ~  ~
~  0  0C  0  .  1          1C 1  ~  ~
  ~  0  0   .  .  1K:keep_b 1  .  ~  ~
~  ~  .   .  .  .          .  ~  ~  ~
  ~  ~  ~   ~  ~  ~          ~  ~  ~  ~
""",
    # An EASY rival on purpose: the lesson is that a castle line is a *tool* problem,
    # not a strength problem, so the opponent turtles rather than out-fighting you.
    seats=["player", ("ai", "EASY")],
    rules=dict(
        maxTier=4,
        navalEnabled=False,
        diplomacyEnabled=False,
        disabledBuildings=buildings("FARM", "TOWER", "STRONG_TOWER", "MINE", "MARKET", "LUMBER_CAMP"),
    ),
    treasury=[45, 30],
    objectives=[
        {"type": "field", "unitType": "CATAPULT", "count": 1},
        {"type": "captureHexes", "hexes": ["@keep_a", "@keep_b"]},
    ],
    failures=[{"type": "turnLimit", "rounds": 30}],
    par=18,
    hints=[
        {"id": "castle", "until": {"type": "uiSignal", "name": "unitSelected"}, "focus": ["@keep_a"]},
        {"id": "catapult", "until": {"type": "units", "unitType": "CATAPULT", "count": 1}},
        {"id": "archer", "until": {"type": "objectiveDone", "index": 1}, "focus": ["@keep_a", "@keep_b"]},
    ],
)

# --- 7. Salt and Sail -------------------------------------------------------
# Water. A port, a transport, a landing — and a rival who cannot be reached on
# foot, so there is no way to win by walking.

SALT_AND_SAIL = dict(
    id="academy_salt_and_sail",
    seed=107,
    # A two-hex channel: wide enough to sail, too wide to bridge cheaply, and there
    # is no land route at all — the level cannot be won on foot.
    map="""
~  ~  ~   ~  ~       ~   ~  ~        ~  ~  ~
  ~  .  .   .  .       ~   ~  ~        .  .  ~
~  .  0   0  .       ~   ~  .        1  1  ~
  ~  0  0C  0  0:cape ~*  ~  .:beach 1C .  ~
~  0  0   .  .       ~   ~  .        1  .  ~
  ~  .  .   .  .       ~   ~  ~        .  ~  ~
~  ~  .   .  .       ~   ~  ~        ~  ~  ~
  ~  ~  ~   ~  ~       ~   ~  ~        ~  ~  ~
""",
    seats=["player", ("ai", "EASY")],
    rules=dict(maxTier=4, diplomacyEnabled=False, disabledBuildings=buildings(
        "FARM", "TOWER", "STRONG_TOWER", "MINE", "MARKET", "LUMBER_CAMP", "PORT", "FISHERY", "BRIDGE",
    )),
    treasury=[70, 25],
    objectives=[
        {"type": "build", "building": "PORT", "count": 1},
        {"type": "eliminate", "seat": 1},
    ],
    # An amphibious conquest is slow by nature — the clock is a nudge, not a race.
    failures=[{"type": "turnLimit", "rounds": 60}],
    par=25,
    hints=[
        {"id": "port", "until": {"type": "buildings", "building": "PORT", "count": 1}, "focus": ["@cape"]},
        {"id": "transport", "until": {"type": "units", "unitType": "TRANSPORT", "count": 1}},
        {"id": "landing", "until": {"type": "ownsHexes", "hexes": ["@beach"]}, "focus": ["@beach"]},
        {"id": "conquer", "until": {"type": "objectiveDone", "index": 1}},
    ],
)

# --- 8. Words Before Swords -------------------------------------------------
# Three seats, everything switched on. Survive the opening while two rivals are
# stronger than you, then take the board. A pact is the only way to buy the
# time — and the level ends with you breaking it, which is the last lesson.

WORDS_BEFORE_SWORDS = dict(
    id="academy_words_before_swords",
    seed=108,
    map="""
~  ~  ~  ~   ~  ~   ~  ~   ~  ~   ~  ~  ~
  ~  .  .  .   .  .   .  .   .  .   .  ~  ~
~  .  .  0   0  .   .  .   .  1   1  .  ~
  ~  .  0  0C  0  .   .% .   1  1C  1  .  ~
~  .  .  0   .  .   .  .   .  1   1  .  ~
  ~  .  .  .   .  .$  .  .$  .  .   .  ~  ~
~  .  .  .   .  2   2  2   .  .   .  .  ~
  ~  .  .  .   .  2   2C 2   .  .   .  ~  ~
~  ~  .  .   .  .   .  .   .  .   ~  ~  ~
  ~  ~  ~  ~   ~  ~   ~  ~   ~  ~   ~  ~  ~
""",
    seats=["player", ("ai", "NORMAL"), ("ai", "EASY")],
    rules=dict(maxTier=4),
    treasury=[35, 45, 45],
    objectives=[
        {"type": "survive", "rounds": 8},
        {"type": "conquerAll"},
    ],
    failures=[{"type": "turnLimit", "rounds": 60}],
    par=35,
    hints=[
        {"id": "outnumbered", "until": {"type": "uiSignal", "name": "diplomacyOpened"}},
        {"id": "pact", "until": {"type": "round", "round": 4}},
        {"id": "betray", "until": {"type": "objectiveDone", "index": 0}},
    ],
    aiSolvable=False,
)


CAMPAIGN = dict(
    id="academy",
    order=0,
    levels=[
        FIRST_STEPS,
        COIN_AND_CROWN,
        SHOULDER_TO_SHOULDER,
        STONE_AND_TIMBER,
        CUT_THE_LINE,
        RANGED_AND_SIEGE,
        SALT_AND_SAIL,
        WORDS_BEFORE_SWORDS,
    ],
)
