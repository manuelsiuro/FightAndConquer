package com.msa.fightandconquer.ui.menu

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.msa.fightandconquer.R

/** One menu action: its label and its tinted 24 dp glyph. */
enum class MenuEntry(@StringRes val labelRes: Int, @DrawableRes val iconRes: Int) {
    CONTINUE(R.string.menu_continue, R.drawable.ic_play),
    NEW_GAME(R.string.menu_new_game, R.drawable.ic_swords),
    CAMPAIGN(R.string.menu_campaign, R.drawable.ic_banner),
    MAP_EDITOR(R.string.menu_map_editor, R.drawable.ic_hex_pencil),
    GUIDE(R.string.guide_menu_entry, R.drawable.ic_research),
    SETTINGS(R.string.menu_settings, R.drawable.ic_gear),
    ABOUT(R.string.menu_about, R.drawable.ic_info),
}

/**
 * Pure layout rules for the main menu (no Compose, no Android calls): which entries show,
 * in what order, which one is the pastel primary, how the tiles chunk into rows and when
 * the title/tiles are anchored top/bottom. docs/ui-hud.md "Screens & navigation".
 *
 * `MenuScreen` is a dumb mapping over this object — the decisions live here so they get
 * JVM tests (`:app` has no Robolectric; only the emulator sees the real layout).
 */
object MenuLayout {
    const val TILES_PER_ROW = 3
    const val TILE_DP = 88
    const val TILE_GAP_DP = 8          // = HudSpacing
    const val SCREEN_GUTTER_DP = 12    // = HudGutter (bottom inset of the block)
    const val CONTINUE_BAR_HEIGHT_DP = 48

    /** Below this usable height the anchored layout would collide; fall back to a scroll column. */
    const val ANCHORED_MIN_HEIGHT_DP = 420

    /**
     * Height the title block takes at the top (its 16 dp inset + the two-line chip) —
     * an estimate, it only feeds the framing math, never the layout.
     */
    const val TITLE_BLOCK_DP = 104

    /** Never lift more than this — beyond it the world's far rim leaves the screen top. */
    const val MAX_LIFT_FRACTION = 0.15f

    /** The Continue bar above the grid, only with an autosave. */
    fun continueBar(hasAutosave: Boolean): MenuEntry? =
        if (hasAutosave) MenuEntry.CONTINUE else null

    /** The grid is always these six, in this order, regardless of the autosave. */
    fun tiles(): List<MenuEntry> = listOf(
        MenuEntry.NEW_GAME,
        MenuEntry.CAMPAIGN,
        MenuEntry.MAP_EDITOR,
        MenuEntry.GUIDE,
        MenuEntry.SETTINGS,
        MenuEntry.ABOUT,
    )

    /** The one pastel surface: Continue when it exists, else New game. */
    fun primary(hasAutosave: Boolean): MenuEntry =
        if (hasAutosave) MenuEntry.CONTINUE else MenuEntry.NEW_GAME

    /** [tiles] chunked into rows of [perRow]; the last row may be shorter, never empty. */
    fun rows(tiles: List<MenuEntry>, perRow: Int = TILES_PER_ROW): List<List<MenuEntry>> =
        tiles.chunked(perRow)

    /** Width of a full row: 3 tiles + 2 gaps = 280 dp (the Continue bar matches it). */
    fun rowWidthDp(): Int = TILES_PER_ROW * TILE_DP + (TILES_PER_ROW - 1) * TILE_GAP_DP

    /** Height of the whole bottom block incl. its 12 dp bottom inset: 196 dp, or 252 dp with Continue. */
    fun blockHeightDp(hasAutosave: Boolean): Int {
        val rowCount = rows(tiles()).size
        val grid = rowCount * TILE_DP + (rowCount - 1) * TILE_GAP_DP
        val continueBar = if (hasAutosave) CONTINUE_BAR_HEIGHT_DP + TILE_GAP_DP else 0
        return grid + continueBar + SCREEN_GUTTER_DP
    }

    /** True when title-on-top / tiles-at-bottom fit without colliding. */
    fun anchored(availableHeightDp: Int): Boolean = availableHeightDp >= ANCHORED_MIN_HEIGHT_DP

    /**
     * Fraction of the usable height the orbiting world's center should sit *above* the
     * screen center so it lands in the middle of the free band instead of behind the
     * tiles: the band runs from [TITLE_BLOCK_DP] down to the top of the bottom block, so
     * its middle is (block - title) / 2 dp above the screen's middle, i.e.
     * (block - title) / (2 . availableHeight) of the height. Clamped to
     * [0, MAX_LIFT_FRACTION]; 0 when the layout is not [anchored] (the scrolling column
     * covers the world anyway). Fed to `BoardScene.orbitTargetLiftFraction`.
     */
    fun liftFraction(availableHeightDp: Int, hasAutosave: Boolean): Float {
        if (!anchored(availableHeightDp)) return 0f
        val bandOffsetDp = blockHeightDp(hasAutosave) - TITLE_BLOCK_DP
        return (bandOffsetDp.toFloat() / (2f * availableHeightDp)).coerceIn(0f, MAX_LIFT_FRACTION)
    }
}
