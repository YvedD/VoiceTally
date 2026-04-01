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
        val sessionId: String
    )

    // Actieve PIN-sessie (null = geen actieve pairing)
    @Volatile
    private var activePairingSession: PairingSession? = null

    // Geautoriseerde sessie-tokens → clientId
    private val authorizedTokens = mutableMapOf<String, String>()   // token → clientId

    // ─── QR bootstrap-sessie ─────────────────────────────────────────────────

    /**
     * Geef de actieve sticky QR-sessie terug, of maak er één aan als die nog niet bestaat.
     * Deze bootstrap-sessie blijft geldig zolang de master-samenwerking actief blijft,
     * tenzij expliciet geroteerd via [rotatePairingSession].
     */
    fun openPairingSession(): String {
        activePairingSession?.let { active ->
            Log.d(TAG, "Bestaande sticky QR-sessie hergebruikt")
            return active.sessionId
        }

        val sessionId = generateBootstrapSessionId()
        activePairingSession = PairingSession(sessionId = sessionId)
        Log.d(TAG, "Nieuwe sticky QR-sessie geopend")
        return sessionId
    }

    /**
     * Forceer een nieuwe bootstrap-QR zonder bestaande sessietokens van gekoppelde clients te raken.
     */
    fun rotatePairingSession(): String {
        val sessionId = generateBootstrapSessionId()
        activePairingSession = PairingSession(sessionId = sessionId)
        Log.d(TAG, "Sticky QR-sessie handmatig vernieuwd")
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
    fun validateSession(sessionId: String, clientId: String, reconnectToken: String = ""): Pair<Boolean, String> {
        if (reconnectToken.isNotBlank()) {
            synchronized(authorizedTokens) {
                val authorizedClientId = authorizedTokens[reconnectToken]
                if (authorizedClientId == clientId) {
                    Log.d(TAG, "Client $clientId opnieuw gekoppeld met bestaand sessietoken")
                    return Pair(true, reconnectToken)
                }
            }
        }

        val session = activePairingSession
        if (session == null) {
            Log.w(TAG, "Geen actieve QR-sessie voor client $clientId")
            return Pair(false, "")
        }
        if (!sessionId.equals(session.sessionId, ignoreCase = false)) {
            Log.w(TAG, "Ongeldige QR-sessie ontvangen van client $clientId")
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
