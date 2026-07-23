package com.yvesds.vt5.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yvesds.vt5.R
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import com.yvesds.vt5.hoofd.HoofdActiviteit
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * SplashActiviteit - Toont het VT5 logo tijdens het opstarten
 * 
 * Deze activity toont het vt5.png logo groot en gecentreerd op een donkere achtergrond.
 * Na een korte vertraging navigeert het automatisch naar HoofdActiviteit.
 */
@SuppressLint("CustomSplashScreen")
class SplashActiviteit : AppCompatActivity() {
    
    companion object {
        /**
         * Duur van de splash screen in milliseconden.
         * Dit geeft tijd om data in de achtergrond te laden.
         */
        private const val SPLASH_DURATION_MS = 2000L
    }
    
    private var splashJob: Job? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_splash)
        
        // Voorkom dat de gebruiker terug kan naar de splash screen
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Negeer back button tijdens splash screen
            }
        })
        
        // Navigeer naar HoofdActiviteit na de splash duur en data-load
        splashJob = lifecycleScope.launch {
            val startedAt = System.currentTimeMillis()
            val pb = findViewById<android.widget.ProgressBar>(R.id.pbLoading)
            
            // Wacht op zowel aliassen als serverdata (parallel)
            withTimeoutOrNull(10_000L) {
                val aliasJob = async { VT5App.awaitStartupAliasRefresh() }
                val dataJob = async { 
                    try {
                        ServerDataCache.getOrLoad(this@SplashActiviteit)
                    } catch (e: Exception) {
                        android.util.Log.w("Splash", "ServerData preload failed: ${e.message}")
                    }
                }
                aliasJob.await()
                dataJob.await()
            }

            // AI model preload temporarily disabled to avoid heavy startup IO.
            // We postpone AI integration to a later release per request.
            val aiError: String? = null

            // Data is geladen: verberg progressbar
            pb?.visibility = android.view.View.INVISIBLE
            
            val elapsed = System.currentTimeMillis() - startedAt
            val remaining = (SPLASH_DURATION_MS - elapsed).coerceAtLeast(0L)
            if (remaining > 0L) {
                delay(remaining)
            }
            navigateToMain(aiError)
        }
    }
    
    override fun onDestroy() {
        splashJob?.cancel()
        super.onDestroy()
    }
    
    private fun navigateToMain(aiError: String? = null) {
        if (!isFinishing && !isDestroyed) {
            val intent = Intent(this, HoofdActiviteit::class.java)
            if (aiError != null) {
                intent.putExtra("ai_model_loaded", false)
                intent.putExtra("ai_model_error", aiError)
            } else {
                // only communicate success flag if model was actually loaded
                if (com.yvesds.vt5.ai.ModelManager.getLoadedModel() != null) {
                    intent.putExtra("ai_model_loaded", true)
                }
            }
            startActivity(intent)
            finish()
        }
    }
}
