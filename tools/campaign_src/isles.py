"""The Sundered Isles — a naval story campaign.

Six chapters in one archipelago: four islands in a ring of open water, seen from
a different corner each time. The Academy teaches; this campaign asks. Every
level here is a logistics problem before it is a fight — a channel you cannot
walk, a lighthouse you cannot lose, a fleet you have to hunt.

Story beats (`scripts`) do the narrating: a relief squadron that arrives on
schedule, raiders that appear off your coast. They are ordinary replayable
actions, so a saved game resumes mid-chapter with the story intact.

    python3 tools/build_campaigns.py isles
"""

ALL_BUILDINGS = [
    "FARM", "TOWER", "STRONG_TOWER", "MINE", "MARKET",
    "LUMBER_CAMP", "WATCHTOWER", "PORT", "FISHERY", "BRIDGE",
]


def buildings(*allowed):
    return [b for b in ALL_BUILDINGS if b not in allowed]


# The archipelago every chapter is cut from: four ten-hex islands, north pair
# split by a two-wide channel, the southern pair reachable only by sea.
#
#   A . . .        B . . .
#   . . . .      . . . .
#   . . .          . . .
#
#   . . .          . . .
#   C . . .      D . . .
#   . . .          . . .


# --- 1. Landfall ------------------------------------------------------------
# You already hold a port and a loaded transport. The whole chapter is the
# crossing: put soldiers on the far beach and keep them there.

LANDFALL = dict(
    id="isles_landfall",
    seed=201,
    map="""
~  ~  ~   ~  ~  ~   ~  ~  ~        ~        ~        ~  ~
  ~  0  0   0  ~:ferry ~   ~  ~  1        1        .        ~  ~
~  0  0C  0  0P     ~*  ~  .  1C       1        .        ~  ~
  ~  0  0   0  ~:relief ~   ~  ~  .:sand_a .:sand_b .:sand_c ~  ~
~  ~  ~   ~  ~  ~   ~  ~  ~        ~        ~        ~  ~
  ~  ~  .   .  .  ~   ~  ~  .        .        .        ~  ~
~  .  .   .  .  ~   ~  .  .        .        .        ~  ~
  ~  ~  .   .  .  ~   ~  ~  .        .        .        ~  ~
~  ~  ~   ~  ~  ~   ~  ~  ~        ~        ~        ~  ~
""",
    seats=["player", ("ai", "EASY")],
    rules=dict(
        diplomacyEnabled=False,
        specialUnitsEnabled=False,
        disabledBuildings=buildings("FARM", "TOWER", "PORT", "FISHERY", "BRIDGE"),
    ),
    treasury=[55, 30],
    units=[
        {"seat": 0, "hex": "@ferry", "unitType": "TRANSPORT", "tier": 1},
        {"seat": 0, "hex": "@capital0", "unitType": "SOLDIER", "tier": 2},
    ],
    objectives=[
        {"type": "captureHexes", "hexes": ["@sand_a", "@sand_b", "@sand_c"]},
        {"type": "holdHexes", "hexes": ["@sand_a", "@sand_b"], "rounds": 3},
    ],
    failures=[{"type": "turnLimit", "rounds": 45}],
    par=20,
    # A named beachhead, not a conquest: the AI stand-in sails for the easiest
    # neutral island instead. Verified by hand on a device.
    aiSolvable=False,
    scripts=[
        {
            "id": "relief_squadron",
            "condition": {"type": "round", "round": 5},
            "action": {
                "tag": "relief_squadron",
                "spawns": [{"owner": 0, "hex": "@relief", "unitType": "TRANSPORT", "tier": 1}],
                "grants": [{"player": 0, "coins": 25}],
            },
        },
    ],
    hints=[
        {"id": "embark", "until": {"type": "uiSignal", "name": "unitSelected"}, "focus": ["@ferry"]},
        {"id": "sail", "until": {"type": "ownsHexes", "hexes": ["@sand_a"]}, "focus": ["@sand_a"]},
        {"id": "supply", "until": {"type": "objectiveDone", "index": 1}},
    ],
)

# --- 2. The Lighthouse ------------------------------------------------------
# Fog rolls in. The watchtower on the headland is the only thing that sees the
# channel — lose it and the chapter ends, whatever else you have done.

