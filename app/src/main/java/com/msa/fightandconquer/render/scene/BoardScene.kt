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
        exists = { it in tiles },
        isRaised = { tiles[it]?.raised == true },
    )

    // ----- tiles -----

    private class TileEntity(
        val entity: Int,
        val instance: MaterialInstance,
        var color: Float3,
        var raised: Boolean,
        var y: Float,
    )

    private val tiles = HashMap<Hex, TileEntity>()

    // ----- pieces -----

    private inner class Piece(
        val kind: PieceKind,
        val entities: IntArray,
        val instances: List<MaterialInstance>,
        val roles: List<com.msa.fightandconquer.render.mesh.ColorRole>,
        val ownerIndex: Int?,
        var hex: Hex,
        var scale: Float = 1f,
        var yOffset: Float = 0f,
        var xz: Pair<Float, Float>? = null, // non-null while hopping between hexes
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
                tm.setTransform(ti, Transforms.trs(x, y, z, scale = scale))
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
        fogVisible = visible
        fogExplored = explored ?: emptySet()
        for ((hex, te) in tiles) applyTileColor(hex, te)
        for (piece in unitPieces.values) piece.setHidden(isFogged(piece.hex))
        for ((hex, piece) in buildingPieces) piece.setHidden(isFogged(hex))
        for ((hex, piece) in floraPieces) piece.setHidden(isFogged(hex))
        for ((hex, piece) in depositPieces) applyDepositFog(hex, piece)
        // Auras were possibly drawn before fog arrived (init reconcile) or the fog
        // edge moved — re-derive them so no ring survives inside the fog.
        refreshAuras(latestState)
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
        val tileMaterial = materials.material("hexTile")
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE

        for ((hex, tile) in initialState.tiles) {
            val cx = HexWorld.centerX(hex)
            val cz = HexWorld.centerZ(hex)
            minX = minOf(minX, cx); maxX = maxOf(maxX, cx)
            minZ = minOf(minZ, cz); maxZ = maxOf(maxZ, cz)

            val color = tile.owner?.let { Palette.faction(it.value) } ?: Palette.NEUTRAL
            val raised = tile.owner != null
            val instance = tileMaterial.createInstance().apply {
                setParameter("colorFrom", color.x, color.y, color.z)
                setParameter("colorTo", color.x, color.y, color.z)
                setParameter("tileCenter", cx, 0f, cz)
                setParameter("waveRadius", 0f)
                setParameter("waveSoftness", 0.18f)
            }
            val entity = EntityManager.get().create()
            RenderableManager.Builder(1)
                .boundingBox(hexMesh.aabb)
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, hexMesh.vertexBuffer, hexMesh.indexBuffer)
                .material(0, instance)
                .castShadows(true)
                .receiveShadows(true)
                .build(filament, entity)
            engine.scene.addEntity(entity)
            val te = TileEntity(entity, instance, color, raised, if (raised) Primitives.CAPTURE_RAISE else 0f)
            tiles[hex] = te
            setTileTransform(hex, te)
        }

        rig.targetX = (minX + maxX) / 2f
        rig.targetZ = (minZ + maxZ) / 2f
        boardSpanX = maxX - minX + 2f
        boardSpanZ = maxZ - minZ + 2f
        rig.boundsFromBoard(minX, maxX, minZ, maxZ)

        reconcile(initialState, log = false)
    }

    // ----- public surface -----

    fun tap(xPx: Float, yPx: Float) {
        // Tap during playback fast-forwards instead of selecting (doc: skip-animation input).
        if (!animator.isIdle || eventQueue.isNotEmpty()) {
            skipAnimations()
            return
        }
        val viewport = engine.view.viewport
        val hex = picker.pick(xPx, yPx, viewport.width, viewport.height, rig)
        if (hex == null) {
            onTapMiss?.invoke() // tapping the void cancels the selection
            return
        }
        onTap?.invoke(hex)
    }

    fun pan(dxPx: Float, dyPx: Float) {
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

    fun zoom(factor: Float) = rig.zoomBy(factor)

    /** Feed a new authoritative state and the events that produced it. */
    fun apply(state: GameState, events: List<GameEvent>) {
        latestState = state
        pendingState = state
        eventQueue.addAll(events)
    }

    /**
     * Show selection + legal-move overlays: translucent discs hovering over tiles.
     * Colors: selected = white, move = white (dimmer), capture = warm red, merge = gold.
     */
    fun showHighlights(selected: Hex?, moves: Set<Hex>, captures: Set<Hex>, merges: Set<Hex>) {
        clearHighlights()
        highlightClock = 0f // pulse always starts bright: "these just lit up"
        selected?.let { addHighlight(it, 1f, 1f, 1f, 0.55f) }
        for (hex in moves) addHighlight(hex, 1f, 1f, 1f, 0.3f)
        for (hex in captures) addHighlight(hex, 0.95f, 0.45f, 0.35f, 0.5f, pulse = true)
        for (hex in merges) addHighlight(hex, 0.9f, 0.75f, 0.35f, 0.55f)
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
                sinkAway(piece) {
                    if (event.cause != DeathCause.KILLED) {
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
                    animator.tween(0.25f, Easings::easeOutCubic, onEnd = {
                        destroyPiece(consumed)
                        finish()
                    }) { t ->
                        consumed.xz = lerpHex(from, to, t)
                        consumed.yOffset = Easings.hop(t) * 0.3f
                        consumed.scale = 1f - 0.5f * t
                        consumed.updateTransform()
                    }
                } else {
                    finish()
                }
            }

            is GameEvent.BuildingBuilt -> {
                val owner = latestState.tiles[event.hex]?.owner?.value
                val piece = createPiece(buildingKind(event.building), event.hex, owner)
                buildingPieces[event.hex] = piece
                spawnBounce(piece)
                if (!isFogged(event.hex)) rumbleTime = 0f // no juice for unseen builds
            }

            is GameEvent.BuildingDestroyed -> {
                val piece = buildingPieces.remove(event.hex) ?: return
                sinkAway(piece)
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

            is GameEvent.TurnStarted -> {
                // Refresh spent-dim for the whole board immediately (undims the new army).
                for ((id, piece) in unitPieces) {
                    piece.setDimmed(latestState.units[id]?.spent == true)
                }
            }

            // HUD-level events: no board animation (diplomacy stays off the board).
            is GameEvent.ActionRejected, is GameEvent.Bankruptcy,
            is GameEvent.PlayerEliminated, is GameEvent.GameOver,
            is GameEvent.PactProposed, is GameEvent.PactAccepted, is GameEvent.PactDeclined,
            is GameEvent.PactExpired, is GameEvent.PactProposalExpired,
            is GameEvent.PactBroken, is GameEvent.TributeSent,
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
        animator.tween(0.25f, Easings::easeOutCubic, onEnd = {
            piece.xz = null
            piece.yOffset = 0f
            piece.updateTransform()
            unitId?.let { piece.setDimmed(latestState.units[it]?.spent == true) }
        }) { t ->
            piece.xz = lerpHex(from, to, t)
            piece.yOffset = Easings.hop(t) * height
            piece.updateTransform()
        }
    }

    /**
     * BFS shortest path from..to over tiles owned by [owner] in the POST-move state
     * (a captured destination is already owned by the mover then). Null when a plain
     * direct hop should be used (adjacent, unreachable, or degenerate).
     */
    private fun ownedPath(from: Hex, to: Hex, owner: com.msa.fightandconquer.core.model.PlayerId): List<Hex>? {
        if (from == to) return null
        val canEnter: (Hex) -> Boolean = { h -> h == from || latestState.tiles[h]?.owner == owner }
        if (!canEnter(to)) return null
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
        return if (path.size in 3..MAX_PATH_LEN) path else null // 2 = plain hop; too long = direct
    }

    /** Chained per-hex hops: mid segments linear (continuous run), final segment eases out. */
    private fun hopAlong(piece: Piece, unitId: UnitId, path: List<Hex>) {
        val segments = path.size - 1
        val perHex = minOf(0.16f, 0.9f / segments)
        fun runSegment(index: Int) {
            val a = path[index]
            val b = path[index + 1]
            val last = index == segments - 1
            piece.hex = b
            val yDelta = tileTopY(a) - tileTopY(b)
            val height = if (last) 0.3f else 0.2f
            animator.tween(perHex, if (last) Easings::easeOutCubic else Easings::linear, onEnd = {
                if (last) {
                    piece.xz = null
                    piece.yOffset = 0f
                    piece.updateTransform()
                    piece.setDimmed(latestState.units[unitId]?.spent == true)
                } else {
                    runSegment(index + 1)
                }
            }) { t ->
                piece.xz = lerpHex(a, b, t)
                piece.yOffset = Easings.hop(t) * height + (1f - t) * yDelta
                piece.updateTransform()
            }
        }
        runSegment(0)
    }

    private fun sinkAway(piece: Piece, duration: Float = 0.25f, onDone: (() -> Unit)? = null) {
        animator.tween(duration, Easings::easeInCubic, onEnd = {
            destroyPiece(piece)
            onDone?.invoke()
        }) { t ->
            piece.scale = 1f - t
            piece.yOffset = -0.1f * t
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

    private fun createPiece(kind: PieceKind, hex: Hex, ownerIndex: Int?): Piece {
        val parts = pieceMeshes.partsFor(kind)
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
        return Piece(kind, entities, instances, parts.map { it.role }, ownerIndex, hex)
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

    private fun buildingKind(building: Building): PieceKind = when (building) {
        Building.CAPITAL -> PieceKind.CAPITAL
        Building.FARM -> PieceKind.FARM
        Building.TOWER -> PieceKind.TOWER
        Building.STRONG_TOWER -> PieceKind.STRONG_TOWER
        Building.MINE -> PieceKind.MINE
        Building.MARKET -> PieceKind.MARKET
        Building.LUMBER_CAMP -> PieceKind.LUMBER_CAMP
        Building.WATCHTOWER -> PieceKind.WATCHTOWER
    }

    private fun tileTopY(hex: Hex): Float = (tiles[hex]?.y ?: 0f) + Primitives.HEX_HEIGHT

    private fun setTileTransform(hex: Hex, te: TileEntity) {
        val tm = filament.transformManager
        var instance = tm.getInstance(te.entity)
        if (instance == 0) instance = tm.create(te.entity)
        tm.setTransform(instance, Transforms.translation(HexWorld.centerX(hex), te.y, HexWorld.centerZ(hex)))
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
            covered.merge(hex, defense, ::maxOf)
            com.msa.fightandconquer.core.hex.HexMath.forEachNeighbor(hex) { n ->
                if (state.tiles[n]?.owner == owner) covered.merge(n, defense, ::maxOf)
            }
        }
        for (unit in state.units.values) {
            if (unit.type != com.msa.fightandconquer.core.model.UnitType.ARCHER) continue
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
            if (piece == null || piece.kind != expectedKind) {
                piece?.let { destroyPiece(it) }
                unitPieces[unit.id] = createPiece(expectedKind, unit.hex, unit.owner.value)
                if (piece != null || pendingState != null) corrections++
            } else if (piece.hex != unit.hex || piece.scale != 1f || piece.xz != null) {
                piece.hex = unit.hex
                piece.scale = 1f
                piece.yOffset = 0f
                piece.xz = null
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
            if (piece == null || piece.kind != kind) {
                piece?.let { destroyPiece(it) }
                pieces[hex] = createPiece(kind, hex, tile.owner?.value)
                if (piece != null) corrections++
            } else if (piece.scale != 1f) {
                piece.scale = 1f
                piece.yOffset = 0f
                piece.updateTransform()
                corrections++
            }
            // Fog is a view annotation — sync silently (never a correction).
            pieces.getValue(hex).setHidden(isFogged(hex))
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
            filament.destroyMaterialInstance(te.instance)
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
        /** Label anchor height: above the tallest piece (capital banner ~0.70). */
        private const val ANCHOR_LIFT = 0.8f
    }
}
