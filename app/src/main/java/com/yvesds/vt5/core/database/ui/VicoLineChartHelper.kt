package com.yvesds.vt5.core.database.ui

import android.graphics.Color
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.component.TextComponent

/**
 * Helper object voor Vico 2.0.0-beta.3 LineCartesianLayer configuratie.
 */
object VicoLineChartHelper {

    private val axisLabel = TextComponent(
        color = Color.WHITE,
        textSizeSp = 9f,
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

    private val monthLetterByWeekStart = mapOf(
        1 to "J", 5 to "F", 9 to "M", 13 to "A", 17 to "M", 21 to "J",
        25 to "J", 29 to "A", 33 to "S", 37 to "O", 41 to "N", 45 to "D", 49 to "D"
    )

    /** Bouw één of meerdere lijnlagen met standaard cubic-bezier interpolatie. */
    fun createLineLayer(vararg lineColors: Int): LineCartesianLayer {
        val lines = lineColors.map { color ->
            val fill = Fill(color)
            LineCartesianLayer.Line(fill = LineCartesianLayer.LineFill.single(fill))
        }

        return LineCartesianLayer(
            LineCartesianLayer.LineProvider.series(*lines.toTypedArray()),
            pointSpacingDp = 2f,
        )
    }

    /** Create a dedicated LineCartesianLayer for beaufort values, fixed to 0..maxBeaufort on the Y axis */
    fun createBeaufortLineLayer(maxBeaufort: Double, beaufortColor: Int, alpha: Int = 140, pointSpacingDp: Float = 2f): LineCartesianLayer {
        // Apply requested alpha to the provided color so the beaufort line can be semi-transparent
        val a = alpha.coerceIn(0, 255)
        val withAlpha = Color.argb(a, Color.red(beaufortColor), Color.green(beaufortColor), Color.blue(beaufortColor))
        val fill = Fill(withAlpha)
        val line = LineCartesianLayer.Line(fill = LineCartesianLayer.LineFill.single(fill))

        // Use a fixed range provider for Y (0..maxBeaufort). X range will remain automatic.
        val rangeProvider = CartesianLayerRangeProvider.fixed(
            /* minX */ null,
            /* maxX */ null,
            /* minY */ 0.0,
            /* maxY */ maxBeaufort,
        )

        return LineCartesianLayer(
            LineCartesianLayer.LineProvider.series(line),
            rangeProvider = rangeProvider,
            verticalAxisPosition = Axis.Position.Vertical.End,
            pointSpacingDp = pointSpacingDp,
        )
    }

    /**
     * Labels enkel per maand (ongeveer om de 4 weken): week 1, 5, 9, ...
     * We tonen enkel de maandletter.
     */
    fun createMonthLabelAxis(): HorizontalAxis<Axis.Position.Horizontal.Bottom> {
        val monthFormatter = CartesianValueFormatter { _, value, _ ->
            val week = value.toInt() + 1
            monthLetterByWeekStart[week] ?: "?"
        }

        return HorizontalAxis.bottom(
            line = axisLine,
            label = axisLabel,
            valueFormatter = monthFormatter,
            tick = axisTick,
            tickLengthDp = 4f,
            guideline = axisGuideline,
            itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = 4, offset = 0),
        )
    }

    /** Korte verticale streepjes voor elke week, zonder labels. */
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

    fun createCountAxis(): VerticalAxis<Axis.Position.Vertical.Start> {
        val formatter = CartesianValueFormatter { _, value, _ ->
            // Display counts as whole numbers, no decimals
            String.format(java.util.Locale.getDefault(), "%.0f", value)
        }

        return VerticalAxis.start(
            line = axisLine,
            label = axisLabel,
            valueFormatter = formatter,
            tick = axisTick,
            tickLengthDp = 4f,
            guideline = axisGuideline,
            itemPlacer = VerticalAxis.ItemPlacer.count(count = { 6 }),
        )
    }

    fun createBeaufortAxis(): VerticalAxis<Axis.Position.Vertical.End> {
        val formatter = CartesianValueFormatter { _, value, _ ->
            // Beaufort scale: show as whole numbers (0..7)
            String.format(java.util.Locale.getDefault(), "%.0f", value)
        }

        return VerticalAxis.end(
            line = axisLine,
            label = axisLabel,
            valueFormatter = formatter,
            tick = axisTick,
            tickLengthDp = 4f,
            guideline = null,
            // Prefer more ticks for the beaufort axis so integer steps are visible
            itemPlacer = VerticalAxis.ItemPlacer.count(count = { 8 }),
        )
    }
}
