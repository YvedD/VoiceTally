package com.yvesds.vt5.features.masterClient

import android.util.Log
import java.security.SecureRandom

/**
 * PairingManager – genereert en valideert tijdelijke PIN-codes voor master-client pairing.
 *
 * De master genereert een 6-cijferige PIN die zichtbaar wordt getoond op het scherm.
 * Een client voert deze PIN in om een sessietoken te ontvangen.
 * PIN's hebben een zachte vervaldatum: als de PIN verlopen is maar nog klopt,
 * wordt de geldigheid verlengd zodat late tellers nog kunnen aansluiten.
 * De PIN blijft bruikbaar tot de master expliciet een nieuwe PIN genereert.
 */
class PairingManager {

    private val TAG = "PairingManager"
    private val rng = SecureRandom()

    data class PairingSession(
        val pin: String,
        val sessionToken: String,
        val expiresAt: Long          // epoch ms
    )

    // Actieve PIN-sessie (null = geen actieve pairing)
    @Volatile
    private var activePairingSession: PairingSession? = null

    // Geautoriseerde sessie-tokens → clientId
    private val authorizedTokens = mutableMapOf<String, String>()   // token → clientId

    companion object {
        private const val PIN_LENGTH_DIGITS = 6
        private const val PIN_VALIDITY_MS   = 10 * 60 * 1000L    // 10 minuten
    }

    // ─── PIN generatie ────────────────────────────────────────────────────────

    /**
     * Genereer een nieuwe 6-cijferige PIN en sla op als actieve pairing-sessie.
     * Eerder aangemaakte PIN's worden ongeldig.
     * @return de gegenereerde PIN als String
     */
    fun generatePin(): String {
        val pin = (0 until PIN_LENGTH_DIGITS)
            .map { rng.nextInt(10) }
            .joinToString("")
        val token = generateToken()
        activePairingSession = PairingSession(
            pin       = pin,
            sessionToken = token,
            expiresAt = System.currentTimeMillis() + PIN_VALIDITY_MS
        )
        Log.d(TAG, "Nieuwe PIN gegenereerd (geldig 10 min)")
        return pin
    }

    /** Geeft de huidige PIN terug (of null als er geen actieve sessie is). */
    fun getCurrentPin(): String? = activePairingSession?.pin

    // ─── PIN validatie (master-zijde) ─────────────────────────────────────────

    /**
     * Valideer een ingevoerde PIN.
     * Bij succes wordt een sessietoken aangemaakt en teruggestuurd.
     * @return Pair(accepted, sessionToken) – sessionToken is leeg bij afwijzing
     */
    fun validatePin(pin: String, clientId: String): Pair<Boolean, String> {
        val session = activePairingSession
        if (session == null) {
            Log.w(TAG, "Geen actieve PIN-sessie voor validatie")
            return Pair(false, "")
        }
        if (!pin.equals(session.pin, ignoreCase = false)) {
            Log.w(TAG, "Ongeldige PIN ingevoerd door client $clientId")
            return Pair(false, "")
        }
        if (System.currentTimeMillis() > session.expiresAt) {
            Log.i(TAG, "PIN verlopen, geldigheid wordt verlengd voor late aansluiting")
            activePairingSession = session.copy(
                expiresAt = System.currentTimeMillis() + PIN_VALIDITY_MS
            )
        }
        // Genereer een uniek session-token voor deze client
        val token = generateToken()
        synchronized(authorizedTokens) {
            authorizedTokens[token] = clientId
        }
        Log.d(TAG, "Client $clientId gekoppeld met geldig token")
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

    private fun generateToken(): String =
        java.util.UUID.randomUUID().toString().replace("-", "")
}
