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
import dev.romainguy.kotlin.math.Float3
import kotlin.math.exp
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
    private val pieceMeshes = PieceMeshes(filament)
    private val animator = Animator()

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
        var hex: Hex,
        var scale: Float = 1f,
        var yOffset: Float = 0f,
        var xz: Pair<Float, Float>? = null, // non-null while hopping between hexes
    ) {
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
    private class HighlightEntity(val entity: Int, val instance: MaterialInstance, var inScene: Boolean)
    private val highlightPool = ArrayList<HighlightEntity>()
    private var highlightsShown = 0

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
        val hex = picker.pick(xPx, yPx, viewport.width, viewport.height, rig) ?: return
        onTap?.invoke(hex)
    }

    fun pan(dxPx: Float, dyPx: Float) = rig.pan(dxPx, dyPx, engine.view.viewport.height)

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
        selected?.let { addHighlight(it, 1f, 1f, 1f, 0.55f) }
        for (hex in moves) addHighlight(hex, 1f, 1f, 1f, 0.3f)
        for (hex in captures) addHighlight(hex, 0.95f, 0.45f, 0.35f, 0.5f)
        for (hex in merges) addHighlight(hex, 0.9f, 0.75f, 0.35f, 0.55f)
    }

    fun clearHighlights() {
        for (i in 0 until highlightsShown) {
            val h = highlightPool[i]
            if (h.inScene) {
                engine.scene.removeEntity(h.entity)
                h.inScene = false
            }
        }
        highlightsShown = 0
    }

    private fun addHighlight(hex: Hex, r: Float, g: Float, b: Float, a: Float) {
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
        }
        rig.update(engine.camera)
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
                val piece = createPiece(pieceMeshes.unitKind(event.unit.tier), event.unit.hex, event.unit.owner.value)
                unitPieces[event.unit.id] = piece
                spawnBounce(piece)
                rumbleTime = 0f
            }

            is GameEvent.UnitMoved -> {
                val piece = unitPieces[event.unit] ?: return
                hop(piece, event.from, event.to)
            }

            is GameEvent.HexCaptured -> {
                val te = tiles[event.hex] ?: return
                val color = Palette.faction(event.newOwner.value)
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
                        pieceMeshes.unitKind(event.into.tier),
                        event.into.hex,
                        event.into.owner.value,
                    )
                    unitPieces[event.into.id] = upgraded
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
                rumbleTime = 0f
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

            // HUD-level events: no board animation.
            is GameEvent.ActionRejected, is GameEvent.TurnStarted, is GameEvent.Bankruptcy,
            is GameEvent.PlayerEliminated, is GameEvent.GameOver,
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

    private fun hop(piece: Piece, from: Hex, to: Hex, height: Float = 0.3f) {
        piece.hex = to
        animator.tween(0.25f, Easings::easeOutCubic, onEnd = {
            piece.xz = null
            piece.yOffset = 0f
            piece.updateTransform()
        }) { t ->
            piece.xz = lerpHex(from, to, t)
            piece.yOffset = Easings.hop(t) * height
            piece.updateTransform()
        }
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
        return Piece(kind, entities, instances, hex).also { it.updateTransform() }
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
        for (piece in unitPieces.values) {
            if (piece.hex == hex && piece.xz == null) piece.updateTransform()
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
                te.instance.setParameter("colorFrom", color.x, color.y, color.z)
                te.instance.setParameter("colorTo", color.x, color.y, color.z)
                te.instance.setParameter("waveRadius", 0f)
                setTileTransform(hex, te)
            }
        }

        // Units.
        val staleUnits = unitPieces.keys.filter { it !in state.units }
        staleUnits.forEach { id -> unitPieces.remove(id)?.let { destroyPiece(it); corrections++ } }
        for (unit in state.units.values) {
            val expectedKind = pieceMeshes.unitKind(unit.tier)
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

        // Keep pieces glued to final tile heights.
        for (piece in unitPieces.values) piece.updateTransform()
        for (piece in buildingPieces.values) piece.updateTransform()
        for (piece in floraPieces.values) piece.updateTransform()

        if (log && corrections > 0) {
            Log.w(TAG, "reconcile corrected $corrections discrepancies (events should have covered these)")
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
        }
        return corrections
    }

    override fun destroy() {
        (unitPieces.values + buildingPieces.values + floraPieces.values).forEach { destroyPiece(it) }
        unitPieces.clear(); buildingPieces.clear(); floraPieces.clear()
        for (h in highlightPool) {
            if (h.inScene) engine.scene.removeEntity(h.entity)
            filament.destroyEntity(h.entity)
            EntityManager.get().destroy(h.entity)
            filament.destroyMaterialInstance(h.instance)
        }
        highlightPool.clear()
        highlightMesh.destroy(filament)
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
    }
}
