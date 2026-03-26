package com.yvesds.vt5.features.masterClient

import android.util.Log
import java.security.SecureRandom

/**
 * PairingManager – beheert tijdelijke QR-sessies voor master-client pairing.
 *
 * De master genereert een tijdelijke sessie-ID die in de QR-code terechtkomt.
 * Een client stuurt die sessie-ID terug naar de master om toegang te vragen.
 * De master kent daarna een afzonderlijk sessietoken toe voor de verdere TCP-berichten.
 */
class PairingManager {

    private val TAG = "PairingManager"
    private val rng = SecureRandom()

    data class PairingSession(
        val sessionId: String,
        val expiresAt: Long          // epoch ms
    )

    // Actieve PIN-sessie (null = geen actieve pairing)
    @Volatile
    private var activePairingSession: PairingSession? = null

    // Geautoriseerde sessie-tokens → clientId
    private val authorizedTokens = mutableMapOf<String, String>()   // token → clientId

    companion object {
        private const val SESSION_VALIDITY_MS = 10 * 60 * 1000L    // 10 minuten
    }

    // ─── QR bootstrap-sessie ─────────────────────────────────────────────────

    /**
     * Genereer een nieuwe QR-sessie en sla die op als actieve pairing-sessie.
     * Eerder aangemaakte sessies worden ongeldig.
     */
    fun openPairingSession(): String {
        val sessionId = generateBootstrapSessionId()
        activePairingSession = PairingSession(
            sessionId = sessionId,
            expiresAt = System.currentTimeMillis() + SESSION_VALIDITY_MS
        )
        Log.d(TAG, "Nieuwe QR-sessie geopend (geldig 10 min)")
        return sessionId
    }

    /** Geeft de huidige QR-sessie terug (of null als er geen actieve sessie is). */
    fun getCurrentSessionId(): String? = activePairingSession?.sessionId

    // ─── QR validatie (master-zijde) ──────────────────────────────────────────

    /**
     * Valideer een door de client teruggestuurde QR-sessie.
     * Bij succes wordt een afzonderlijk sessietoken aangemaakt en teruggestuurd.
     * @return Pair(accepted, sessionToken) – sessionToken is leeg bij afwijzing
     */
    fun validateSession(sessionId: String, clientId: String): Pair<Boolean, String> {
        val session = activePairingSession
        if (session == null) {
            Log.w(TAG, "Geen actieve QR-sessie voor client $clientId")
            return Pair(false, "")
        }
        if (!sessionId.equals(session.sessionId, ignoreCase = false)) {
            Log.w(TAG, "Ongeldige QR-sessie ontvangen van client $clientId")
            return Pair(false, "")
        }
        if (System.currentTimeMillis() > session.expiresAt) {
            Log.w(TAG, "QR-sessie verlopen voor client $clientId")
            return Pair(false, "")
        }
        val token = generateToken()
        synchronized(authorizedTokens) {
            authorizedTokens[token] = clientId
        }
        Log.d(TAG, "Client $clientId gekoppeld met geldige QR-sessie")
        return Pair(true, token)
    }

    // ─── Token validatie (per bericht) ───────────────────────────────────────

    /** Controleer of een sessietoken geldig is. */
    fun isTokenValid(token: String): Boolean =
        synchronized(authorizedTokens) { authorizedTokens.containsKey(token) }

    /** Geeft de clientId terug voor een geldig token, of null. */
    fun getClientId(token: String): String? =
        synchronized(authorizedTokens) { authorizedTokens[token] }

    /** Verwijder het token bij ontkoppeling. */
    fun revokeToken(token: String) {
        synchronized(authorizedTokens) { authorizedTokens.remove(token) }
    }

    /** Verwijder alle tokens (einde sessie). */
    fun revokeAll() {
        synchronized(authorizedTokens) { authorizedTokens.clear() }
        activePairingSession = null
    }

    /** Verbonden clientIds. */
    fun connectedClientIds(): List<String> =
        synchronized(authorizedTokens) { authorizedTokens.values.toList() }

    // ─── Hulp ─────────────────────────────────────────────────────────────────

    private fun generateBootstrapSessionId(): String {
        val bytes = ByteArray(9)
        rng.nextBytes(bytes)
        return android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
    }

    private fun generateToken(): String =
        java.util.UUID.randomUUID().toString().replace("-", "")
}
