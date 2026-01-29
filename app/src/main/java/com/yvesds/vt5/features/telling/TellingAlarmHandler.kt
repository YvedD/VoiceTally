package com.yvesds.vt5.features.telling

import android.content.Context
import android.util.Log
import com.yvesds.vt5.core.app.AlarmSoundHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * TellingAlarmHandler
 * 
 * Beheert het uurlijkse alarm direct binnen TellingScherm.
 * Controleert op de 59ste minuut en triggert:
 * - Geluid afspelen (bell.mp3 of systeem notificatie)
 * - Vibratie
 * - Callback naar TellingScherm om HuidigeStandScherm te tonen
 * 
 * Dit is een directere en betrouwbaardere aanpak dan de BroadcastReceiver-gebaseerde
 * HourlyAlarmManager, omdat het alarm getriggerd wordt terwijl de Activity actief is.
 */
class TellingAlarmHandler(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "TellingAlarmHandler"
        private const val PREFS_NAME = "vt5_prefs"
        private const val PREF_HOURLY_ALARM_ENABLED = "pref_hourly_alarm_enabled"
        private const val TARGET_MINUTE = 0 // Alarm op minuut 00 (begin van elk uur)
    }
    
    // Job voor de periodieke check
    private var alarmCheckJob: Job? = null
    
    // Houdt bij voor welk uur we al een alarm hebben getriggerd (voorkomt dubbele triggers)
    private var lastTriggeredHour: Int = -1
    
    // Callback die wordt aangeroepen wanneer het alarm afgaat
    var onAlarmTriggered: (() -> Unit)? = null
    
    /**
     * Start de periodieke controle op minuut 00.
     * Moet aangeroepen worden in onCreate of onResume van TellingScherm.
     */
    fun startMonitoring() {
        if (alarmCheckJob?.isActive == true) {
            Log.i(TAG, "Alarm monitoring loopt al")
            return
        }
        
        Log.i(TAG, "Start alarm monitoring")
        alarmCheckJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val delayMs = checkAndTriggerAlarm()
                delay(delayMs)
            }
        }
    }
    
    /**
     * Stop de periodieke controle.
     * Moet aangeroepen worden in onPause of onDestroy van TellingScherm.
     */
    fun stopMonitoring() {
        Log.i(TAG, "Stop alarm monitoring")
        alarmCheckJob?.cancel()
        alarmCheckJob = null
    }
    
    /**
     * Controleer of het tijd is voor het alarm en trigger indien nodig.
     * 
     * @return milliseconden om te wachten tot de volgende check.
     *         Optimaliseert door te wachten tot vlak voor de volgende minuutwisseling.
     */
    private fun checkAndTriggerAlarm(): Long {
        if (!isEnabled()) {
            // Als alarm uitgeschakeld, check elke 30 seconden of het weer ingeschakeld is
            return 30_000L
        }
        
        val calendar = Calendar.getInstance()
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentSecond = calendar.get(Calendar.SECOND)
        
        // Trigger alleen op minuut 00 en niet als we dit uur al getriggerd hebben
        if (currentMinute == TARGET_MINUTE && lastTriggeredHour != currentHour) {
            lastTriggeredHour = currentHour
            Log.i(TAG, "Alarm getriggerd om ${calendar.time}")
            
            // Trigger alarm op Main thread
            scope.launch(Dispatchers.Main) { triggerAlarm() }
        }

        // Bereken optimale wachttijd tot de volgende check
        return calculateDelayUntilNextCheck(currentMinute, currentSecond)
    }
    
    /**
     * Bereken hoeveel milliseconden te wachten tot de volgende check.
     * We willen zo dicht mogelijk rond minuut 00 checken.
     * - Als we niet op minuut 00 zijn: wacht tot net voor de volgende uurwisseling.
     * - Als we op minuut 00 zijn: wacht tot de volgende minuut (of iets later) om dubbele triggers te vermijden.
     */
    private fun calculateDelayUntilNextCheck(currentMinute: Int, currentSecond: Int): Long {
        return when {
            currentMinute == TARGET_MINUTE -> {
                // We zitten op minuut 00: check opnieuw over ~1 minuut
                val secondsToWait = 60 - currentSecond + 2 // +2s marge
                (secondsToWait * 1000L).coerceAtLeast(1000L)
            }
            else -> {
                // Wacht tot de volgende uurwisseling (minuut 00) minus een kleine marge
                val minutesToWait = 59 - currentMinute
                val secondsToWait = (60 - currentSecond) + (minutesToWait * 60) - 2
                (secondsToWait * 1000L).coerceAtLeast(1000L)
            }
        }
    }
    
    /**
     * Trigger het alarm: speel geluid, vibreer, en roep de callback aan.
     */
    private fun triggerAlarm() {
        Log.i(TAG, "Voer alarm uit: geluid + vibratie + HuidigeStandScherm")
        
        // Speel geluid af en vibreer via gedeelde helper
        AlarmSoundHelper.playAlarmSound(context)
        AlarmSoundHelper.vibrate(context)
        
        // Roep callback aan om HuidigeStandScherm te tonen
        onAlarmTriggered?.invoke()
    }
    
    /**
     * Controleer of het uurlijkse alarm is ingeschakeld.
     */
    private fun isEnabled(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_HOURLY_ALARM_ENABLED, true) // Standaard aan
    }
    
    /**
     * Cleanup resources.
     * Moet aangeroepen worden in onDestroy van TellingScherm.
     */
    fun cleanup() {
        stopMonitoring()
    }
}
