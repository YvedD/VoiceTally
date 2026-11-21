package com.yvesds.vt5.ai

import kotlin.math.exp
import kotlin.math.ln

/**
 * BayesianScoringEngine - BSI 5.0 Bayesiaans Log-Odds Agregatiemodel.
 * Vervangt de ruwe vermenigvuldigingsketting door een wiskundig zuivere
 * Log-Odds optelling van Likelihood Ratios met Sigmoïde S-curve.
 * Voorkomt score-explosies (>98%) en irreële veto-blokkades.
 */
object BayesianScoringEngine {

    private const val MIN_PROB = 0.0001
    private const val MAX_PROB = 0.9999

    /**
     * Omzetten van kans (0.0..1.0) naar Log-Odds (logit).
     */
    fun logit(p: Double): Double {
        val clamped = p.coerceIn(MIN_PROB, MAX_PROB)
        return ln(clamped / (1.0 - clamped))
    }

    /**
     * Omzetten van Log-Odds naar kans (0.0..1.0) via Sigmoïde S-curve.
     */
    fun sigmoid(logOdds: Double): Double {
        val clamped = logOdds.coerceIn(-15.0, 15.0)
        return 1.0 / (1.0 + exp(-clamped))
    }

    /**
     * Bereken de uiteindelijke Bayesiaanse kans (0..100%) op basis van Prior en Likelihoods.
     */
    fun calculatePosteriorProbability(
        priorBaselineBph: Double,
        windMatchFactor: Double,
        efficiencyRatio: Double,
        specialBoost: Double,
        timeMatchFactor: Double,
        gatekeeperFactor: Double,
        twinFactor: Double,
        corridorBoost: Double,
        dailyWeight: Double,
        neuralProb: Double?,
        tensorFactor: Double?
    ): Int {
        // 1. Prior Log-Odds (gebaseerd op seizoensnorm/BpH)
        val priorProb = (priorBaselineBph / 50.0).coerceIn(0.01, 0.80)
        val priorLogOdds = logit(priorProb)

        // 2. Likelihood Ratios (Log-Odds bijdragen)
        val llWind = ln(windMatchFactor.coerceAtLeast(0.05)) * 0.7
        val llEfficiency = ln(efficiencyRatio.coerceAtLeast(0.05)) * 0.5
        val llSpecial = ln(specialBoost.coerceAtLeast(1.0)) * 0.8
        val llTime = ln(timeMatchFactor.coerceAtLeast(0.01)) * 0.6
        val llGatekeeper = ln(gatekeeperFactor.coerceAtLeast(0.01)) * 0.9
        val llTwin = ln(twinFactor.coerceAtLeast(0.5)) * 0.4
        val llCorridor = ln(1.0 + corridorBoost) * 0.6
        val llDaily = ln(dailyWeight.coerceAtLeast(0.5)) * 0.5

        val llNeural = if (neuralProb != null && neuralProb > 0.0) {
            logit(neuralProb.coerceIn(0.01, 0.95)) * 0.4
        } else 0.0

        val llTensor = if (tensorFactor != null && tensorFactor > 0.0) {
            ln(tensorFactor.coerceAtLeast(0.1)) * 0.35
        } else 0.0

        // 3. Totale Posterior Log-Odds
        val totalLogOdds = priorLogOdds + llWind + llEfficiency + llSpecial + llTime +
                llGatekeeper + llTwin + llCorridor + llDaily + llNeural + llTensor

        // 4. Sigmoïde transformatie naar 0-100%
        val posterior = sigmoid(totalLogOdds)
        return (posterior * 98.0).toInt().coerceIn(1, 98)
    }
}
