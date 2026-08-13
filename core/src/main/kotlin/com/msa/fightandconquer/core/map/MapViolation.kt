package com.msa.fightandconquer.core.map

import com.msa.fightandconquer.core.hex.Hex

/**
 * A structural defect in an authored map or custom scenario, as a typed code rather
 * than prose: the engine module carries no string resources, so the app maps each
 * variant to a translatable message (mirroring `RejectionReason`), while [describe]
 * keeps the developer-facing English used by tests and tooling.
 *
 * Map-level variants are produced by [MapValidator.validateAuthoredCodes]; the
 * scenario- and objective-level ones by the custom-map validator, which mirrors every
 * `require` in `LevelFactory` so a clean scenario can never throw at instantiation.
 */
sealed interface MapViolation {

    // Map-level — the engine's own invariants.

    data object NoLand : MapViolation
    data object DuplicateTiles : MapViolation
    data class SeaTileOwned(val hex: Hex) : MapViolation
    data class SeaTileHasBuilding(val hex: Hex) : MapViolation
    data class SeaTileHasFlora(val hex: Hex) : MapViolation
    data class SeaTileLandDeposit(val hex: Hex) : MapViolation
    data object FishShoalOnLand : MapViolation
    data class SeaSplit(val components: Int) : MapViolation
    data object SurfaceDisconnected : MapViolation
    data class LandmassUnreachable(val index: Int) : MapViolation
    data object NoCapitals : MapViolation
    data object DuplicateCapitals : MapViolation
    data class CapitalOffMap(val seat: Int) : MapViolation
    data class CapitalUnmarked(val seat: Int) : MapViolation
    data class CapitalWrongOwner(val seat: Int, val owner: Int?) : MapViolation
    data class SeatOwnsNothing(val seat: Int) : MapViolation
    data class SeatCutOffTiles(val seat: Int, val count: Int) : MapViolation
    data class OrphanOwner(val seat: Int) : MapViolation

    // Scenario-level — mirrors of LevelFactory's requires.

    data class SeatCountMismatch(val seats: Int, val capitals: Int) : MapViolation
    data object NoPlayerSeat : MapViolation
    data object MultiplePlayerSeats : MapViolation
    data class TreasurySizeMismatch(val purses: Int, val seats: Int) : MapViolation
    data class CivsSizeMismatch(val civs: Int, val seats: Int) : MapViolation
    data class UnitOffMap(val hex: Hex) : MapViolation
    data class UnitStacked(val hex: Hex) : MapViolation
    data class UnitNotOnOwnedGround(val seat: Int, val hex: Hex) : MapViolation
    data class UnitSeatMissing(val seat: Int, val hex: Hex) : MapViolation

    // Objective-level — mirrors of CampaignFormatTest's static checks.

    data object NoObjectives : MapViolation
    data class ObjectiveHexOffMap(val hex: Hex) : MapViolation
    data class ObjectiveHexUnreachable(val hex: Hex) : MapViolation
    data class ObjectiveSeatMissing(val seat: Int) : MapViolation
    data class ObjectiveAlreadyDecided(val index: Int) : MapViolation
    data object FailureAtStart : MapViolation

    /**
     * Developer-facing English. The map-level strings are byte-identical to the prose
     * `MapValidator.validateAuthored` has always returned, so existing tests and the
     * campaign build tooling keep their exact output.
     */
    fun describe(): String = when (this) {
        NoLand -> "map has no land"
        DuplicateTiles -> "duplicate tile definitions"
        is SeaTileOwned -> "sea tile $hex has an owner"
        is SeaTileHasBuilding -> "sea tile $hex has a building"
        is SeaTileHasFlora -> "sea tile $hex has flora"
        is SeaTileLandDeposit -> "sea tile $hex has a land deposit"
        FishShoalOnLand -> "fish shoal on land"
        is SeaSplit -> "sea split into $components components"
        SurfaceDisconnected -> "map is not one connected land+sea surface"
        is LandmassUnreachable -> "landmass $index is unreachable (no adjacent sea)"
        NoCapitals -> "no capitals"
        DuplicateCapitals -> "duplicate capitals"
        is CapitalOffMap -> "capital $seat off-map"
        is CapitalUnmarked -> "capital $seat not marked on tile"
        is CapitalWrongOwner -> "capital $seat on tile owned by $owner"
        is SeatOwnsNothing -> "seat $seat owns no hexes"
        is SeatCutOffTiles -> "seat $seat starts with $count cut-off hexes"
        is OrphanOwner -> "tiles owned by seat $seat, which has no capital"
        is SeatCountMismatch -> "$seats seats for $capitals capitals"
        NoPlayerSeat -> "no player seat"
        MultiplePlayerSeats -> "more than one player seat"
        is TreasurySizeMismatch -> "$purses purses for $seats seats"
        is CivsSizeMismatch -> "$civs civilizations for $seats seats"
        is UnitOffMap -> "starting unit on off-map hex $hex"
        is UnitStacked -> "two starting units on $hex"
        is UnitNotOnOwnedGround -> "seat $seat's starting unit at $hex is not on its owner's ground"
        is UnitSeatMissing -> "starting unit at $hex belongs to seat $seat, which does not exist"
        NoObjectives -> "no objectives"
        is ObjectiveHexOffMap -> "objective names off-map hex $hex"
        is ObjectiveHexUnreachable -> "objective hex $hex is unreachable from the player capital"
        is ObjectiveSeatMissing -> "objective names seat $seat, which does not exist"
        is ObjectiveAlreadyDecided -> "objective $index is already decided at turn zero"
        FailureAtStart -> "a fail condition already triggers at turn zero"
    }
}
