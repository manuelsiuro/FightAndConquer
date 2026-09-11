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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.fightandconquer.R
import com.msa.fightandconquer.ui.UiColors
import com.msa.fightandconquer.ui.theme.UiFontFamily

/** One seat's curve: parallel [rounds]/[values]; an eliminated seat's just ends early. */
data class ChartSeries(val color: Color, val rounds: List<Int>, val values: List<Int>)

/** A key moment pinned onto its actor's curve. */
data class ChartMarker(val round: Int, val value: Int, val color: Color)

/**
 * Axis-light multi-series timeline: labeled integer gridlines, min/max labels, one 2 dp
 * line per seat in its faction pastel, and an animated left-to-right draw-in. [filled]
 * adds the 12 % tint-ladder area under each curve (the debrief's territory lens).
 * [yUnit] names what the Y axis measures, appended to the max label.
 */
@Composable
fun TimelineChart(
    series: List<ChartSeries>,
    markers: List<ChartMarker> = emptyList(),
    filled: Boolean = false,
    yUnit: String? = null,
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
    // Built from scratch, so it misses the theme's LocalTextStyle: name the UI face.
    val labelStyle = TextStyle(fontFamily = UiFontFamily, fontSize = 10.sp, color = UiColors.inkMuted)
    val gridColor = UiColors.hairline
    val baselineColor = UiColors.divider
    val topLabel = yUnit?.let { stringResource(R.string.chart_axis_max, maxValue, it) }
        ?: maxValue.toString()
    val startTurnLabel = stringResource(R.string.hud_turn, 1)
    val endTurnLabel = stringResource(R.string.hud_turn, maxRound + 1)

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

        // Grid: baseline plus labeled integer lines, full width, never clipped by the draw-in.
        drawLine(baselineColor, Offset(0f, chartHeight), Offset(chartWidth, chartHeight), 1.dp.toPx())
        for (value in chartGridValues(maxValue)) {
            val gy = y(value)
            drawLine(gridColor, Offset(0f, gy), Offset(chartWidth, gy), 1.dp.toPx())
            val label = textMeasurer.measure(value.toString(), labelStyle)
            drawText(label, topLeft = Offset(2.dp.toPx(), gy - label.size.height - 1.dp.toPx()))
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

        // Min/max labels inside the frame, turn span under the baseline (1-based,
        // matching the HUD's turn counter).
        drawText(textMeasurer, topLabel, Offset(2.dp.toPx(), 2.dp.toPx()), labelStyle)
        drawText(textMeasurer, "0", Offset(2.dp.toPx(), chartHeight - 14.sp.toPx()), labelStyle)
        val endLabel = textMeasurer.measure(endTurnLabel, labelStyle)
        drawText(textMeasurer, startTurnLabel, Offset(0f, chartHeight + 2.dp.toPx()), labelStyle)
        drawText(
            textMeasurer,
            endTurnLabel,
            Offset(chartWidth - endLabel.size.width, chartHeight + 2.dp.toPx()),
            labelStyle,
        )
    }
}

/**
 * Interior gridline values for a [niceCeil] max — 1-4 lines whose step is itself a
 * friendly 1/2/5 × 10^k, so every label reads as a round number.
 */
internal fun chartGridValues(max: Int): List<Int> {
    val divisor = listOf(5, 4, 2).firstOrNull { max % it == 0 && isFriendlyStep(max / it) }
        ?: return emptyList()
    return (1 until divisor).map { max / divisor * it }
}

private fun isFriendlyStep(step: Int): Boolean {
    var mantissa = step
    while (mantissa % 10 == 0) mantissa /= 10
    return mantissa in listOf(1, 2, 5)
}

/** The smallest 1/2/5 × 10^k at or above [value] — chart tops land on friendly numbers. */
internal fun niceCeil(value: Int): Int {
    if (value <= 1) return 1
    var magnitude = 1
    while (magnitude * 10 <= value) magnitude *= 10
    return listOf(1, 2, 5, 10).first { it * magnitude >= value } * magnitude
}
