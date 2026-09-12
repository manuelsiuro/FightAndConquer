package com.msa.fightandconquer.ui.game

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.engine.PurchaseOption

/** The two halves of the purchase menu: what the selected hex can recruit, what it can build. */
enum class PurchaseCategory(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    @StringRes val descriptionRes: Int,
) {
    RECRUIT(R.string.hud_recruit, R.drawable.ic_swords, R.string.cd_open_recruit),
    BUILD(R.string.hud_build, R.drawable.ic_build, R.string.cd_open_build),
}

/**
 * Pure rules of the Recruit / Build pair over the engine's [PurchaseOption] list (no Compose,
 * no Android calls). docs/ui-hud.md "Interaction model", docs/design/game-screen-hud-handoff.md
 * addendum "Purchase menu".
 *
 * The composable and the ViewModel are dumb mappings over this object — the decisions live
 * here so they get JVM tests (`:app` has no Robolectric; only a device sees the real pair).
 */
object PurchaseMenu {

    /** Units recruit, structures build — a locked card stays in its category. */
    fun categoryOf(option: PurchaseOption): PurchaseCategory = when (option) {
        is PurchaseOption.Unit -> PurchaseCategory.RECRUIT
        is PurchaseOption.Structure -> PurchaseCategory.BUILD
    }

    /** [all] filtered to [category], engine order preserved. */
    fun options(all: List<PurchaseOption>, category: PurchaseCategory): List<PurchaseOption> =
        all.filter { categoryOf(it) == category }

    /** A button is live when its half has at least one card (locked cards count: they are shown). */
    fun available(all: List<PurchaseOption>, category: PurchaseCategory): Boolean =
        all.any { categoryOf(it) == category }

    /** The pair shows whenever the hex sells anything at all — never for an empty list. */
    fun shown(all: List<PurchaseOption>): Boolean = all.isNotEmpty()

    /** The category whose cards are open: [requested] if it has something to sell, else null. */
    fun open(all: List<PurchaseOption>, requested: PurchaseCategory?): PurchaseCategory? =
        requested?.takeIf { available(all, it) }

    /** Tap rule: the active button folds its cards (null); any other button opens itself. */
    fun toggle(current: PurchaseCategory?, tapped: PurchaseCategory): PurchaseCategory? =
        if (current == tapped) null else tapped
}
