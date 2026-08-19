package com.yvesds.vt5.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yvesds.vt5.R
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import com.yvesds.vt5.hoofd.HoofdActiviteit
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * SplashActiviteit - Toont het VT5 logo tijdens het opstarten
 */
@SuppressLint("CustomSplashScreen")
class SplashActiviteit : AppCompatActivity() {
    
    companion object {
        private const val SPLASH_DURATION_MS = 4500L
    }
    
    private var splashJob: Job? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_splash)
        
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })
        
        splashJob = lifecycleScope.launch {
            val startedAt = System.currentTimeMillis()
            val pb = findViewById<android.widget.ProgressBar>(R.id.pbLoading)
            val containerAi = findViewById<android.view.View>(R.id.containerAiLogo)
            val ivSplash = findViewById<android.widget.ImageView>(R.id.ivSplashLogo)
            val tvBsi = findViewById<android.widget.TextView>(R.id.tvBsiTitle)

            tvBsi?.let {
                val fullText = "Bio Statistic Intelligence"
                val spannable = android.text.SpannableString(fullText)
                spannable.setSpan(android.text.style.RelativeSizeSpan(1.5f), 0, 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(android.text.style.ForegroundColorSpan(android.graphics.Color.WHITE), 0, 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(android.text.style.RelativeSizeSpan(1.5f), 4, 5, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(android.text.style.ForegroundColorSpan(android.graphics.Color.WHITE), 4, 5, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(android.text.style.RelativeSizeSpan(1.5f), 14, 15, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(android.text.style.ForegroundColorSpan(android.graphics.Color.WHITE), 14, 15, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                it.text = spannable
            }

            delay(1200.milliseconds)
            if (!isActive) return@launch

            ivSplash?.animate()?.alpha(0f)?.setDuration(1250)?.start()
            delay(1250.milliseconds)
            if (!isActive) return@launch
            
            containerAi?.animate()?.alpha(1.0f)?.setDuration(1250)?.start()
            
            withTimeoutOrNull(10000.milliseconds) {
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

            pb?.visibility = android.view.View.INVISIBLE
            
            val elapsed = System.currentTimeMillis() - startedAt
            val remaining = (SPLASH_DURATION_MS - elapsed).coerceAtLeast(0L)
            if (remaining > 0L) {
                delay(remaining.milliseconds)
            }
            
            if (isActive) {
                navigateToMain()
            }
        }
    }
    
    override fun onDestroy() {
        splashJob?.cancel()
        super.onDestroy()
    }
    
    private fun navigateToMain() {
        if (!isFinishing && !isDestroyed) {
            val intent = Intent(this, HoofdActiviteit::class.java)
            if (com.yvesds.vt5.ai.ModelManager.getLoadedModel() != null) {
                intent.putExtra("ai_model_loaded", true)
            }
            startActivity(intent)
            finish()
        }
    }
}
