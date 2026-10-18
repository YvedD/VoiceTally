/**
 * BirdNetIntegrationHandler.kt
 *
 * Helper klasse voor BirdNET-GO integratie (voorheen inline in TellingScherm).
 * Voorziet het icoon-clickgebeurtenishandler en SSE-streamafhandeling.
 *
 * NOTE: De meeste functionaliteit is nu direct in TellingScherm geplaatst via
 * startBirdNetPendingTickerIfNeeded(), startBirdNetPendingTickerIfNeeded(), etc.
 */
package com.yvesds.vt5.features.telling

import android.content.Context
import android.util.Log

/**
 * Placeholder class - de BirdNET-GO integratie is nu volledig geimplementeerd
 * rechtstreeks in TellingScherm.kt via de methoden:
 * - handleBirdNetPressed()
 * - discoverBirdNetHost()
 * - showManualBirdNetConfigDialog()
 * - startBirdNetPendingTickerIfNeeded()
 * - stopBirdNetPendingTicker()
 * - formatBirdNetPendingTickerText()
 */
object BirdNetIntegrationHandler {
    private const val TAG = "BirdNetIntegration"

    init {
        Log.i(TAG, "BirdNET-GO integratie is volledig geimplementeerd in TellingScherm.kt")
    }
}

