package com.msa.fightandconquer.ui.debrief

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.ui.UiColors

/** One seat's curve: parallel [rounds]/[values]; an eliminated seat's just ends early. */
data class ChartSeries(val color: Color, val rounds: List<Int>, val values: List<Int>)

/** A key moment pinned onto its actor's curve. */
data class ChartMarker(val round: Int, val value: Int, val color: Color)

/**
 * Axis-light multi-series timeline: hairline gridlines, min/max labels, one 2 dp line
 * per seat in its faction pastel, and an animated left-to-right draw-in. [filled] adds
 * the 12 % tint-ladder area under each curve (the debrief's territory lens).
 */
@Composable
fun TimelineChart(
    series: List<ChartSeries>,
    markers: List<ChartMarker> = emptyList(),
    filled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val maxRound = (series.maxOfOrNull { it.rounds.lastOrNull() ?: 0 } ?: 0).coerceAtLeast(1)
    val maxValue = niceCeil(
        maxOf(
            series.maxOfOrNull { it.values.maxOrNull() ?: 0 } ?: 0,
            markers.maxOfOrNull { it.value } ?: 0,
        ),
    )
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = UiColors.inkMuted)
    val gridColor = UiColors.hairline
    val baselineColor = UiColors.divider

    // The war redraws itself on every lens switch — progress restarts with the data.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(series) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 800, easing = FastOutSlowInEasing))
    }

    Canvas(modifier.fillMaxWidth().height(170.dp)) {
        val bottomInset = 16.dp.toPx()
        val chartHeight = size.height - bottomInset
        val chartWidth = size.width

        fun x(round: Int): Float = round.toFloat() / maxRound * chartWidth
        fun y(value: Int): Float = chartHeight - value.toFloat() / maxValue * chartHeight

        // Grid: baseline plus thirds, full width, never clipped by the draw-in.
        drawLine(baselineColor, Offset(0f, chartHeight), Offset(chartWidth, chartHeight), 1.dp.toPx())
        for (third in 1..3) {
            val gy = chartHeight - chartHeight * third / 3f
            drawLine(gridColor, Offset(0f, gy), Offset(chartWidth, gy), 1.dp.toPx())
        }

        clipRect(right = chartWidth * progress.value) {
            for (s in series) {
                if (s.rounds.isEmpty()) continue
                // At most ~2 points per pixel keeps a 400-round game cheap to stroke.
                val stride = (s.rounds.size / (chartWidth / 2f)).toInt().coerceAtLeast(1)
                val line = Path()
                val indices = (s.rounds.indices step stride) + (s.rounds.size - 1)
                var first = true
                for (i in indices) {
                    val px = x(s.rounds[i])
                    val py = y(s.values[i])
                    if (first) line.moveTo(px, py) else line.lineTo(px, py)
                    first = false
                }
                if (filled) {
                    val area = Path().apply {
                        addPath(line)
                        lineTo(x(s.rounds.last()), chartHeight)
                        lineTo(x(s.rounds.first()), chartHeight)
                        close()
                    }
                    drawPath(area, s.color.copy(alpha = 0.12f))
                }
                drawPath(line, s.color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
            for (marker in markers) {
                drawCircle(marker.color, radius = 4.dp.toPx(), center = Offset(x(marker.round), y(marker.value)))
            }
        }

        // Min/max labels inside the frame, round span under the baseline (1-based,
        // matching the HUD's turn counter).
        drawText(textMeasurer, maxValue.toString(), Offset(2.dp.toPx(), 2.dp.toPx()), labelStyle)
        drawText(textMeasurer, "0", Offset(2.dp.toPx(), chartHeight - 14.sp.toPx()), labelStyle)
        val endText = (maxRound + 1).toString()
        val endLabel = textMeasurer.measure(endText, labelStyle)
        drawText(textMeasurer, "1", Offset(0f, chartHeight + 2.dp.toPx()), labelStyle)
        drawText(
            textMeasurer,
            endText,
            Offset(chartWidth - endLabel.size.width, chartHeight + 2.dp.toPx()),
            labelStyle,
        )
    }
}

/** The smallest 1/2/5 × 10^k at or above [value] — chart tops land on friendly numbers. */
private fun niceCeil(value: Int): Int {
    if (value <= 1) return 1
    var magnitude = 1
    while (magnitude * 10 <= value) magnitude *= 10
    return listOf(1, 2, 5, 10).first { it * magnitude >= value } * magnitude
}
