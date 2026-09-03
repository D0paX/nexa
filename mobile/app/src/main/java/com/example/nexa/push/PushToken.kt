package com.example.nexa.push

import java.security.MessageDigest

/**
 * An FCM registration token.
 *
 * A token identifies a *delivery endpoint*. It is not a user, not a device
 * identity, not a credential and not an authorization — possessing one says
 * only that messages can be routed somewhere, and NEXA never treats it as
 * evidence of anything else.
 *
 * The raw value is private and [toString] returns a fingerprint, so the
 * ordinary ways a secret escapes — a log line, an exception message, a
 * debug string template, a crash report — cannot leak it. Reading the real
 * value takes a deliberate call to [expose], which exists for exactly one
 * caller: the registrar that hands it to the backend.
 */
class PushToken(private val raw: String) {

    init {
        require(raw.isNotBlank()) { "token must not be blank" }
    }

    /**
     * A stable, non-reversible identifier for this token.
     *
     * Safe to log and safe to show: it lets two token values be compared for
     * equality across a support conversation without either being disclosed.
     */
    val fingerprint: String by lazy {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        digest.take(FINGERPRINT_BYTES).joinToString("") { "%02x".format(it) }
    }

    /** The real token. Only the registrar should ever call this. */
    fun expose(): String = raw

    /** Deliberately not the token. */
    override fun toString(): String = "PushToken(fp=$fingerprint)"

    override fun equals(other: Any?): Boolean = other is PushToken && other.raw == raw

    override fun hashCode(): Int = raw.hashCode()

    private companion object {
        const val FINGERPRINT_BYTES = 4
    }
}

/**
 * What NEXA knows about its own ability to receive push messages.
 *
 * Never carries the token. The UI needs to tell an operator whether delivery
 * to this device is possible, which the fingerprint answers without
 * disclosing anything.
 */
sealed interface PushTokenState {
    data object Unknown : PushTokenState

    /** A token exists and this device can be reached. */
    data class Available(val fingerprint: String) : PushTokenState

    /**
     * No token. [reason] is operator-facing and explains the situation without
     * implying the security system itself is impaired.
     */
    data class Unavailable(val reason: String) : PushTokenState
}

// ============================================================
// BACKEND REGISTRATION
// ============================================================

sealed interface PushRegistrationResult {
    data object Registered : PushRegistrationResult
    data class Failed(val reason: String) : PushRegistrationResult

    /** No backend contract exists yet. Not a failure — nothing was attempted. */
    data object NotConfigured : PushRegistrationResult
}

/**
 * Hands a registration token to the NEXA backend.
 *
 * The contract the backend must eventually provide:
 *
 *   POST   <base>/api/v1/push/registrations
 *          body: { token, platform: "android", appVersion, installationId }
 *          auth: the operator session — the token itself authenticates
 *                nothing, and the endpoint must not accept it as identity
 *          200:  registration accepted
 *
 *   DELETE <base>/api/v1/push/registrations/{installationId}
 *          removes the endpoint on sign-out or token invalidation
 *
 * Two requirements on the backend side, stated here because they are the ones
 * an implementer is most likely to get wrong:
 *
 *  1. The endpoint must authenticate the *operator*, not the token. A token
 *     is a routing address that anyone who obtains it could replay.
 *  2. Registrations must be scoped so a token cannot subscribe itself to
 *     scopes the operator cannot already see.
 *
 * No endpoint exists today, so the shipped implementation is
 * [NoOpPushTokenRegistrar] and nothing is transmitted.
 */
interface PushTokenRegistrar {
    suspend fun register(token: PushToken): PushRegistrationResult
    suspend fun unregister(): PushRegistrationResult
}

/**
 * The registrar used until the backend contract above exists.
 *
 * Deliberately inert. It does not queue, retry or persist the token, because
 * a token held for an endpoint that does not exist is a stored secret with no
 * purpose.
 */
object NoOpPushTokenRegistrar : PushTokenRegistrar {
    override suspend fun register(token: PushToken): PushRegistrationResult =
        PushRegistrationResult.NotConfigured

    override suspend fun unregister(): PushRegistrationResult =
        PushRegistrationResult.NotConfigured
}
