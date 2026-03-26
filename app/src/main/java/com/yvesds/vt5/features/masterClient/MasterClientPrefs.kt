package com.yvesds.vt5.features.masterClient

import android.content.Context
import androidx.core.content.edit

/**
 * MasterClientPrefs – centraal beheer van master/client-modus instellingen.
 *
 * Modus:
 *   SOLO   = klassieke standalone werking (default)
 *   MASTER = dit toestel is de master die een lokale server draait en records ontvangt
 *   CLIENT = dit toestel verbindt als client naar een master
 */
object MasterClientPrefs {

    private const val PREFS_NAME     = "vt5_mc_prefs"

    const val MODE_SOLO   = "solo"
    const val MODE_MASTER = "master"
    const val MODE_CLIENT = "client"

    private const val KEY_MODE         = "mc_mode"
    private const val KEY_CLIENT_ID    = "mc_client_id"
    private const val KEY_MASTER_IP    = "mc_master_ip"
    private const val KEY_MASTER_PORT  = "mc_master_port"
    private const val KEY_BOOTSTRAP_SESSION = "mc_bootstrap_session"
    private const val KEY_SESSION_TOKEN = "mc_session_token"

    const val DEFAULT_PORT = 50234

    // ─── Mode ─────────────────────────────────────────────────────────────────

    @Volatile
    private var runtimeMode: String? = null

    fun getMode(context: Context): String =
        runtimeMode ?: prefs(context).getString(KEY_MODE, MODE_SOLO).orEmpty().ifBlank { MODE_SOLO }

    fun setMode(context: Context, mode: String) {
        require(mode in listOf(MODE_SOLO, MODE_MASTER, MODE_CLIENT)) {
            "Ongeldige modus '$mode'. Geldige waarden: $MODE_SOLO, $MODE_MASTER, $MODE_CLIENT"
        }
        runtimeMode = mode
        prefs(context).edit { putString(KEY_MODE, mode) }
    }

    fun resetModeToSolo() {
        runtimeMode = MODE_SOLO
    }

    // ─── Client identity ──────────────────────────────────────────────────────

    /**
     * Unieke client-ID (stable per installatie).
     * Wordt aangemaakt bij eerste gebruik en daarna hergebruikt.
     */
    fun getClientId(context: Context): String {
        val prefs = prefs(context)
        val existing = prefs.getString(KEY_CLIENT_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val newId = java.util.UUID.randomUUID().toString()
        prefs.edit { putString(KEY_CLIENT_ID, newId) }
        return newId
    }

    // ─── Master-verbinding (client-zijde) ─────────────────────────────────────

    fun getMasterIp(context: Context): String =
        prefs(context).getString(KEY_MASTER_IP, "") ?: ""

    fun setMasterIp(context: Context, ip: String) =
        prefs(context).edit { putString(KEY_MASTER_IP, ip) }

    fun getMasterPort(context: Context): Int =
        prefs(context).getInt(KEY_MASTER_PORT, DEFAULT_PORT)

    fun setMasterPort(context: Context, port: Int) =
        prefs(context).edit { putInt(KEY_MASTER_PORT, port) }

    fun getSessionToken(context: Context): String =
        prefs(context).getString(KEY_SESSION_TOKEN, "") ?: ""

    fun setSessionToken(context: Context, token: String) =
        prefs(context).edit { putString(KEY_SESSION_TOKEN, token) }

    fun getBootstrapSession(context: Context): String =
        prefs(context).getString(KEY_BOOTSTRAP_SESSION, "") ?: ""

    fun setBootstrapSession(context: Context, session: String) =
        prefs(context).edit { putString(KEY_BOOTSTRAP_SESSION, session) }

    fun clearSession(context: Context) {
        prefs(context).edit {
            remove(KEY_SESSION_TOKEN)
            remove(KEY_MASTER_IP)
            remove(KEY_BOOTSTRAP_SESSION)
        }
    }


    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
