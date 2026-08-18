package com.msa.fightandconquer.render.scene

import android.content.Context
import android.util.Log
import com.google.android.filament.EntityManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.msa.fightandconquer.core.engine.DeathCause
import com.msa.fightandconquer.core.engine.GameEvent
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Civilization
import com.msa.fightandconquer.core.model.Flora
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.UnitId
import com.msa.fightandconquer.render.CameraRig
import com.msa.fightandconquer.render.HexPicker
import com.msa.fightandconquer.render.HexWorld
import com.msa.fightandconquer.render.RenderEngine
import com.msa.fightandconquer.render.SceneController
import com.msa.fightandconquer.render.SceneEnvironment
import com.msa.fightandconquer.render.Transforms
import com.msa.fightandconquer.render.anim.Animator
import com.msa.fightandconquer.render.anim.Easings
import com.msa.fightandconquer.render.material.MaterialStore
import com.msa.fightandconquer.render.material.Palette
import com.msa.fightandconquer.render.mesh.ColorRole
import com.msa.fightandconquer.render.mesh.GpuMesh
import com.msa.fightandconquer.render.mesh.PieceKind
import com.msa.fightandconquer.render.mesh.PieceMeshes
import com.msa.fightandconquer.render.mesh.Primitives
import com.msa.fightandconquer.render.mesh.upload
import dev.romainguy.kotlin.math.Float2
import dev.romainguy.kotlin.math.Float3
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.sin

/**
 * The complete board view: tiles + chess pieces, driven by GameEvents.
 *
 * Events queue up and play strictly in order (one animation beat at a time);
 * when the queue drains, [reconcile] snaps the scene to the authoritative GameState —
 * so an unhandled event degrades to "no animation", never a wrong board.
 */