LIGHTHOUSE = dict(
    id="isles_lighthouse",
    seed=202,
    map="""
~  ~  ~   ~  ~  ~   ~  ~  ~   ~   ~  ~  ~
  ~  .  .   .  ~  ~   ~  ~  1   1   1  ~  ~
~  .  .   .  .  ~* ~  .  1   1C  1  ~  ~
  ~  .  .   .  ~  ~   ~  ~  1   1   1  ~  ~
~  ~  ~   ~  ~  ~   ~  ~  ~   ~   ~  ~  ~
  ~  ~  0   0  0  ~   ~  ~  .   .   .  ~  ~
~  0  0C  0  0P ~:raid_water ~  .  .   .   .  ~  ~
  ~  ~  0W:beacon 0  0  ~   ~  ~  .   .   .  ~  ~
~  ~  ~   ~  ~  ~   ~  ~  ~   ~   ~  ~  ~
""",
    seats=["player", ("ai", "NORMAL")],
    rules=dict(
        fogOfWar=True,
        diplomacyEnabled=False,
        disabledBuildings=buildings("FARM", "TOWER", "STRONG_TOWER", "WATCHTOWER", "PORT", "FISHERY"),
    ),
    treasury=[60, 45],
    objectives=[{"type": "survive", "rounds": 12}],
    failures=[
        {"type": "loseHexes", "hexes": ["@beacon"]},
        {"type": "turnLimit", "rounds": 45},
    ],
    par=12,
    scripts=[
        {
            "id": "night_raid",
            "condition": {"type": "round", "round": 4},
            "action": {
                "tag": "night_raid",
                "spawns": [{"owner": 1, "hex": "@raid_water", "unitType": "TRANSPORT", "tier": 1}],
            },
        },
    ],
    hints=[
        {"id": "fog", "until": {"type": "round", "round": 1}},
        {"id": "beacon", "until": {"type": "uiSignal", "name": "unitSelected"}, "focus": ["@beacon"]},
        {"id": "endure", "until": {"type": "objectiveDone", "index": 0}},
    ],
)

# --- 3. Wolves of the Channel -----------------------------------------------
# A hunt. The raider fleet is scripted so the quarry is guaranteed to exist —
# a naval objective must never depend on an opponent choosing to build boats.

WOLVES = dict(
    id="isles_wolves",
    seed=203,
    map="""
~  ~  ~   ~   ~   ~   ~  ~  ~   ~   ~  ~  ~
  ~  0  0   0   ~   ~   ~  ~  1   1   1  ~  ~
~  0  0C  0   0P  ~*  ~  .  1   1C  1  ~  ~
  ~  0  0   0   ~:hunt_a ~:hunt_b ~  ~  1   1   1  ~  ~
~  ~  ~   ~   ~   ~   ~  ~  ~   ~   ~  ~  ~
  ~  ~  .   .   .   ~   ~  ~  .   .   .  ~  ~
~  .  .   .   .   ~   ~  .  .   .   .  ~  ~
  ~  ~  .   .   .   ~   ~  ~  .   .   .  ~  ~
~  ~  ~   ~   ~   ~   ~  ~  ~   ~   ~  ~  ~
""",
    seats=["player", ("ai", "NORMAL")],
    rules=dict(
        diplomacyEnabled=False,
        disabledBuildings=buildings("FARM", "TOWER", "MINE", "MARKET", "PORT", "FISHERY"),
    ),
    treasury=[80, 50],
    objectives=[{"type": "sink", "count": 3}],
    failures=[{"type": "turnLimit", "rounds": 45}],
    par=18,
    aiSolvable=False,  # hunting is an objective, not a land grab
    scripts=[
        {
            "id": "wolves_first",
            "condition": {"type": "round", "round": 2},
            "action": {
                "tag": "wolves_first",
                "spawns": [{"owner": 1, "hex": "@hunt_a", "unitType": "TRANSPORT", "tier": 1}],
            },
        },
        {
            "id": "wolves_second",
            "condition": {"type": "round", "round": 5},
            "action": {
                "tag": "wolves_second",
                "spawns": [{"owner": 1, "hex": "@hunt_b", "unitType": "TRANSPORT", "tier": 1}],
            },
        },
        {
            "id": "wolves_third",
            "condition": {"type": "round", "round": 8},
            "action": {
                "tag": "wolves_third",
                "spawns": [{"owner": 1, "hex": "@hunt_a", "unitType": "TRANSPORT", "tier": 1}],
            },
        },
        {
            "id": "wolves_fourth",
            "condition": {"type": "round", "round": 11},
            "action": {
                "tag": "wolves_fourth",
                "spawns": [{"owner": 1, "hex": "@hunt_b", "unitType": "TRANSPORT", "tier": 1}],
            },
        },
    ],
    hints=[
        {"id": "warship", "until": {"type": "units", "unitType": "WARSHIP", "count": 1}},
        {"id": "ties", "until": {"type": "objectiveDone", "index": 0}, "focus": ["@hunt_a", "@hunt_b"]},
    ],
)

# --- 4. The Strait ----------------------------------------------------------
# One hex of water is all that separates the northern isles. A bridge is
# cheaper than a fleet — if you can lay it before the clock runs out.

