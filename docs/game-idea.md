
## 1. Core Mechanics & Rules

The game is played on a hexagonal grid. Your goal is simple: **conquer the entire map** by eliminating all opposing colors.

### The Economy (The Most Vital Element)

Everything revolves around your coin economy.

* **Income:** Each hex under your control generates **1 coin per turn**.
* **Farms:** You can build Farms to increase income. They cost money up-front but provide a permanent turn-by-turn economic boost.
* **Upkeep:** Every single unit you spawn has a turn-based upkeep cost. If your upkeep exceeds your total income and your treasury hits zero, **all your units instantly die**, leaving you completely defenseless.

### Unit Hierarchy

Units have four tiers of strength. A unit can only capture a hex or destroy an enemy if its strength is *strictly greater* than the defense rating of that tile.

| Tier | Unit Type | Upkeep Cost / Turn | Defense / Attack Power |
| --- | --- | --- | --- |
| **Tier 1** | Peasant | 2 coins | 1 |
| **Tier 2** | Spearman | 6 coins | 2 |
| **Tier 3** | Baron | 18 coins | 3 |
| **Tier 4** | Knight | 54 coins | 4 |

> **Combining Units:** You can merge two adjacent units on the board to upgrade them to the next tier (e.g., merging two Peasants creates a Spearman).

### Defense & Buildings

Hexes are defended by the units standing on or next to them, or by defensive structures:

* **Towers (Cost ~15 coins):** Provide a static defense rating of **2** to the tile they sit on and all adjacent tiles. Only a Baron (Tier 3) or Knight (Tier 4) can capture a tower-protected hex. Towers cost no turn-by-turn upkeep, making them incredibly cost-effective.
* **Strong Towers / Castles:** Provide higher defense ratings but cost significantly more.

---

## 2. The Environmental Threat: Trees & Palms

Nature is your passive enemy in Game. Whenever a unit dies, a **Gravestone** appears, which eventually turns into a **Tree**.

* **The Economy Killer:** Any hex with a tree or palm tree **stops generating income**.
* **Spreading:** Trees will actively spread to adjacent hexes if left unchecked, eating away your economy.
* **Lumberjacking:** To get rid of them, you must send a unit to stand on the hex to cut it down. Clearing a tree rewards you with a flat **+3 coins**.

---

## 3. Key Strategies & Tactics

### The Early Game: Land Grab

At the start, ignore the enemy and focus entirely on **capturing neutral (gray) hexes** as fast as humanly possible. Use cheap Peasants to push outward. More hexes = more income = ability to build more Farms.

### Choke Points and "Towering"

Look for narrow land connections (1 to 3 hexes wide). Dropping a cheap Tower at a choke point creates an impassable wall for enemy Peasants and Spearmen, allowing you to safely ignore that border and focus your offense elsewhere.

### The Economy Trap (Avoid Over-Upgrading)

The most common rookie mistake is upgrading a unit to a Baron or Knight too early. Because a Knight costs 54 coins *every turn*, spawning one can instantly plunge your economy into a negative spiral, deleting your army. Only spawn high-tier units to execute a swift attack, then let them die or merge them if you can't afford them.

### Territory Isolation (Slicing)

If you manage to push a line of units directly through the middle of an enemy's territory and split it in half, **the smaller isolated piece loses connection to their main treasury**. Without funding, any enemy units trapped in that isolated pocket will instantly starve and die on the next turn.

---

Create a 3D aesthetic hyper-minimalist masterpiece.

---

## 4. The 3D Grid Architecture

Instead of flat 2D vector hexagons, the world should be constructed of individual **3D Hexagonal Prisms (Blocks)**.

* **Tactile Height Changes:**
* **Neutral territory:** Stays completely flush with a zero-level base plane.
* **Captured territory:** Rises slightly upward (e.g., +0.1 units on the Y-axis) when captured by a player color. This makes every empire look like a raised plateau, offering an instant visual cue of who is dominating the board.
* **Choke Points & Coastlines:** Give the entire hex grid a subtle, thick bezel with slightly rounded corners. This captures crisp highlights under 3D directional lighting.



---

## 5. Low-Poly "Chess Piece" Vocabulary

Units and buildings should abandon flat sprites completely in favor of stylized, geometric shapes. They shouldn't look realistic; they should look like premium, physical wooden or plastic game pieces.

* **Farms:** A minimalist, hollowed-out 3D wedge shape reminiscent of a small greenhouse, or a clean grid of tiny cube-like rows directly on the hex tile.
* **Towers / Castles:** A simple, solid cylinder with a cone cap for a standard Tower. For a Strong Tower, merge a cube base with an intersecting cylinder top.
* **Trees & Palms:** A single vertical capsule (the trunk) topped with a low-poly ico-sphere or a series of concentric green cones stacked together.
* **The Army (The 4 Tiers):** Use abstract, iconic 3D geometric tokens instead of human figures to keep rendering overhead low and the screen clean.
* **Tier 1 (Peasant):** A simple, upright 3D Capsule or Cone token.
* **Tier 2 (Spearman):** A sleek Prism or Diamond-cut crystal token.
* **Tier 3 (Baron):** A clean Hexagonal Pillar with a gold cap.
* **Tier 4 (Knight):** A bold Star or Crown extrusion token.



---

## 6. High-End Material & Lighting Setup

The real secret to making simple 3D geometry look modern lies in the lighting environment rather than complex textures.

* **Shading (Matte PBR):** Use completely matte materials with zero metallic properties and high roughness. This removes annoying, distracting glare and replicates a clean, modern tabletop board game texture.
* **Lighting:** Use a single strong **Directional Light** tilted at a 45-degree angle to cast dramatic, crisp, soft real-time shadows across the grid. Combine this with ambient occlusion (subtle dark contact shadows where objects touch the ground) to give every piece a satisfying sense of weight.

---

## 7. The Neo-Pastel Color Palette

Use modern palette balances immediate gameplay legibility with a premium look.

### The Faction Matrix

By shifting to soft, desaturated pastel tones with high luminance contrast, the map remains clean even when 10 factions are competing:

| Element / Faction | Base Color | Hex Code | Purpose / Visual Feel |
| --- | --- | --- | --- |
| **Neutral Hexes** | Pale Ash / Oatmeal | `#EAE6E1` | Recedes cleanly into the background. |
| **Factions (Player 1)** | Soft Sage Green | `#8FA89B` | Calming, distinct, non-aggressive player tone. |
| **Factions (Player 2)** | Dusty Coral | `#DE9B8B` | Striking but soft red alternative. |
| **Factions (Player 3)** | Muted Ochre | `#E6C594` | Warm mustard yellow variant. |
| **Factions (Player 4)** | Slate Blue | `#8FA3B5` | Deep, cool tone for broad contrast. |
| **Trees & Palms** | Deep Juniper Green | `#3D5A4C` | Highly visible block that cuts through any faction's color. |

---

## 8. Animation Polish

To make the game *feel* premium, use snappy, physics-based motion rather than static spawns:

* **Spawning a Unit:** The piece scales up from zero with a bouncy spring animation (ease-out-back curve), dropping onto the tile with a minor camera rumble.
* **Territory Capture:** When a tile changes ownership, it smoothly interpolates (slides) up or down to its new plateau height while its color cleanly transitions via a smooth radial wave outward from the center.