class BoardScene(
    private val engine: RenderEngine,
    context: Context,
    initialState: GameState,
) : SceneController {

    private val filament = engine.engine
    private val materials = MaterialStore(context, filament)
    private val environment = SceneEnvironment(filament, engine.scene)
    private val hexMesh: GpuMesh = Primitives.hexPrism().upload(filament)
    private val pieceMeshes = PieceMeshes(filament, context)
    private val animator = Animator()

    /**
     * Camera glides run on their own animator: the shared [animator] gates the event
     * queue and tap-to-skip, so a glide there would swallow taps and stall beats.
     */
    private val cameraAnimator = Animator()

    val rig = CameraRig()
    private val picker = HexPicker(
        topYOf = { hex -> tiles[hex]?.let { it.y + Primitives.HEX_HEIGHT } },
    )

    // ----- tiles -----

    private class TileEntity(
        val entity: Int,
        /** Per-tile hexTile instance for land; the SHARED water instance for sea. */
        var instance: MaterialInstance,
        var color: Float3,
        var raised: Boolean,
        var y: Float,
        val sea: Boolean = false,
    )

    private val tiles = HashMap<Hex, TileEntity>()

    // ----- water (all sea tiles share one animated material instance per fog band) -----

    private val waterVisible: MaterialInstance by lazy {
        materials.material("water").createInstance().apply {
            setParameter("shallowColor", Palette.SEA.x, Palette.SEA.y, Palette.SEA.z)
            setParameter("deepColor", Palette.SEA_DEEP.x, Palette.SEA_DEEP.y, Palette.SEA_DEEP.z)
            setParameter("time", 0f)
        }
    }
    private val waterExplored: MaterialInstance by lazy {
        materials.material("water").createInstance().apply {
            val s = Palette.SEA * FOG_EXPLORED_FACTOR
            val d = Palette.SEA_DEEP * FOG_EXPLORED_FACTOR
            setParameter("shallowColor", s.x, s.y, s.z)
            setParameter("deepColor", d.x, d.y, d.z)
            setParameter("time", 0f)
        }
    }
    private var hasSea = false
    private var waterTime = 0f

    // ----- pieces -----

    private inner class Piece(
        val kind: PieceKind,
        /** The art set actually rendering — [PieceMeshes.artCivFor] of the owner's civ.
         *  Part of piece identity: reconcile recreates on a mismatch, like a kind change. */
        val civ: Civilization,
        val entities: IntArray,
        val instances: List<MaterialInstance>,
        val roles: List<com.msa.fightandconquer.render.mesh.ColorRole>,
        val ownerIndex: Int?,
        var hex: Hex,
        var scale: Float = 1f,
        var yOffset: Float = 0f,
        var xz: Pair<Float, Float>? = null, // non-null while hopping between hexes
        /** Segment start while animating — fog visibility judges both ends. */
        var animFrom: Hex? = null,
        /** Y-rotation in radians (bridges orient toward their connected shores). */
        var yaw: Float = 0f,
    ) {
        /** View-only mirror of GameUnit.spent: spent units render darker. */
        var dimmed = false
            private set

        /** View-only fog flag: hidden pieces leave the scene (entities stay alive). */
        var hidden = false
            private set

        fun setDimmed(dim: Boolean) {
            if (dim == dimmed) return
            dimmed = dim
            val f = if (dim) DIM_FACTOR else 1f
            for (i in instances.indices) {
                val c = colorFor(roles[i], ownerIndex)
                instances[i].setParameter("baseColor", c.x * f, c.y * f, c.z * f)
            }
        }

        fun setHidden(hide: Boolean) {
            if (hide == hidden) return
            hidden = hide
            for (entity in entities) {
                if (hide) engine.scene.removeEntity(entity) else engine.scene.addEntity(entity)
            }
        }

        fun updateTransform() {
            val (x, z) = xz ?: (HexWorld.centerX(hex) to HexWorld.centerZ(hex))
            val y = tileTopY(hex) + yOffset
            val tm = filament.transformManager
            for (entity in entities) {
                var ti = tm.getInstance(entity)
                if (ti == 0) ti = tm.create(entity)
                tm.setTransform(ti, Transforms.trs(x, y, z, angleYRadians = yaw, scale = scale))
            }
        }
    }

    private val unitPieces = HashMap<UnitId, Piece>()
    private val buildingPieces = HashMap<Hex, Piece>()
    private val floraPieces = HashMap<Hex, Piece>()

    /**
     * Static terrain markers (gold veins, fertile ground). Never event-animated:
     * they exist from map generation, hide while a building stands on their hex, and
     * — unlike units/flora — stay visible (dimmed) on explored-but-fogged terrain.
     */
    private val depositPieces = HashMap<Hex, Piece>()

    // ----- event queue -----

    private val eventQueue = ArrayDeque<GameEvent>()
    private var pendingState: GameState? = null
    private var latestState: GameState = initialState
    private var rumbleTime = -1f
    private var boardSpanX = 10f
    private var boardSpanZ = 10f
    private var cameraFitted = false

    var onTap: ((Hex) -> Unit)? = null

    // ----- highlights (selection + legal moves) -----

    private val highlightMesh: GpuMesh = Primitives.hexDisc(Primitives.HEX_RADIUS - 0.07f).upload(filament)
    private class HighlightEntity(
        val entity: Int,
        val instance: MaterialInstance,
        var inScene: Boolean,
        var pulse: Boolean = false,
        val rgba: FloatArray = FloatArray(4),
    )
    private val highlightPool = ArrayList<HighlightEntity>()
    private var highlightsShown = 0
    private var highlightClock = 0f

    // ----- defense auras (ring decals under covered tiles) -----

    private val auraMesh: GpuMesh = Primitives.hexAnnulus(0.34f, 0.44f).upload(filament)
    private class AuraEntity(val entity: Int, val instance: MaterialInstance, var inScene: Boolean)
    private val auraPool = ArrayList<AuraEntity>()
    private var aurasShown = 0

    // ----- fog of war (view-only, synced silently — never a reconcile correction) -----

    /** Hexes in the viewer's live vision; null = fog off (everything visible). */
    private var fogVisible: Set<Hex>? = null

    /** Explored memory: fogged hexes render as dark terrain instead of near-black. */
    private var fogExplored: Set<Hex> = emptySet()

    /**
     * Swap in the viewer's fog sets and re-apply them immediately. Tiles keep their
     * LOGICAL faction color in [TileEntity.color] (reconcile's diff is untouched);
     * only the rendered uniforms change. Pieces on fogged hexes leave the scene the
     * same way highlights do.
     */
    fun setFog(visible: Set<Hex>?, explored: Set<Hex>?) {
        wake() // uniforms changed — show the new fog promptly even when idle
        fogVisible = visible
        fogExplored = explored ?: emptySet()
        for ((hex, te) in tiles) {
            applyTileColor(hex, te)
            // The rendered raise is fog-aware (ownership is not terrain, so the
            // fog rim stays flat) — re-seat every tile for the new fog edge.
            if (!te.sea) setTileTransform(hex, te)
        }
        for (piece in unitPieces.values) piece.setHidden(pieceFogged(piece))
        for ((hex, piece) in buildingPieces) piece.setHidden(isFogged(hex))
        for ((hex, piece) in floraPieces) piece.setHidden(isFogged(hex))
        for ((hex, piece) in depositPieces) applyDepositFog(hex, piece)
        // Tile tops may have visually moved with the fog edge — re-glue pieces.
        for (piece in buildingPieces.values) piece.updateTransform()
        for (piece in floraPieces.values) piece.updateTransform()
        for (piece in depositPieces.values) piece.updateTransform()
        for (piece in unitPieces.values) if (piece.xz == null) piece.updateTransform()
        // Auras were possibly drawn before fog arrived (init reconcile) or the fog
        // edge moved — re-derive them so no ring survives inside the fog.
        refreshAuras(latestState)
    }

    /** Fog state of a piece: while animating, both segment ends must be visible. */
    private fun pieceFogged(piece: Piece): Boolean {
        val from = piece.animFrom ?: return isFogged(piece.hex)
        return FogRules.segmentHidden(fogVisible, from, piece.hex)
    }

    private fun isFogged(hex: Hex): Boolean {
        val visible = fogVisible ?: return false
        return hex !in visible
    }

    /**
     * Deposits are terrain, so explored memory keeps them on the board: hidden only on
     * never-seen hexes, dimmed on remembered-but-fogged ones. Leaks nothing — a hex in
     * [fogExplored] was fully seen once, and deposits never change afterwards.
     */
    private fun applyDepositFog(hex: Hex, piece: Piece) {
        val visible = fogVisible
        when {
            visible == null || hex in visible -> {
                piece.setHidden(false)
                piece.setDimmed(false)
            }
            hex in fogExplored -> {
                piece.setHidden(false)
                piece.setDimmed(true)
            }
            else -> piece.setHidden(true)
        }
    }

    /** Writes the tile's rendered color: logical color when visible, dark neutral in fog. */
    private fun applyTileColor(hex: Hex, te: TileEntity) {
        val visible = fogVisible
        if (te.sea) {
            // Sea has no per-tile params — fog picks which SHARED instance renders it.
            // (Sea is pre-discovered, so the never-seen band cannot occur.)
            val chosen = if (visible == null || hex in visible) waterVisible else waterExplored
            if (te.instance !== chosen) {
                val rm = filament.renderableManager
                val ri = rm.getInstance(te.entity)
                if (ri != 0) rm.setMaterialInstanceAt(ri, 0, chosen)
                te.instance = chosen
            }
            return
        }
        val c = when {
            visible == null || hex in visible -> te.color
            hex in fogExplored -> Palette.NEUTRAL * FOG_EXPLORED_FACTOR
            else -> Palette.NEUTRAL * FOG_HIDDEN_FACTOR
        }
        te.instance.setParameter("colorFrom", c.x, c.y, c.z)
        te.instance.setParameter("colorTo", c.x, c.y, c.z)
        te.instance.setParameter("waveRadius", 0f)
    }

    // ----- screen anchors for HUD labels/popups -----

    private var trackedAnchors: Set<Hex> = emptySet()
    private val _anchors = MutableStateFlow<Map<Hex, Float2>>(emptyMap())
    val anchors: StateFlow<Map<Hex, Float2>> = _anchors.asStateFlow()

    fun setTrackedAnchors(hexes: Set<Hex>) {
        trackedAnchors = hexes
    }

    var onTapMiss: (() -> Unit)? = null

    init {
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE

        for ((hex, tile) in initialState.tiles) {
            val cx = HexWorld.centerX(hex)
            val cz = HexWorld.centerZ(hex)
            minX = minOf(minX, cx); maxX = maxOf(maxX, cx)
            minZ = minOf(minZ, cz); maxZ = maxOf(maxZ, cz)
            createTile(hex, tile)
        }

        rig.targetX = (minX + maxX) / 2f
        rig.targetZ = (minZ + maxZ) / 2f
        boardSpanX = maxX - minX + 2f
        boardSpanZ = maxZ - minZ + 2f
        rig.boundsFromBoard(minX, maxX, minZ, maxZ)

        // Load only the art sets this game can show; absent civs stay unloaded.
        pieceMeshes.preload(
            initialState.players.mapTo(HashSet()) { it.civ } + Civilization.KINGDOM,
        )
        reconcile(initialState, log = false)
    }

    /** Builds the tile entity for [hex] exactly as construction always has. */
    private fun createTile(hex: Hex, tile: com.msa.fightandconquer.core.model.Tile): TileEntity {
        val cx = HexWorld.centerX(hex)
        val cz = HexWorld.centerZ(hex)
        val sea = tile.terrain == com.msa.fightandconquer.core.model.Terrain.SEA
        if (sea) hasSea = true
        val color = when {
            sea -> Palette.SEA
            else -> tile.owner?.let { Palette.faction(it.value) } ?: Palette.NEUTRAL
        }
        val raised = !sea && tile.owner != null
        val instance = if (sea) {
            waterVisible // shared: one animated instance for the whole ocean
        } else {
            materials.material("hexTile").createInstance().apply {
                setParameter("colorFrom", color.x, color.y, color.z)
                setParameter("colorTo", color.x, color.y, color.z)
                setParameter("tileCenter", cx, 0f, cz)
                setParameter("waveRadius", 0f)
                setParameter("waveSoftness", 0.18f)
            }
        }
        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(hexMesh.aabb)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, hexMesh.vertexBuffer, hexMesh.indexBuffer)
            .material(0, instance)
            .castShadows(!sea)
            .receiveShadows(true)
            .build(filament, entity)
        engine.scene.addEntity(entity)
        val y = when {
            sea -> -Primitives.SEA_SINK
            raised -> Primitives.CAPTURE_RAISE
            else -> 0f
        }
        val te = TileEntity(entity, instance, color, raised, y, sea)
        tiles[hex] = te
        setTileTransform(hex, te)
        return te
    }

    /** Removes a tile entity from the scene; the game path never needs this. */
    private fun destroyTile(hex: Hex) {
        val te = tiles.remove(hex) ?: return
        engine.scene.removeEntity(te.entity)
        filament.destroyEntity(te.entity)
        EntityManager.get().destroy(te.entity)
        // Sea tiles share the water instances, destroyed once in destroy().
        if (!te.sea) filament.destroyMaterialInstance(te.instance)
    }

    // ----- public surface -----

    /**
     * Frames of full-rate rendering left after the last interaction — input
     * response and settling UI must never render at the ambience rate.
     */
    private var wakeFrames = 10

    private fun wake() {
        wakeFrames = 10
    }

    /** True while pulsing highlight discs (captures, hints) are on screen. */
    private var pulsingShown = false

    /** Renderer throttle hook: full rate only while something moves or reacts. */
    override fun isBusy(): Boolean =
        wakeFrames > 0 || pulsingShown || rumbleTime >= 0f ||
            !animator.isIdle || !cameraAnimator.isIdle ||
            eventQueue.isNotEmpty() || pendingState != null

    fun tap(xPx: Float, yPx: Float) {
        wake()
        // Tap during playback fast-forwards instead of selecting (doc: skip-animation input).
        if (!animator.isIdle || eventQueue.isNotEmpty()) {
            skipAnimations()
            return
        }
        val viewport = engine.view.viewport
        val hex = picker.pick(xPx, yPx, viewport.width, viewport.height, rig)
        if (hex == null) {
            if (pickVoid) {
                voidPick(xPx, yPx, viewport.width, viewport.height)?.let {
                    onTap?.invoke(it)
                    return
                }
            }
            onTapMiss?.invoke() // tapping the void cancels the selection
            return
        }
        onTap?.invoke(hex)
    }

    /** Unconditional ray hit on the flat land plane, for painting where no tile is. */
    private fun voidPick(xPx: Float, yPx: Float, viewportW: Int, viewportH: Int): Hex? {
        if (viewportW <= 0 || viewportH <= 0) return null
        val (origin, dir) = rig.rayThrough(xPx, yPx, viewportW, viewportH)
        if (dir.y >= -1e-5f) return null
        val t = (Primitives.HEX_TOP_Y - origin.y) / dir.y
        if (t <= 0f) return null
        return HexWorld.worldToHex(origin.x + dir.x * t, origin.z + dir.z * t)
    }

    fun pan(dxPx: Float, dyPx: Float) {
        wake()
        cameraAnimator.cancelAll() // user input always beats a glide
        rig.pan(dxPx, dyPx, engine.view.viewport.height)
    }

    /** Smooth camera glide to a hex (units-left helper etc.). */
    fun jumpTo(hex: Hex, targetDistance: Float? = null) {
        val endX = HexWorld.centerX(hex).coerceIn(rig.minTargetX, rig.maxTargetX)
        val endZ = HexWorld.centerZ(hex).coerceIn(rig.minTargetZ, rig.maxTargetZ)
        val startX = rig.targetX
        val startZ = rig.targetZ
        val startD = rig.distance
        val endD = (targetDistance ?: rig.distance.coerceAtMost(12f)).coerceIn(rig.minDistance, rig.maxDistance)
        val dist = hypot(endX - startX, endZ - startZ)
        if (dist < 0.05f && abs(endD - startD) < 0.05f) return
        val duration = (0.15f + dist * 0.025f).coerceIn(0.25f, 0.45f)
        cameraAnimator.cancelAll()
        cameraAnimator.tween(duration, Easings::easeOutCubic) { t ->
            rig.targetX = startX + (endX - startX) * t
            rig.targetZ = startZ + (endZ - startZ) * t
            rig.distance = startD + (endD - startD) * t
        }
    }

    fun zoom(factor: Float) {
        wake()
        rig.zoomBy(factor)
    }

    /** Feed a new authoritative state and the events that produced it. */
    fun apply(state: GameState, events: List<GameEvent>) {
        latestState = state
        pendingState = state
        eventQueue.addAll(events)
    }

    // ----- editor surface (never called on the game path) -----

    /** Editor mode: a miss on real tiles falls back to the flat land plane, so empty
     *  hexes can be painted. Stays false in play — the game keeps tap-to-cancel. */
    var pickVoid: Boolean = false

    /**
     * Snap the scene to an editor state whose TILE SET may differ from the last one —
     * hexes appear, vanish and flip LAND↔SEA, which [reconcile] deliberately never
     * handles. Terrain flips rebuild the entity (different material, height, shadow);
     * everything on top rides the ordinary reconcile that follows. No events, no
     * animation, no correction logging: an edit is authoritative, not a discrepancy.
     */
    fun applyEditorState(state: GameState) {
        latestState = state
        tiles.keys.filterNot { it in state.tiles }.forEach { destroyTile(it) }
        for ((hex, tile) in state.tiles) {
            val existing = tiles[hex]
            val sea = tile.terrain == com.msa.fightandconquer.core.model.Terrain.SEA
            if (existing == null || existing.sea != sea) {
                if (existing != null) destroyTile(hex)
                createTile(hex, tile)
            }
        }
        refreshBoardBounds()
        reconcile(state, log = false)
        wake()
    }

    /** Re-derives camera bounds after the board's extent changed (editor only). */
    private fun refreshBoardBounds() {
        if (tiles.isEmpty()) return
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (hex in tiles.keys) {
            val cx = HexWorld.centerX(hex)
            val cz = HexWorld.centerZ(hex)
            minX = minOf(minX, cx); maxX = maxOf(maxX, cx)
            minZ = minOf(minZ, cz); maxZ = maxOf(maxZ, cz)
        }
        boardSpanX = maxX - minX + 2f
        boardSpanZ = maxZ - minZ + 2f
        rig.boundsFromBoard(minX, maxX, minZ, maxZ)
    }

    /**
     * Show selection + legal-move overlays: translucent discs hovering over tiles.
     * Colors: selected = white, move = white (dimmer), capture = warm red, merge = gold.
     * [fishingRange] marks the shoals a (would-be) fishery works: gold-sand, STATIC —
     * a pulsing set here would pin [isBusy] and cook an idle board (docs/rendering.md).
     */
    fun showHighlights(
        selected: Hex?,
        moves: Set<Hex>,
        captures: Set<Hex>,
        merges: Set<Hex>,
        hintFocus: Set<Hex> = emptySet(),
        fishingRange: Set<Hex> = emptySet(),
    ) {
        clearHighlights()
        highlightClock = 0f // pulse always starts bright: "these just lit up"
        // The campaign coach's ring goes down first, so a selection highlight on the same
        // hex reads on top of it — the hint is context, the selection is what you just did.
        for (hex in hintFocus) addHighlight(hex, 0.45f, 0.8f, 0.95f, 0.5f, pulse = true)
        // Range context under everything a tap just changed.
        for (hex in fishingRange) addHighlight(hex, 0.95f, 0.80f, 0.40f, 0.40f)
        selected?.let { addHighlight(it, 1f, 1f, 1f, 0.55f) }
        for (hex in moves) addHighlight(hex, 1f, 1f, 1f, 0.3f)
        for (hex in captures) addHighlight(hex, 0.95f, 0.45f, 0.35f, 0.5f, pulse = true)
        for (hex in merges) addHighlight(hex, 0.9f, 0.75f, 0.35f, 0.55f)
        pulsingShown = (0 until highlightsShown).any { highlightPool[it].pulse }
        wake()
    }

    fun clearHighlights() {
        for (i in 0 until highlightsShown) {
            val h = highlightPool[i]
            if (h.inScene) {
                engine.scene.removeEntity(h.entity)
                h.inScene = false
            }
            h.pulse = false
        }
        highlightsShown = 0
        pulsingShown = false
        wake() // redraw the cleared board promptly even when otherwise idle
    }

    private fun addHighlight(hex: Hex, r: Float, g: Float, b: Float, a: Float, pulse: Boolean = false) {
        if (hex !in tiles) return
        val h = if (highlightsShown < highlightPool.size) {
            highlightPool[highlightsShown]
        } else {
            val instance = materials.material("highlight").createInstance()
            val entity = EntityManager.get().create()
            RenderableManager.Builder(1)
                .boundingBox(highlightMesh.aabb)
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, highlightMesh.vertexBuffer, highlightMesh.indexBuffer)
                .material(0, instance)
                .castShadows(false)
                .receiveShadows(false)
                .build(filament, entity)
            HighlightEntity(entity, instance, inScene = false).also { highlightPool.add(it) }
        }
        highlightsShown++
        h.pulse = pulse
        h.rgba[0] = r; h.rgba[1] = g; h.rgba[2] = b; h.rgba[3] = a
        h.instance.setParameter("color", r, g, b, a)
        val tm = filament.transformManager
        var ti = tm.getInstance(h.entity)
        if (ti == 0) ti = tm.create(h.entity)
        tm.setTransform(
            ti,
            Transforms.translation(HexWorld.centerX(hex), tileTopY(hex) + 0.012f, HexWorld.centerZ(hex)),
        )
        if (!h.inScene) {
            engine.scene.addEntity(h.entity)
            h.inScene = true
        }
    }

    // ----- editor ghost ring: dim discs marking paintable empty hexes -----

    private class GhostEntity(val entity: Int, val instance: MaterialInstance, var inScene: Boolean)

    private val ghostPool = ArrayList<GhostEntity>()
    private var ghostsShown = 0

    /**
     * Marks empty hexes the editor can grow onto. A separate pool from highlights so
     * the brush cursor ([showHighlights]) never clears the ring. Pass empty to hide.
     */
    fun setGhosts(hexes: Collection<Hex>) {
        for (i in 0 until ghostsShown) {
            val g = ghostPool[i]
            if (g.inScene) {
                engine.scene.removeEntity(g.entity)
                g.inScene = false
            }
        }
        ghostsShown = 0
        for (hex in hexes) addGhost(hex)
        wake()
    }

    private fun addGhost(hex: Hex) {
        val g = if (ghostsShown < ghostPool.size) {
            ghostPool[ghostsShown]
        } else {
            val instance = materials.material("highlight").createInstance().apply {
                setParameter("color", 1f, 1f, 1f, GHOST_ALPHA)
            }
            val entity = EntityManager.get().create()
            RenderableManager.Builder(1)
                .boundingBox(highlightMesh.aabb)
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, highlightMesh.vertexBuffer, highlightMesh.indexBuffer)
                .material(0, instance)
                .castShadows(false)
                .receiveShadows(false)
                .build(filament, entity)
            GhostEntity(entity, instance, inScene = false).also { ghostPool.add(it) }
        }
        ghostsShown++
        val tm = filament.transformManager
        var ti = tm.getInstance(g.entity)
        if (ti == 0) ti = tm.create(g.entity)
        tm.setTransform(
            ti,
            // The hex has no tile yet: the ghost floats where a flat land top would be.
            Transforms.translation(HexWorld.centerX(hex), Primitives.HEX_TOP_Y + 0.012f, HexWorld.centerZ(hex)),
        )
        if (!g.inScene) {
            engine.scene.addEntity(g.entity)
            g.inScene = true
        }
    }

    /** Skip all pending animation (fast-forward to the latest state). */
    fun skipAnimations() {
        animator.finishAll()
        eventQueue.clear()
        pendingState?.let { reconcile(it) }
        pendingState = null
    }

    // ----- frame loop -----

    override fun onFrame(frameTimeNanos: Long, deltaSeconds: Float) {
        fitCameraOnce()
        if (deltaSeconds > 0f) {
            // Start the next queued beat(s) whenever the previous one finished.
            var guard = 0
            while (animator.isIdle && eventQueue.isNotEmpty() && guard++ < 128) {
                processEvent(eventQueue.removeFirst())
            }
            animator.update(deltaSeconds)
            cameraAnimator.update(deltaSeconds)
            if (animator.isIdle && eventQueue.isEmpty()) {
                pendingState?.let { reconcile(it) }
                pendingState = null
            }
            if (rumbleTime >= 0f) {
                rumbleTime += deltaSeconds
                val amp = 0.05f * exp(-9f * rumbleTime)
                rig.shake = Float3(0f, amp * sin(45f * rumbleTime), 0f)
                if (amp < 0.001f) {
                    rumbleTime = -1f
                    rig.shake = Float3(0f, 0f, 0f)
                }
            }
            // Water shimmer: one uniform write per fog band, regardless of sea size.
            if (hasSea) {
                waterTime += deltaSeconds
                // Wrap on the bands' common period (20pi) — keeps the shader arg small
                // for mediump sin() without a visible jump.
                if (waterTime > WATER_PERIOD) waterTime -= WATER_PERIOD
                waterVisible.setParameter("time", waterTime)
                waterExplored.setParameter("time", waterTime)
                // Idle boat bob: a view-only yOffset ripple (reconcile ignores
                // yOffset, so the zero-correction gate is untouched). Skipped
                // while a piece animates (xz set) or the queue is playing.
                if (animator.isIdle) {
                    for ((id, piece) in unitPieces) {
                        if (isBoatKind(piece.kind) && piece.xz == null && !piece.hidden) {
                            piece.yOffset = 0.008f * sin(waterTime * 1.3f + (id.value % 7) * 0.9f)
                            piece.updateTransform()
                        }
                    }
                }
            }
            // Capture-highlight pulse (a handful of uniform writes at most).
            highlightClock += deltaSeconds
            val pulseAlpha = 0.72f + 0.28f * sin(highlightClock * 7f)
            for (i in 0 until highlightsShown) {
                val h = highlightPool[i]
                if (h.pulse) {
                    h.instance.setParameter("color", h.rgba[0], h.rgba[1], h.rgba[2], h.rgba[3] * pulseAlpha)
                }
            }
        }
        rig.update(engine.camera)
        publishAnchors()
        if (wakeFrames > 0) wakeFrames--
    }

    /** Projects tracked hexes to screen px; publishes only on movement (quarter-px quantized). */
    private fun publishAnchors() {
        if (trackedAnchors.isEmpty()) {
            if (_anchors.value.isNotEmpty()) _anchors.value = emptyMap()
            return
        }
        val viewport = engine.view.viewport
        if (viewport.width <= 0 || viewport.height <= 0) return
        val out = HashMap<Hex, Float2>(trackedAnchors.size)
        for (hex in trackedAnchors) {
            if (hex !in tiles) continue
            val projected = rig.project(
                Float3(HexWorld.centerX(hex), tileTopY(hex) + ANCHOR_LIFT, HexWorld.centerZ(hex)),
                viewport.width,
                viewport.height,
            ) ?: continue
            out[hex] = Float2(round(projected.x * 4f) / 4f, round(projected.y * 4f) / 4f)
        }
        if (out != _anchors.value) _anchors.value = out
    }

    /**
     * Frames the whole board once the viewport aspect is known (portrait screens make
     * the horizontal FOV the binding constraint — a single-axis fit cuts the sides off).
     */
    private fun fitCameraOnce() {
        if (cameraFitted) return
        val viewport = engine.view.viewport
        if (viewport.width <= 0 || viewport.height <= 0) return
        val aspect = viewport.width.toFloat() / viewport.height
        val tanHalf = kotlin.math.tan(Math.toRadians(RenderEngine.FOV_DEGREES / 2).toFloat())
        val fitZ = boardSpanZ * 0.5f / tanHalf
        val fitX = boardSpanX * 0.5f / (tanHalf * aspect)
        val distance = maxOf(fitZ, fitX) * 1.1f
        rig.maxDistance = maxOf(40f, distance * 1.3f)
        rig.distance = distance.coerceIn(rig.minDistance, rig.maxDistance)
        cameraFitted = true
    }

    // ----- event animation -----

    private fun processEvent(event: GameEvent) {
        when (event) {
            is GameEvent.UnitSpawned -> {
                val piece = createPiece(pieceMeshes.unitKind(event.unit), event.unit.hex, event.unit.owner.value)
                unitPieces[event.unit.id] = piece
                piece.setDimmed(latestState.units[event.unit.id]?.spent == true)
                spawnBounce(piece)
                if (!isFogged(event.unit.hex)) rumbleTime = 0f // no juice for unseen spawns
            }

            is GameEvent.UnitMoved -> {
                val piece = unitPieces[event.unit] ?: return
                if (isBoatKind(piece.kind)) {
                    // Boats SAIL: flat glide along open water, never a hop.
                    val path = seaPath(event.from, event.to)
                    if (path != null) {
                        hopAlong(piece, event.unit, path, glide = true)
                    } else {
                        hop(piece, event.from, event.to, height = 0f, unitId = event.unit)
                    }
                    return
                }
                val owner = latestState.units[event.unit]?.owner ?: latestState.tiles[event.to]?.owner
                val path = owner?.let { ownedPath(event.from, event.to, it) }
                if (path != null) {
                    hopAlong(piece, event.unit, path)
                } else {
                    hop(piece, event.from, event.to, unitId = event.unit)
                }
            }

            is GameEvent.HexCaptured -> {
                val te = tiles[event.hex] ?: return
                // A captured building swaps art when the new owner's civ renders a
                // different set — done here so the reconcile that follows the queue
                // sees the swap already made (never a correction). Tint rules are
                // unchanged: createPiece re-derives colors from the same roles.
                buildingPieces[event.hex]?.let { standing ->
                    val newCiv = artCivFor(event.newOwner.value, standing.kind)
                    if (standing.civ != newCiv) {
                        destroyPiece(standing)
                        val fresh = createPiece(standing.kind, event.hex, event.newOwner.value)
                        if (fresh.kind == PieceKind.BRIDGE) {
                            fresh.yaw = bridgeYaw(event.hex)
                            fresh.updateTransform()
                        }
                        buildingPieces[event.hex] = fresh
                    }
                }
                // Sea stays water: a captured bridge hex shows ownership on the piece,
                // never on the tile (no raise, no wave).
                if (te.sea) return
                val color = Palette.faction(event.newOwner.value)
                if (isFogged(event.hex)) {
                    // Fogged capture: update logical state silently — no wave, no reveal.
                    te.color = color
                    te.raised = true
                    te.y = Primitives.CAPTURE_RAISE
                    applyTileColor(event.hex, te)
                    setTileTransform(event.hex, te)
                    refreshPiecesOn(event.hex)
                    return
                }
                te.raised = true
                te.instance.setParameter("colorTo", color.x, color.y, color.z)
                val startY = te.y
                animator.tween(0.3f, Easings::easeOutCubic, onEnd = {
                    te.instance.setParameter("colorFrom", color.x, color.y, color.z)
                    te.instance.setParameter("waveRadius", 0f)
                    te.color = color
                }) { t ->
                    te.y = startY + (Primitives.CAPTURE_RAISE - startY) * t
                    setTileTransform(event.hex, te)
                    te.instance.setParameter("waveRadius", t * WAVE_MAX_RADIUS)
                    refreshPiecesOn(event.hex)
                }
            }

            is GameEvent.UnitDied -> {
                val piece = unitPieces.remove(event.unit) ?: return
                val atSea = latestState.tiles[event.hex]?.terrain ==
                    com.msa.fightandconquer.core.model.Terrain.SEA
                // The drowned go deeper and slower — and leave no gravestone.
                sinkAway(piece, duration = if (atSea) 0.4f else 0.25f, depth = if (atSea) 0.3f else 0.1f) {
                    if (event.cause != DeathCause.KILLED && event.cause != DeathCause.SUNK && !atSea) {
                        val grave = createPiece(PieceKind.GRAVESTONE, event.hex, null)
                        floraPieces[event.hex] = grave
                        spawnBounce(grave, duration = 0.25f)
                    }
                }
            }

            is GameEvent.UnitsMerged -> {
                val consumed = unitPieces.remove(event.consumed)
                val target = unitPieces[event.into.id]
                val finish = {
                    // Swap the target piece for the upgraded tier with a bounce.
                    target?.let { destroyPiece(it) }
                    val upgraded = createPiece(
                        pieceMeshes.unitKind(event.into),
                        event.into.hex,
                        event.into.owner.value,
                    )
                    unitPieces[event.into.id] = upgraded
                    upgraded.setDimmed(latestState.units[event.into.id]?.spent == true)
                    spawnBounce(upgraded)
                }
                if (consumed != null) {
                    val from = consumed.hex
                    val to = event.into.hex
                    consumed.animFrom = from
                    consumed.setHidden(FogRules.segmentHidden(fogVisible, from, to))
                    val startYaw = consumed.yaw
                    animator.tween(0.25f, Easings::easeOutCubic, onEnd = {
                        destroyPiece(consumed)
                        finish()
                    }) { t ->
                        consumed.xz = lerpHex(from, to, t)
                        consumed.yOffset = Easings.hop(t) * 0.3f
                        consumed.scale = 1f - 0.5f * t
                        faceHeading(consumed, startYaw, from, to, t)
                        consumed.updateTransform()
                    }
                } else {
                    finish()
                }
            }

            is GameEvent.BuildingBuilt -> {
                // A deposit marker shows only while its hex has no building on it,
                // and an overwritten piece must die now or it orphans in the scene.
                depositPieces.remove(event.hex)?.let { destroyPiece(it) }
                buildingPieces.remove(event.hex)?.let { destroyPiece(it) }
                val owner = latestState.tiles[event.hex]?.owner?.value
                val piece = createPiece(buildingKind(event.building), event.hex, owner)
                if (piece.kind == PieceKind.BRIDGE) piece.yaw = bridgeYaw(event.hex)
                buildingPieces[event.hex] = piece
                spawnBounce(piece)
                if (!isFogged(event.hex)) rumbleTime = 0f // no juice for unseen builds
            }

            is GameEvent.BuildingDestroyed -> {
                val piece = buildingPieces.remove(event.hex) ?: return
                sinkAway(piece)
            }

            is GameEvent.BuildingRotated -> {
                val piece = buildingPieces[event.hex] ?: return
                val from = piece.yaw
                val to = bridgeYaw(event.hex) // latestState already holds the new axis
                animator.tween(0.25f, Easings::easeOutCubic, onEnd = {
                    piece.yaw = to // exact value, so reconcile sees no drift
                    piece.updateTransform()
                }) { t ->
                    piece.yaw = PieceHeadings.lerpAngle(from, to, t)
                    piece.updateTransform()
                }
            }

            is GameEvent.TreeGrown -> growTree(event.hex, replaceGrave = true)

            is GameEvent.TreeSpread -> growTree(event.to, replaceGrave = false)

            is GameEvent.TreeCleared -> {
                val piece = floraPieces.remove(event.hex) ?: return
                sinkAway(piece)
            }

            is GameEvent.GravestoneTrampled -> {
                val piece = floraPieces.remove(event.hex) ?: return
                sinkAway(piece, duration = 0.15f)
            }

            is GameEvent.CapitalMoved -> {
                buildingPieces.remove(event.from)?.let { sinkAway(it) }
                if (event.to != event.from) {
                    val owner = latestState.tiles[event.to]?.owner?.value
                    // Clear any flora piece the relocation displaced.
                    floraPieces.remove(event.to)?.let { destroyPiece(it) }
                    val piece = createPiece(PieceKind.CAPITAL, event.to, owner)
                    buildingPieces[event.to] = piece
                    spawnBounce(piece)
                }
            }

            is GameEvent.UnitEmbarked -> {
                val piece = unitPieces.remove(event.unit) ?: return
                // Walk aboard, shrink into the hold.
                piece.hex = event.at
                piece.animFrom = event.from
                piece.setHidden(FogRules.segmentHidden(fogVisible, event.from, event.at))
                val from = event.from
                val startYaw = piece.yaw
                animator.tween(0.25f, Easings::easeOutCubic, onEnd = { destroyPiece(piece) }) { t ->
                    piece.xz = lerpHex(from, event.at, t)
                    piece.yOffset = Easings.hop(t) * 0.25f
                    piece.scale = 1f - 0.6f * t
                    faceHeading(piece, startYaw, from, event.at, t)
                    piece.updateTransform()
                }
            }

            is GameEvent.UnitDisembarked -> {
                val landed = createPiece(
                    pieceMeshes.unitKind(event.unit),
                    event.to,
                    event.unit.owner.value,
                )
                unitPieces[event.unit.id] = landed
                landed.setDimmed(latestState.units[event.unit.id]?.spent == true)
                val boatHex = unitPieces[event.transport]?.hex ?: event.to
                hop(landed, boatHex, event.to, unitId = event.unit.id)
            }

            is GameEvent.Bombarded -> {
                // The broadside itself is camera juice; the kill/demolition each
                // arrive as their own events right after.
                if (!isFogged(event.target)) rumbleTime = 0f
            }

            is GameEvent.TurnStarted -> {
                // Refresh spent-dim for the whole board immediately (undims the new army).
                for ((id, piece) in unitPieces) {
                    piece.setDimmed(latestState.units[id]?.spent == true)
                }
            }

            // HUD-level events: no board animation (diplomacy stays off the board, and a
            // campaign story beat announces itself in a toast — its spawns arrive as
            // ordinary UnitSpawned events that this same loop already animates).
            is GameEvent.ActionRejected, is GameEvent.Bankruptcy,
            is GameEvent.PlayerEliminated, is GameEvent.GameOver,
            is GameEvent.PactProposed, is GameEvent.PactAccepted, is GameEvent.PactDeclined,
            is GameEvent.PactExpired, is GameEvent.PactProposalExpired,
            is GameEvent.PactBroken, is GameEvent.TributeSent,
            is GameEvent.ScriptFired, is GameEvent.RefundPaid,
            -> Unit
        }
    }

    private fun growTree(hex: Hex, replaceGrave: Boolean) {
        if (replaceGrave) floraPieces.remove(hex)?.let { destroyPiece(it) }
        val tree = createPiece(PieceKind.TREE, hex, null)
        floraPieces[hex] = tree
        tree.scale = 0f
        tree.updateTransform()
        animator.tween(0.4f, Easings::easeOutBack) { t ->
            tree.scale = t
            tree.updateTransform()
        }
    }

    private fun spawnBounce(piece: Piece, duration: Float = 0.35f) {
        piece.scale = 0f
        piece.updateTransform()
        animator.tween(duration, Easings::easeOutBack) { t ->
            piece.scale = t
            piece.updateTransform()
        }
    }

    private fun hop(piece: Piece, from: Hex, to: Hex, height: Float = 0.3f, unitId: UnitId? = null) {
        piece.hex = to
        piece.animFrom = from
        piece.setHidden(FogRules.segmentHidden(fogVisible, from, to))
        val startYaw = piece.yaw
        animator.tween(0.25f, Easings::easeOutCubic, onEnd = {
            piece.animFrom = null
            piece.xz = null
            piece.yOffset = 0f
            piece.updateTransform()
            piece.setHidden(isFogged(to))
            unitId?.let { piece.setDimmed(latestState.units[it]?.spent == true) }
        }) { t ->
            piece.xz = lerpHex(from, to, t)
            piece.yOffset = Easings.hop(t) * height
            faceHeading(piece, startYaw, from, to, t)
            piece.updateTransform()
        }
    }

    /**
     * BFS shortest path from..to over tiles owned by [owner] in the POST-move state
     * (a captured destination is already owned by the mover then). Null when a plain
     * direct hop should be used (adjacent, unreachable, or degenerate).
     */
    private fun ownedPath(from: Hex, to: Hex, owner: com.msa.fightandconquer.core.model.PlayerId): List<Hex>? {
        val canEnter: (Hex) -> Boolean = { h -> h == from || latestState.tiles[h]?.owner == owner }
        if (!canEnter(to)) return null
        return bfsPath(from, to, canEnter)
    }

    /**
     * BFS shortest path for an animation, or null when the plain direct segment
     * should be used (identical/adjacent endpoints, unreachable, or too long).
     */
    private fun bfsPath(from: Hex, to: Hex, canEnter: (Hex) -> Boolean): List<Hex>? {
        if (from == to) return null
        val parent = HashMap<Hex, Hex>()
        val queue = ArrayDeque<Hex>().apply { add(from) }
        val visited = HashSet<Hex>().apply { add(from) }
        var reached = false
        while (queue.isNotEmpty() && visited.size < 512 && !reached) {
            val current = queue.removeFirst()
            com.msa.fightandconquer.core.hex.HexMath.forEachNeighbor(current) { n ->
                if (!reached && n !in visited && canEnter(n)) {
                    visited.add(n)
                    parent[n] = current
                    if (n == to) reached = true else queue.add(n)
                }
            }
        }
        if (!reached) return null
        val path = ArrayList<Hex>()
        var h = to
        while (true) {
            path.add(h)
            h = parent[h] ?: break
        }
        path.reverse()
        return if (path.size in 3..MAX_PATH_LEN) path else null // 2 = plain hop/glide
    }

    /**
     * Chained per-hex hops: mid segments linear (continuous run), final segment
     * eases out. [glide] flattens the arc entirely — boats sail, they don't hop.
     */
    private fun hopAlong(piece: Piece, unitId: UnitId, path: List<Hex>, glide: Boolean = false) {
        val segments = path.size - 1
        val perHex = minOf(0.16f, 0.9f / segments)
        fun runSegment(index: Int) {
            val a = path[index]
            val b = path[index + 1]
            val last = index == segments - 1
            piece.hex = b
            piece.animFrom = a
            // Fog: render only the segments the viewer can fully see — an enemy
            // marching deep through the murk stays unseen the whole way.
            piece.setHidden(FogRules.segmentHidden(fogVisible, a, b))
            val yDelta = tileTopY(a) - tileTopY(b)
            val height = if (glide) 0f else if (last) 0.3f else 0.2f
            val startYaw = piece.yaw
            animator.tween(perHex, if (last) Easings::easeOutCubic else Easings::linear, onEnd = {
                if (last) {
                    piece.animFrom = null
                    piece.xz = null
                    piece.yOffset = 0f
                    piece.updateTransform()
                    piece.setHidden(isFogged(piece.hex))
                    piece.setDimmed(latestState.units[unitId]?.spent == true)
                } else {
                    runSegment(index + 1)
                }
            }) { t ->
                piece.xz = lerpHex(a, b, t)
                piece.yOffset = Easings.hop(t) * height + (1f - t) * yDelta
                faceHeading(piece, startYaw, a, b, t)
                piece.updateTransform()
            }
        }
        runSegment(0)
    }

    /** BFS shortest path over open water for the sail animation (null = direct glide). */
    private fun seaPath(from: Hex, to: Hex): List<Hex>? = bfsPath(from, to) { h ->
        h == from || h == to ||
            latestState.tiles[h]?.terrain == com.msa.fightandconquer.core.model.Terrain.SEA
    }

    private fun sinkAway(
        piece: Piece,
        duration: Float = 0.25f,
        depth: Float = 0.1f,
        onDone: (() -> Unit)? = null,
    ) {
        animator.tween(duration, Easings::easeInCubic, onEnd = {
            destroyPiece(piece)
            onDone?.invoke()
        }) { t ->
            piece.scale = 1f - t
            piece.yOffset = -depth * t
            piece.updateTransform()
        }
    }

    private fun lerpHex(from: Hex, to: Hex, t: Float): Pair<Float, Float> {
        val x = HexWorld.centerX(from) + (HexWorld.centerX(to) - HexWorld.centerX(from)) * t
        val z = HexWorld.centerZ(from) + (HexWorld.centerZ(to) - HexWorld.centerZ(from)) * t
        return x to z
    }

    // ----- piece plumbing -----

    private fun colorFor(role: ColorRole, ownerIndex: Int?): Float3 = when (role) {
        ColorRole.FACTION -> ownerIndex?.let { Palette.faction(it) } ?: Palette.PIECE_NEUTRAL
        ColorRole.GOLD -> Palette.GOLD
        ColorRole.TREE_FOLIAGE -> Palette.TREE
        ColorRole.TRUNK -> Palette.TRUNK
        ColorRole.STONE -> Palette.STONE
        ColorRole.PIP -> Palette.INK
    }

    /** The owner's civilization; null owner (neutral pieces, deposits) renders Kingdom. */
    private fun civFor(ownerIndex: Int?): Civilization =
        ownerIndex?.let { latestState.players.getOrNull(it)?.civ } ?: Civilization.KINGDOM

    /** The art identity for a piece: collapses fallback shares onto KINGDOM. */
    private fun artCivFor(ownerIndex: Int?, kind: PieceKind): Civilization =
        pieceMeshes.artCivFor(civFor(ownerIndex), kind)

    private fun createPiece(kind: PieceKind, hex: Hex, ownerIndex: Int?): Piece {
        val civ = artCivFor(ownerIndex, kind)
        val parts = pieceMeshes.partsFor(civ, kind)
        val pieceMaterial = materials.material("piece")
        val entities = IntArray(parts.size)
        val instances = ArrayList<MaterialInstance>(parts.size)
        parts.forEachIndexed { index, part ->
            val instance = pieceMaterial.createInstance().apply {
                val c = colorFor(part.role, ownerIndex)
                setParameter("baseColor", c.x, c.y, c.z)
                setParameter("roughness", 0.85f)
            }
            val entity = EntityManager.get().create()
            RenderableManager.Builder(1)
                .boundingBox(part.mesh.aabb)
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, part.mesh.vertexBuffer, part.mesh.indexBuffer)
                .material(0, instance)
                .castShadows(true)
                .receiveShadows(true)
                .build(filament, entity)
            engine.scene.addEntity(entity)
            entities[index] = entity
            instances.add(instance)
        }
        return Piece(kind, civ, entities, instances, parts.map { it.role }, ownerIndex, hex)
            .also {
                it.updateTransform()
                // Fog: hide in the same pass so a fogged piece never flashes for a frame.
                it.setHidden(isFogged(hex))
            }
    }

    private fun destroyPiece(piece: Piece) {
        for (entity in piece.entities) {
            engine.scene.removeEntity(entity)
            filament.destroyEntity(entity)
            EntityManager.get().destroy(entity)
        }
        piece.instances.forEach { filament.destroyMaterialInstance(it) }
    }

    /** Kinds that sail and bob: the sea-glide and idle-bob gates key off this. */
    private fun isBoatKind(kind: PieceKind): Boolean =
        kind == PieceKind.BOAT || kind == PieceKind.WARSHIP || kind == PieceKind.FISHING_BOAT

    private fun buildingKind(building: Building): PieceKind = when (building) {
        Building.CAPITAL -> PieceKind.CAPITAL
        Building.FARM -> PieceKind.FARM
        Building.TOWER -> PieceKind.TOWER
        Building.STRONG_TOWER -> PieceKind.STRONG_TOWER
        Building.MINE -> PieceKind.MINE
        Building.MARKET -> PieceKind.MARKET
        Building.LUMBER_CAMP -> PieceKind.LUMBER_CAMP
        Building.WATCHTOWER -> PieceKind.WATCHTOWER
        Building.PORT -> PieceKind.PORT
        Building.FISHERY -> PieceKind.FISHERY
        Building.BRIDGE -> PieceKind.BRIDGE
    }

    /**
     * A bridge deck (authored along Z) aims along the player-stored orientation,
     * or auto-aligns with the chain's through-axis — a pure function of the board
     * (see [PieceHeadings.bridgeYaw]), so create and reconcile always agree and
     * the yaw never counts as a correction.
     */
    private fun bridgeYaw(hex: Hex): Float = PieceHeadings.bridgeYaw(
        hex,
        latestState.tiles[hex]?.bridgeOrientation,
    ) { n ->
        val t = latestState.tiles[n]
        t != null && (
            t.terrain == com.msa.fightandconquer.core.model.Terrain.LAND ||
                t.building == Building.BRIDGE
            )
    }

    /**
     * Turns [piece] toward the travel direction [from] → [to] over the first
     * [HEADING_TURN_FRACTION] of a motion tween ([t] is the tween's progress).
     * Shortest arc, so a reversal is a 180° about-face, never a 350° spin.
     */
    private fun faceHeading(piece: Piece, startYaw: Float, from: Hex, to: Hex, t: Float) {
        if (from == to) return
        val target = PieceHeadings.headingYaw(from, to)
        piece.yaw = PieceHeadings.lerpAngle(startYaw, target, minOf(1f, t / HEADING_TURN_FRACTION))
    }

    /**
     * The RENDERED tile height: the logical raise ([TileEntity.y], which
     * reconcile diffs against) is suppressed inside the fog — ownership is not
     * terrain, and a hex visibly rising in the murk would announce an unseen
     * capture. Everything placed on tile tops (pieces, auras, highlights) reads
     * height through here, so the flattened rim stays consistent.
     */
    private fun renderedTileY(hex: Hex, te: TileEntity): Float = if (isFogged(hex)) 0f else te.y

    private fun tileTopY(hex: Hex): Float {
        val te = tiles[hex] ?: return Primitives.HEX_HEIGHT
        return renderedTileY(hex, te) + Primitives.HEX_HEIGHT
    }

    private fun setTileTransform(hex: Hex, te: TileEntity) {
        val tm = filament.transformManager
        var instance = tm.getInstance(te.entity)
        if (instance == 0) instance = tm.create(te.entity)
        tm.setTransform(
            instance,
            Transforms.translation(HexWorld.centerX(hex), renderedTileY(hex, te), HexWorld.centerZ(hex)),
        )
    }

    private fun refreshPiecesOn(hex: Hex) {
        buildingPieces[hex]?.updateTransform()
        floraPieces[hex]?.updateTransform()
        depositPieces[hex]?.updateTransform()
        for (piece in unitPieces.values) {
            if (piece.hex == hex && piece.xz == null) piece.updateTransform()
        }
    }

    // ----- defense auras -----

    /**
     * Ring decals on every tile covered by a tower/castle/capital or an archer's
     * aura (self + owned neighbors), so protection is visible before you bump into
     * it. Alpha scales with the best defense level covering the tile.
     */
    private fun refreshAuras(state: GameState) {
        // hex -> best covering defense level
        val covered = HashMap<Hex, Int>()
        for ((hex, tile) in state.tiles) {
            val owner = tile.owner ?: continue
            val defense = when (tile.building) {
                Building.TOWER -> state.config.rules.towerDefense
                Building.STRONG_TOWER -> state.config.rules.strongTowerDefense
                Building.CAPITAL -> state.config.rules.capitalDefense
                else -> continue
            }
            // A source inside the fog contributes nothing — not even to a
            // visible rim hex, or its ring would betray the hidden building.
            if (FogRules.auraSourceHidden(fogVisible, hex)) continue
            covered.merge(hex, defense, ::maxOf)
            com.msa.fightandconquer.core.hex.HexMath.forEachNeighbor(hex) { n ->
                if (state.tiles[n]?.owner == owner) covered.merge(n, defense, ::maxOf)
            }
        }
        for (unit in state.units.values) {
            if (unit.type != com.msa.fightandconquer.core.model.UnitType.ARCHER) continue
            // Same for archers — their ring moving at the fog rim would track
            // an unseen unit's manoeuvres live.
            if (FogRules.auraSourceHidden(fogVisible, unit.hex)) continue
            val aura = state.config.rules.archerAuraDefense
            covered.merge(unit.hex, aura, ::maxOf)
            com.msa.fightandconquer.core.hex.HexMath.forEachNeighbor(unit.hex) { n ->
                if (state.tiles[n]?.owner == unit.owner) covered.merge(n, aura, ::maxOf)
            }
        }

        // Hide previous, then show current from the pool.
        for (i in 0 until aurasShown) {
            val aura = auraPool[i]
            if (aura.inScene) {
                engine.scene.removeEntity(aura.entity)
                aura.inScene = false
            }
        }
        aurasShown = 0
        for ((hex, level) in covered) {
            if (isFogged(hex)) continue // a tower ring deep in fog would leak its presence
            val aura = if (aurasShown < auraPool.size) {
                auraPool[aurasShown]
            } else {
                val instance = materials.material("highlight").createInstance()
                val entity = EntityManager.get().create()
                RenderableManager.Builder(1)
                    .boundingBox(auraMesh.aabb)
                    .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, auraMesh.vertexBuffer, auraMesh.indexBuffer)
                    .material(0, instance)
                    .castShadows(false)
                    .receiveShadows(false)
                    .build(filament, entity)
                AuraEntity(entity, instance, inScene = false).also { auraPool.add(it) }
            }
            aurasShown++
            val alpha = 0.30f + 0.08f * (level - 1)
            aura.instance.setParameter("color", 0.56f, 0.64f, 0.71f, alpha)
            val tm = filament.transformManager
            var ti = tm.getInstance(aura.entity)
            if (ti == 0) ti = tm.create(aura.entity)
            // Below the highlight discs (+0.012) but above the tile top: no z-fighting.
            tm.setTransform(
                ti,
                Transforms.translation(HexWorld.centerX(hex), tileTopY(hex) + 0.006f, HexWorld.centerZ(hex)),
            )
            if (!aura.inScene) {
                engine.scene.addEntity(aura.entity)
                aura.inScene = true
            }
        }
    }

    // ----- reconcile: the self-healing safety net -----

    private fun reconcile(state: GameState, log: Boolean = true) {
        var corrections = 0

        for ((hex, tile) in state.tiles) {
            val te = tiles[hex] ?: continue
            if (te.sea) {
                // Water is static geometry: fixed sink, never raised, shared material.
                // Fog-band swaps are view-only and handled by setFog/applyTileColor.
                applyTileColor(hex, te)
                continue
            }
            val color = tile.owner?.let { Palette.faction(it.value) } ?: Palette.NEUTRAL
            val raised = tile.owner != null
            val y = if (raised) Primitives.CAPTURE_RAISE else 0f
            if (te.color != color || te.raised != raised || te.y != y) {
                corrections++
                te.color = color
                te.raised = raised
                te.y = y
                applyTileColor(hex, te) // fog-aware: renders the logical color only when visible
                setTileTransform(hex, te)
            }
        }

        // Units.
        val staleUnits = unitPieces.keys.filter { it !in state.units }
        staleUnits.forEach { id -> unitPieces.remove(id)?.let { destroyPiece(it); corrections++ } }
        for (unit in state.units.values) {
            val expectedKind = pieceMeshes.unitKind(unit)
            val piece = unitPieces[unit.id]
            // A differing art civ is an identity change like a kind change: recreate.
            if (piece == null || piece.kind != expectedKind ||
                piece.civ != artCivFor(unit.owner.value, expectedKind)
            ) {
                piece?.let { destroyPiece(it) }
                unitPieces[unit.id] = createPiece(expectedKind, unit.hex, unit.owner.value)
                if (piece != null || pendingState != null) corrections++
            } else if (piece.hex != unit.hex || piece.scale != 1f || piece.xz != null) {
                piece.hex = unit.hex
                piece.scale = 1f
                piece.yOffset = 0f
                piece.xz = null
                piece.animFrom = null
                piece.updateTransform()
                corrections++
            }
            // Dim and fog are view annotations events intentionally defer — sync silently.
            unitPieces.getValue(unit.id).setDimmed(unit.spent)
            unitPieces.getValue(unit.id).setHidden(isFogged(unit.hex))
        }

        // Buildings + flora, per tile.
        reconcileProps(state, buildingPieces, corrections) { tile -> tile.building?.let { buildingKind(it) } }
            .also { corrections = it }
        reconcileProps(state, floraPieces, corrections) { tile ->
            when (tile.flora) {
                is Flora.Tree -> PieceKind.TREE
                is Flora.Gravestone -> PieceKind.GRAVESTONE
                null -> null
            }
        }.also { corrections = it }

        // Deposits: static terrain with no events, so presence changes (a building
        // covering the marker, initial creation) are expected here — never corrections.
        syncDeposits(state)

        // Keep pieces glued to final tile heights.
        for (piece in unitPieces.values) piece.updateTransform()
        for (piece in buildingPieces.values) piece.updateTransform()
        for (piece in floraPieces.values) piece.updateTransform()
        for (piece in depositPieces.values) piece.updateTransform()

        refreshAuras(state)

        if (log && corrections > 0) {
            Log.w(TAG, "reconcile corrected $corrections discrepancies (events should have covered these)")
        }
    }

    /** A deposit marker shows only while its hex has no building on it. */
    private fun depositKind(tile: com.msa.fightandconquer.core.model.Tile): PieceKind? =
        if (tile.building != null) {
            null
        } else {
            when (tile.deposit) {
                com.msa.fightandconquer.core.model.Deposit.GOLD_VEIN -> PieceKind.GOLD_VEIN
                com.msa.fightandconquer.core.model.Deposit.FERTILE -> PieceKind.FERTILE
                com.msa.fightandconquer.core.model.Deposit.FISH_SHOAL -> PieceKind.FISH_SHOAL
                null -> null
            }
        }

    private fun syncDeposits(state: GameState) {
        val stale = depositPieces.keys.filter { hex -> state.tiles[hex]?.let(::depositKind) == null }
        stale.forEach { hex -> depositPieces.remove(hex)?.let { destroyPiece(it) } }
        for ((hex, tile) in state.tiles) {
            val kind = depositKind(tile) ?: continue
            var piece = depositPieces[hex]
            if (piece == null || piece.kind != kind) {
                piece?.let { destroyPiece(it) }
                piece = createPiece(kind, hex, null)
                depositPieces[hex] = piece
            }
            applyDepositFog(hex, piece)
        }
    }

    private inline fun reconcileProps(
        state: GameState,
        pieces: HashMap<Hex, Piece>,
        startCorrections: Int,
        expected: (com.msa.fightandconquer.core.model.Tile) -> PieceKind?,
    ): Int {
        var corrections = startCorrections
        val stale = pieces.keys.filter { hex -> state.tiles[hex]?.let(expected) == null }
        stale.forEach { hex -> pieces.remove(hex)?.let { destroyPiece(it); corrections++ } }
        for ((hex, tile) in state.tiles) {
            val kind = expected(tile) ?: continue
            val piece = pieces[hex]
            // An owner whose civ renders different art (captures, elimination-neutral
            // bridges) is an identity change like a kind change: recreate.
            if (piece == null || piece.kind != kind ||
                piece.civ != artCivFor(tile.owner?.value, kind)
            ) {
                piece?.let { destroyPiece(it) }
                val fresh = createPiece(kind, hex, tile.owner?.value)
                if (kind == PieceKind.BRIDGE) {
                    fresh.yaw = bridgeYaw(hex)
                    fresh.updateTransform()
                }
                pieces[hex] = fresh
                if (piece != null) corrections++
            } else if (piece.scale != 1f) {
                piece.scale = 1f
                piece.yOffset = 0f
                piece.updateTransform()
                corrections++
            }
            val current = pieces.getValue(hex)
            // Bridge yaw is a pure function of state (player axis or auto-align),
            // so existing spans re-aim here as their chain grows and rotations
            // survive undo/load — silently, like fog (never a correction).
            if (current.kind == PieceKind.BRIDGE) {
                val expectedYaw = bridgeYaw(hex)
                if (current.yaw != expectedYaw) {
                    current.yaw = expectedYaw
                    current.updateTransform()
                }
            }
            // Fog is a view annotation — sync silently (never a correction).
            current.setHidden(isFogged(hex))
        }
        return corrections
    }

    override fun destroy() {
        (unitPieces.values + buildingPieces.values + floraPieces.values + depositPieces.values)
            .forEach { destroyPiece(it) }
        unitPieces.clear(); buildingPieces.clear(); floraPieces.clear(); depositPieces.clear()
        for (h in highlightPool) {
            if (h.inScene) engine.scene.removeEntity(h.entity)
            filament.destroyEntity(h.entity)
            EntityManager.get().destroy(h.entity)
            filament.destroyMaterialInstance(h.instance)
        }
        highlightPool.clear()
        for (g in ghostPool) {
            if (g.inScene) engine.scene.removeEntity(g.entity)
            filament.destroyEntity(g.entity)
            EntityManager.get().destroy(g.entity)
            filament.destroyMaterialInstance(g.instance)
        }
        ghostPool.clear()
        highlightMesh.destroy(filament)
        for (aura in auraPool) {
            if (aura.inScene) engine.scene.removeEntity(aura.entity)
            filament.destroyEntity(aura.entity)
            EntityManager.get().destroy(aura.entity)
            filament.destroyMaterialInstance(aura.instance)
        }
        auraPool.clear()
        auraMesh.destroy(filament)
        for (te in tiles.values) {
            filament.destroyEntity(te.entity)
            EntityManager.get().destroy(te.entity)
            // Sea tiles share the water instances — destroyed once, below.
            if (!te.sea) filament.destroyMaterialInstance(te.instance)
        }
        if (hasSea) {
            filament.destroyMaterialInstance(waterVisible)
            filament.destroyMaterialInstance(waterExplored)
        }
        tiles.clear()
        pieceMeshes.destroy(filament)
        hexMesh.destroy(filament)
        environment.destroy()
        materials.destroy()
    }

    companion object {
        private const val TAG = "BoardScene"
        private const val WAVE_MAX_RADIUS = Primitives.HEX_RADIUS * 1.3f + 0.2f
        private const val DIM_FACTOR = 0.72f
        /** Fog tile darkening: explored memory stays readable, unseen land goes near-black. */
        private const val FOG_EXPLORED_FACTOR = 0.45f
        private const val FOG_HIDDEN_FACTOR = 0.12f
        private const val MAX_PATH_LEN = 24
        /** Units turn toward their heading over this leading fraction of each motion segment. */
        private const val HEADING_TURN_FRACTION = 0.25f
        /** Label anchor height: above the tallest piece (capital banner ~0.70). */
        private const val ANCHOR_LIFT = 0.8f
        /** Water shimmer wrap: 20pi is a whole period of both sine bands (x0.9, x0.6). */
        private const val WATER_PERIOD = (20.0 * Math.PI).toFloat()
        /** Editor ghost ring: barely-there, so the board's own colors stay dominant. */
        private const val GHOST_ALPHA = 0.12f
    }
}