STRAIT = dict(
    id="isles_strait",
    seed=204,
    map="""
~  ~  ~   ~  ~        ~        ~  ~  ~   ~   ~  ~  ~
  ~  0  0   0  ~        ~        ~  ~  1   1   1  ~  ~
~  0  0C  0  0  ~:span_a ~:span_b .  1   1C  1  ~  ~
  ~  0  0   0  ~        ~        ~  ~  1   1   1  ~  ~
~  ~  ~   ~  ~        ~        ~  ~  ~   ~   ~  ~  ~
  ~  ~  .   .  .        ~        ~  ~  .   .   .  ~  ~
~  .  .   .  .        ~        ~  .  .   .   .  ~  ~
  ~  ~  .   .  .        ~        ~  ~  .   .   .  ~  ~
~  ~  ~   ~  ~        ~        ~  ~  ~   ~   ~  ~  ~
""",
    seats=["player", ("ai", "EASY")],
    rules=dict(
        diplomacyEnabled=False,
        specialUnitsEnabled=False,
        disabledBuildings=buildings("FARM", "TOWER", "MARKET", "PORT", "BRIDGE"),
    ),
    treasury=[70, 35],
    objectives=[
        {"type": "build", "building": "BRIDGE", "count": 2},
        {"type": "eliminate", "seat": 1},
    ],
    failures=[{"type": "turnLimit", "rounds": 50}],
    par=24,
    aiSolvable=False,  # the AI never lays a bridge it was not scored for
    hints=[
        {"id": "bridge", "until": {"type": "buildings", "building": "BRIDGE", "count": 1},
         "focus": ["@span_a", "@span_b"]},
        {"id": "march", "until": {"type": "objectiveDone", "index": 1}},
    ],
)

# --- 5. The Admiral's Grave -------------------------------------------------
# Three flags in the water. The admiral on the eastern isle is the target; the
# third seat is a squabbling neighbour who will happily take your back if you
# turn it. Pacts are on for the first time in this campaign.

ADMIRALS_GRAVE = dict(
    id="isles_admirals_grave",
    seed=205,
    map="""
~  ~  ~   ~   ~  ~  ~  ~  ~   ~   ~  ~  ~
  ~  0  0   0   ~  ~  ~  ~  1   1   1  ~  ~
~  0  0C  0   0P ~* ~  .  1   1C  1P ~  ~
  ~  0  0   0   ~  ~  ~  ~  1   1   1  ~  ~
~  ~  ~   ~   ~  ~  ~  ~  ~   ~   ~  ~  ~
  ~  ~  .   .   .  ~  ~  ~  2   2   2  ~  ~
~  .  .   .   .  ~  ~  2  2   2C  2  ~  ~
  ~  ~  .   .   .  ~  ~  ~  2   2   2  ~  ~
~  ~  ~   ~   ~  ~  ~  ~  ~   ~   ~  ~  ~
""",
    seats=["player", ("ai", "NORMAL"), ("ai", "EASY")],
    rules=dict(disabledBuildings=buildings(
        "FARM", "TOWER", "STRONG_TOWER", "MINE", "MARKET", "PORT", "FISHERY", "BRIDGE",
    )),
    treasury=[75, 60, 55],
    objectives=[{"type": "eliminate", "seat": 1}],
    failures=[{"type": "turnLimit", "rounds": 60}],
    par=30,
    # Three-way and deliberately hard: the AI holds on but rarely closes it out.
    aiSolvable=False,
    hints=[
        {"id": "two_fronts", "until": {"type": "uiSignal", "name": "diplomacyOpened"}},
        {"id": "admiral", "until": {"type": "objectiveDone", "index": 0}},
    ],
)

# --- 6. Crown of Salt -------------------------------------------------------
# The whole archipelago, no clock, no special pleading. Everything the campaign
# taught, against two opponents who have learned it too.

CROWN_OF_SALT = dict(
    id="isles_crown_of_salt",
    seed=206,
    map="""
~  ~  ~   ~   ~  ~  ~  ~  ~   ~   ~  ~  ~
  ~  0  0   0   ~  ~  ~  ~  1   1   1  ~  ~
~  0  0C  0   0P ~* ~  .  1   1C  1P ~  ~
  ~  0  0   0   ~  ~  ~  ~  1   1   1  ~  ~
~  ~  ~   ~   ~  ~  ~  ~  ~   ~   ~  ~  ~
  ~  ~  .$  .   .  ~  ~  ~  2   2   2  ~  ~
~  .  .   .   .% ~* ~  2  2   2C  2P ~  ~
  ~  ~  .   .   .  ~  ~  ~  2   2   2  ~  ~
~  ~  ~   ~   ~  ~  ~  ~  ~   ~   ~  ~  ~
""",
    seats=["player", ("ai", "NORMAL"), ("ai", "NORMAL")],
    rules=dict(disabledBuildings=["WATCHTOWER"]),
    treasury=[85, 70, 70],
    objectives=[{"type": "conquerAll"}],
    par=40,
    hints=[
        {"id": "finale", "until": {"type": "round", "round": 2}},
    ],
    # A 1-v-2 against two seats of the SAME difficulty as the stand-in: the
    # playthrough AI beating two copies of itself is a coin toss, not a proof.
    # Same reasoning as crown_three_thrones. Termination and the opening gate
    # still apply; a human who finished the campaign outplays two NORMALs.
    aiSolvable=False,
)


CAMPAIGN = dict(
    id="isles",
    order=1,
    levels=[LANDFALL, LIGHTHOUSE, WOLVES, STRAIT, ADMIRALS_GRAVE, CROWN_OF_SALT],
)
