package com.yvesds.vt5.core.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import java.util.Calendar

/**
 * HourlyAlarmManager
 *
 * Beheert de gedeelde voorkeur voor het uurlijkse alarm en ruimt eventuele
 * oude AlarmManager-registraties op.
 *
 * De effectieve alarmtrigger loopt nu via de schermgebonden
 * `TellingAlarmHandler`, zodat het alarm alleen actief is terwijl de app
 * daadwerkelijk in gebruik is.
 */
object HourlyAlarmManager {
    private const val TAG = "HourlyAlarmManager"
    
    // SharedPreferences keys
    private const val PREFS_NAME = "vt5_prefs"
    private const val PREF_HOURLY_ALARM_ENABLED = "pref_hourly_alarm_enabled"
    private const val PREF_TELLING_ID = "pref_telling_id"
    
    // Request code for PendingIntent
    private const val ALARM_REQUEST_CODE = 1001
    
    /**
     * Controleert of het uurlijkse alarm is ingeschakeld
     */
    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_HOURLY_ALARM_ENABLED, true) // Standaard aan
    }
    
    /**
     * Schakelt het uurlijkse alarm in of uit
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean(PREF_HOURLY_ALARM_ENABLED, enabled)
        }
        
        if (enabled) {
            Log.i(TAG, "Uurlijks alarm ingeschakeld (alleen tijdens actieve TellingScherm-sessie)")
        } else {
            cancelAlarm(context)
            Log.i(TAG, "Uurlijks alarm uitgeschakeld")
        }
    }

    /**
     * Verwijdert een eventueel nog ingepland legacy-alarm zonder de ingestelde
     * voorkeur te wijzigen.
     */
    fun cancelScheduledAlarm(context: Context) {
        cancelAlarm(context)
    }
    
    /**
     * Plant het volgende legacy alarm op de 00e minuut van het huidige of volgende uur.
     *
     * Let op: de huidige app-flow gebruikt dit pad niet meer automatisch.
     * De schermgebonden `TellingAlarmHandler` is de leidende implementatie voor
     * actieve tellingen.
     */
    fun scheduleNextAlarm(context: Context) {
        if (!isEnabled(context)) {
            return
        }
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, HourlyAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Bereken de volgende 00e minuut
        val calendar = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.MINUTE, 0)
            
            // Als we al voorbij de 00e minuut zijn, ga naar het volgende uur
            if (before(Calendar.getInstance())) {
                add(Calendar.HOUR_OF_DAY, 1)
            }
        }
        
        val triggerTime = calendar.timeInMillis
        
        // Gebruik exact alarm voor betrouwbare timing (minSdk = 33, dus altijd setExactAndAllowWhileIdle)
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            
            Log.i(TAG, "Volgend alarm gepland voor: ${calendar.time}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Geen toestemming voor exacte alarms: ${e.message}", e)
        }
    }
    
    /**
     * Annuleert het geplande alarm
     */
    private fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, HourlyAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)
        Log.i(TAG, "Alarm geannuleerd")
    }
    
    /**
     * Controleert of een telling momenteel actief is
     */
    private fun isTellingActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val tellingId = prefs.getString(PREF_TELLING_ID, null)
        return !tellingId.isNullOrEmpty()
    }
    
    /**
     * BroadcastReceiver die het alarm afhandelt
     */
    class HourlyAlarmReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "Uurlijks alarm ontvangen")
            
            // Speel geluid en vibreer via gedeelde helper
            AlarmSoundHelper.playAlarmSound(context)
            AlarmSoundHelper.vibrate(context)
            
            // Als een telling actief is, toon HuidigeStandScherm
            if (isTellingActive(context)) {
                showHuidigeStandScherm(context)
            }
            
            // Plan het volgende alarm
            scheduleNextAlarm(context)
        }
        
        /**
         * Toont het HuidigeStandScherm met de huidige telling data
         * 
         * Deze functie brengt TellingScherm naar de voorgrond en triggert
         * het tonen van HuidigeStandScherm via een speciale Intent extra.
         */
        private fun showHuidigeStandScherm(context: Context) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val tellingId = prefs.getString(PREF_TELLING_ID, null)
                
                if (tellingId.isNullOrEmpty()) {
                    Log.w(TAG, "Geen actieve telling gevonden")
                    return
                }
                
                // Breng TellingScherm naar voren met een speciale flag
                // TellingScherm's onNewIntent zal dit oppakken en HuidigeStandScherm tonen
                val intent = Intent(context, com.yvesds.vt5.features.telling.TellingScherm::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                           Intent.FLAG_ACTIVITY_SINGLE_TOP or
                           Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    putExtra(com.yvesds.vt5.features.telling.TellingScherm.EXTRA_SHOW_HUIDIGE_STAND, true)
                }
                
                context.startActivity(intent)
                Log.i(TAG, "TellingScherm naar voorgrond gebracht met HuidigeStand trigger")
            } catch (e: Exception) {
                Log.e(TAG, "Fout bij tonen van telling scherm: ${e.message}", e)
            }
        }
    }
    
    /**
     * BroadcastReceiver voor het herstarten van het alarm na device reboot
     */
    class BootReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                Log.i(TAG, "Device opgestart, alarm herschedulen")
                scheduleNextAlarm(context)
            }
        }
    }
}
