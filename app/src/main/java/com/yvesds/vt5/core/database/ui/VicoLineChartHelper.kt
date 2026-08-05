package com.yvesds.vt5.core.database.ui

import android.graphics.Color
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.component.TextComponent

/**
 * Helper voor Vico 2.0.0-beta.3 configuratie.
 */
object VicoLineChartHelper {

    val whiteAxisLabel = TextComponent(
        color = Color.WHITE,
        textSizeSp = 8f,
    )

    private val axisLine = LineComponent(
        fill = Fill(Color.LTGRAY),
        thicknessDp = 1f,
    )

    private val axisTick = LineComponent(
        fill = Fill(Color.LTGRAY),
        thicknessDp = 1f,
    )

    private val axisGuideline = LineComponent(
        fill = Fill(Color.argb(120, 180, 180, 180)),
        thicknessDp = 0.6f,
    )

    private val monthLetterMap = mapOf(
        1 to "J", 5 to "F", 9 to "M", 14 to "A", 18 to "M", 22 to "J",
        26 to "J", 31 to "A", 35 to "S", 40 to "O", 44 to "N", 48 to "D"
    )

    fun createLineLayer(vararg lineColors: Int): LineCartesianLayer {
        val lines = lineColors.map { color ->
            LineCartesianLayer.Line(fill = LineCartesianLayer.LineFill.single(Fill(color)))
        }
        return LineCartesianLayer(
            LineCartesianLayer.LineProvider.series(*lines.toTypedArray())
        )
    }

    fun createBeaufortLineLayer(maxBeaufort: Double, beaufortColor: Int, alpha: Int = 140): LineCartesianLayer {
        val a = alpha.coerceIn(0, 255)
        val withAlpha = Color.argb(a, Color.red(beaufortColor), Color.green(beaufortColor), Color.blue(beaufortColor))
        val fill = Fill(withAlpha)
        val line = LineCartesianLayer.Line(fill = LineCartesianLayer.LineFill.single(fill))

        val rangeProvider = CartesianLayerRangeProvider.fixed(
            minY = 0.0,
            maxY = maxBeaufort,
        )

        return LineCartesianLayer(
            LineCartesianLayer.LineProvider.series(line),
            rangeProvider = rangeProvider,
            verticalAxisPosition = Axis.Position.Vertical.End
        )
    }

    fun createMonthLabelAxis(): HorizontalAxis<Axis.Position.Horizontal.Bottom> {
        val monthFormatter = CartesianValueFormatter { _, value, _ ->
            monthLetterMap[value.toInt()] ?: " "
        }

        return HorizontalAxis.bottom(
            line = axisLine,
            label = whiteAxisLabel,
            valueFormatter = monthFormatter,
            tick = axisTick,
            tickLengthDp = 4f,
            guideline = axisGuideline,
            itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = 1, offset = 0)
        )
    }

    fun createCountAxis(): VerticalAxis<Axis.Position.Vertical.Start> {
        return VerticalAxis.start(
            line = axisLine,
            label = whiteAxisLabel,
            valueFormatter = { _, value, _ -> String.format(java.util.Locale.getDefault(), "%.0f", value) },
            tick = axisTick,
            tickLengthDp = 4f,
            guideline = axisGuideline,
            itemPlacer = VerticalAxis.ItemPlacer.count(count = { 6 }),
        )
    }

    fun createWeeklyTickAxis(): HorizontalAxis<Axis.Position.Horizontal.Top> {
        return HorizontalAxis.top(
            line = axisLine,
            label = null,
            tick = axisTick,
            tickLengthDp = 4f,
            guideline = null,
            itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = 1, offset = 0),
        )
    }

    fun createBeaufortAxis(): VerticalAxis<Axis.Position.Vertical.End> {
        return VerticalAxis.end(
            line = axisLine,
            label = whiteAxisLabel,
            valueFormatter = { _, value, _ -> String.format(java.util.Locale.getDefault(), "%.0f", value) },
            tick = axisTick,
            tickLengthDp = 4f,
            guideline = null,
            itemPlacer = VerticalAxis.ItemPlacer.count(count = { 8 }),
        )
    }
}
