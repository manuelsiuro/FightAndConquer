package com.msa.fightandconquer.ui.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.toArgb
import com.msa.fightandconquer.core.editor.CustomMapDef
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Terrain
import com.msa.fightandconquer.ui.LightUiColors
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A deterministic 2D minimap of a custom map: pointy-top hexes filled by
 * terrain/owner, capitals dotted, the map name captioned. Serves as the shareable
 * stego carrier and as a library thumbnail. Plain Canvas — no Filament, no theme
 * (faction pastels are fixed across themes; the parchment ground is its own look).
 */
object MinimapRenderer {

    const val SIZE = 512

    fun render(def: CustomMapDef): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(GROUND)

        val tiles = def.level.map.tiles
        if (tiles.isNotEmpty()) {
            // Axial -> plane: x = sqrt3 * (q + r/2), y = 1.5 * r (unit circumradius).
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            val centers = tiles.map { tile ->
                val x = SQRT3 * (tile.hex.q + tile.hex.r / 2f)
                val y = 1.5f * tile.hex.r
                minX = min(minX, x); maxX = max(maxX, x)
                minY = min(minY, y); maxY = max(maxY, y)
                Triple(tile, x, y)
            }
            val margin = 28f
            val spanX = maxX - minX + 2f * SQRT3
            val spanY = maxY - minY + 3f
            val scale = min((SIZE - 2 * margin) / spanX, (SIZE - 2 * margin - CAPTION) / spanY)
            val offX = (SIZE - spanX * scale) / 2f - (minX - SQRT3) * scale
            val offY = (SIZE - CAPTION - spanY * scale) / 2f - (minY - 1.5f) * scale

            val fill = Paint(Paint.ANTI_ALIAS_FLAG)
            val capitals = def.level.map.capitals.toSet()
            for ((tile, x, y) in centers) {
                val cx = x * scale + offX
                val cy = y * scale + offY
                fill.color = when {
                    tile.terrain == Terrain.SEA -> SEA
                    tile.owner != null -> LightUiColors.faction(tile.owner!!).toArgb()
                    else -> NEUTRAL
                }
                canvas.drawPath(hexPath(cx, cy, scale * 0.94f), fill)
                if (tile.hex in capitals && tile.building == Building.CAPITAL) {
                    fill.color = CAPITAL_DOT
                    canvas.drawCircle(cx, cy, scale * 0.34f, fill)
                }
            }
        }

        val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            textSize = 24f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(def.name.take(40), SIZE / 2f, SIZE - 20f, caption)
        return bitmap
    }

    private fun hexPath(cx: Float, cy: Float, radius: Float): Path {
        val path = Path()
        for (i in 0 until 6) {
            val angle = Math.toRadians(60.0 * i - 30.0)
            val x = cx + radius * cos(angle).toFloat()
            val y = cy + radius * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    private val SQRT3 = sqrt(3f)
    private const val CAPTION = 44f

    // Mirrors the board's fixed look in sRGB (Palette holds the same values in linear).
    private const val GROUND = 0xFFF4F2EF.toInt()
    private const val NEUTRAL = 0xFFEAE6E1.toInt()
    private const val SEA = 0xFFA3C6CC.toInt()
    private const val INK = 0xFF3E3A36.toInt()
    private val CAPITAL_DOT = Color.argb(255, 62, 58, 54)
}
