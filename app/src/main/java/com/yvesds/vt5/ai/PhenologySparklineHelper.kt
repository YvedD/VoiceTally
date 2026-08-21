package com.yvesds.vt5.ai

import android.graphics.Color
import com.patrykandpatrick.vico.core.cartesian.CartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.views.cartesian.CartesianChartView
import com.yvesds.vt5.core.database.dao.MonthCountRow

/**
 * PhenologySparklineHelper - Modulaire helper voor het tekenen van de dubbele klok-curve.
 */
object PhenologySparklineHelper {

    suspend fun setup(chartView: CartesianChartView, distribution: List<MonthCountRow>, currentMonth: Int) {
        val modelProducer = CartesianChartModelProducer()
        
        val fullYear = (1..12).map { month ->
            distribution.find { it.month == month }?.count?.toFloat() ?: 0f
        }

        modelProducer.runTransaction {
            lineSeries { series(fullYear) }
        }

        val lineLayer = LineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.Line(
                    fill = LineCartesianLayer.LineFill.single(Fill(Color.parseColor("#4CAF50"))),
                    thicknessDp = 1.5f,
                    areaFill = LineCartesianLayer.AreaFill.single(Fill(Color.parseColor("#1A4CAF50")))
                )
            )
        )

        chartView.chart = CartesianChart(
            layers = arrayOf(lineLayer)
        )

        chartView.modelProducer = modelProducer
    }
}
