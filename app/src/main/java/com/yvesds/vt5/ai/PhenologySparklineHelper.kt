package com.yvesds.vt5.ai

import android.graphics.Color
import com.patrykandpatrick.vico.core.cartesian.CartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.views.cartesian.CartesianChartView
import com.yvesds.vt5.core.database.dao.WeekCountRow
import com.yvesds.vt5.core.database.ui.VicoLineChartHelper
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.Axis

/**
 * [STABLE_GOLDEN_STATE_V3 - 25 AUG 2026]
 * PhenologySparklineHelper - Wetenschappelijke render-engine voor Vico v2.
 * NU MET 52-WEKEN AGGREGATIE EN MAAND-AS.
 */
object PhenologySparklineHelper {

    /**
     * Tekent de curve op basis van een rauwe lijst met floats (altijd exact gebufferd).
     */
    suspend fun setup(chartView: CartesianChartView, distribution: List<Float>) {
        if (distribution.isEmpty()) return

        // 1. FRESH PRODUCER (Stabiel in ScrollView)
        val modelProducer = CartesianChartModelProducer()
        
        modelProducer.runTransaction {
            lineSeries { series(distribution) }
        }

        // 2. CHART SETUP MET MAAND-AS (Geïnspireerd op Windgrafieken)
        val lineLayer = LineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.Line(
                    fill = LineCartesianLayer.LineFill.single(Fill(Color.parseColor("#4CAF50"))), // WOW Groen
                    thicknessDp = 1.5f,
                    areaFill = LineCartesianLayer.AreaFill.single(Fill(Color.parseColor("#1A4CAF50"))),
                    pointConnector = LineCartesianLayer.PointConnector.cubic()
                )
            )
        )

        chartView.chart = CartesianChart(
            layers = arrayOf(lineLayer),
            bottomAxis = VicoLineChartHelper.createMonthLabelAxis() // TOONT J,F,M...D
        )

        // FORCEER VOLLEDIG UITGEZOOMD (Toon alle 52 weken direct)
        chartView.zoomHandler = com.patrykandpatrick.vico.views.cartesian.ZoomHandler(
            zoomEnabled = false, 
            initialZoom = com.patrykandpatrick.vico.core.cartesian.Zoom.Content
        )
        chartView.scrollHandler = com.patrykandpatrick.vico.views.cartesian.ScrollHandler(scrollEnabled = false)
        
        chartView.modelProducer = modelProducer
        chartView.postInvalidate()
    }

    /** 
     * Tekent de 52-weken curve met de harde 53-punten buffer (VOORKOMT GATEN).
     */
    suspend fun setupWeekly(chartView: CartesianChartView, distribution: List<WeekCountRow>) {
        // SQLite %W levert 00 t/m 53. We mappen dit naar een buffer van 54 punten.
        val buffer = FloatArray(54)
        distribution.forEach { row ->
            if (row.week in 0..53) buffer[row.week] = row.count.toFloat()
        }
        setup(chartView, buffer.toList())
    }

    /** Legacy ondersteuning (wordt gemapt naar 12-maand as voor v1 compatibiliteit) */
    suspend fun setup(chartView: CartesianChartView, distribution: List<com.yvesds.vt5.core.database.dao.MonthCountRow>, teldagMonthX: Float) {
        val buffer = FloatArray(13)
        distribution.forEach { if (it.month in 1..12) buffer[it.month] = it.count.toFloat() }
        setup(chartView, buffer.toList())
    }
}
