package com.yvesds.vt5.features.telling.controller

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.yvesds.vt5.R
import com.yvesds.vt5.core.ui.DialogStyler
import com.yvesds.vt5.core.ui.ProgressDialogHelper
import com.yvesds.vt5.features.birdnet.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BirdNetController @Inject constructor(
    private val application: Application,
    private val scope: CoroutineScope,
    private val sseClient: BirdNetSseClient
) {
    private val TAG = "BirdNetController"

    private val _tickerText = MutableStateFlow("")
    val tickerText: StateFlow<String> = _tickerText.asStateFlow()

    private var tickerJob: Job? = null

    fun startTicker() {
        val config = BirdNetConfig.load()
        if (tickerJob != null) {
            tickerJob?.cancel()
        }

        if (!config.isConfigured) {
            _tickerText.value = application.getString(R.string.telling_birdnet_pending_not_configured)
            return
        }

        tickerJob = scope.launch {
            while (isActive) {
                _tickerText.value = application.getString(
                    R.string.telling_birdnet_pending_connecting,
                    config.displayLabel.ifBlank { config.host }
                )

                var receivedConnectedEvent = false

                try {
                    sseClient.streamEvents(config) { event ->
                        when (event) {
                            is BirdNetSseClient.SseEvent.Connected -> {
                                receivedConnectedEvent = true
                                _tickerText.value = application.getString(R.string.telling_birdnet_pending_waiting)
                            }
                            is BirdNetSseClient.SseEvent.Pending -> {
                                _tickerText.value = formatBirdNetPendingTickerText(event.detections)
                            }
                            is BirdNetSseClient.SseEvent.ConnectionError -> {
                                Log.w(TAG, "BirdNET pending ticker verbinding fout: ${event.message}")
                            }
                            else -> Unit
                        }
                    }
                } catch (ex: Exception) {
                    if (!isActive) break
                    Log.w(TAG, "BirdNET pending ticker gestopt: ${ex.message}", ex)
                }

                if (!isActive) break

                _tickerText.value = application.getString(R.string.telling_birdnet_pending_reconnecting)
                delay(if (receivedConnectedEvent) 1_500L else 4_000L)
            }
        }
    }

    fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    fun handleBirdNetPressed(activity: AppCompatActivity) {
        val config = BirdNetConfig.load()
        val message = if (config.isConfigured) {
            activity.getString(R.string.telling_birdnet_dialog_current, config.displayLabel)
        } else {
            activity.getString(R.string.telling_birdnet_dialog_not_configured)
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.telling_birdnet_dialog_title)
            .setMessage(message)
            .setPositiveButton(R.string.telling_birdnet_action_discover) { _, _ ->
                discoverBirdNetHost(activity)
            }
            .setNeutralButton(R.string.telling_birdnet_action_check) { _, _ ->
                checkCurrentBirdNetHost(activity)
            }
            .setNegativeButton(R.string.telling_birdnet_action_clear) { _, _ ->
                BirdNetConfig.clear()
                startTicker()
                Toast.makeText(activity, activity.getString(R.string.telling_birdnet_cleared), Toast.LENGTH_SHORT).show()
            }
            .create()
        DialogStyler.apply(dialog)
        dialog.show()
    }

    private fun discoverBirdNetHost(activity: AppCompatActivity) {
        scope.launch {
            val progressDialog = withContext(Dispatchers.Main) {
                ProgressDialogHelper.show(
                    activity,
                    activity.getString(R.string.telling_birdnet_discover_progress)
                )
            }

            try {
                val found = withContext(Dispatchers.IO) {
                    BirdNetDiscovery.discover(activity)
                }

                withContext(Dispatchers.Main) {
                    if (found != null) {
                        BirdNetConfig.save(found.toConfig())
                        startTicker()
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.telling_birdnet_discover_found, found.displayLabel),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.telling_birdnet_discover_not_found),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (ex: Exception) {
                Log.w(TAG, "BirdNET auto-discover mislukt: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.telling_birdnet_discover_not_found),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                }
            }
        }
    }

    private fun checkCurrentBirdNetHost(activity: AppCompatActivity) {
        val config = BirdNetConfig.load()
        if (!config.isConfigured) {
            Toast.makeText(activity, activity.getString(R.string.telling_birdnet_dialog_not_configured), Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            val reachable = withContext(Dispatchers.IO) {
                BirdNetDiscovery.pingHost(config)
            }

            withContext(Dispatchers.Main) {
                if (reachable) {
                    startTicker()
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.telling_birdnet_host_online, config.displayLabel),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.telling_birdnet_host_offline, config.displayLabel),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun formatBirdNetPendingTickerText(detections: List<BirdNetPendingDetection>): String {
        if (detections.isEmpty()) {
            return application.getString(R.string.telling_birdnet_pending_waiting)
        }

        return detections
            .sortedWith(
                compareByDescending<BirdNetPendingDetection> { it.maxConfidence }
                    .thenByDescending { it.confidence }
                    .thenByDescending { it.hitCount }
            )
            .take(6)
            .joinToString(separator = "   •   ") { detection ->
                application.getString(
                    R.string.telling_birdnet_pending_item,
                    detection.displayName,
                    detection.displayConfidencePct,
                    detection.hitCount.coerceAtLeast(1)
                )
            }
    }
}
