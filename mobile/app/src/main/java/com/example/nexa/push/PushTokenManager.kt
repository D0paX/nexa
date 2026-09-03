package com.example.nexa.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the registration-token lifecycle.
 *
 * A token is a routing address for this installation. It is not an identity,
 * not a credential and not an authorization, and nothing in NEXA treats it as
 * one. It also is not permanent: Android reissues it on reinstall, on data
 * clear, and whenever Firebase decides to, so every path here assumes the
 * current token may be gone tomorrow.
 *
 * Nothing outside this object ever sees the raw value — [state] carries a
 * fingerprint, and the raw token is unwrapped only where it is handed to a
 * registrar.
 */
object PushTokenManager {

    private val _state = MutableStateFlow<PushTokenState>(PushTokenState.Unknown)
    val state: StateFlow<PushTokenState> = _state.asStateFlow()

    @Volatile
    private var registrar: PushTokenRegistrar = NoOpPushTokenRegistrar

    @Volatile
    private var current: PushToken? = null

    fun configure(registrar: PushTokenRegistrar) {
        this.registrar = registrar
    }

    /**
     * Asks Firebase for the current token.
     *
     * Every failure path is a normal outcome, not an error to surface loudly.
     * Without a google-services.json there is no default FirebaseApp and this
     * throws — which means push transport is unavailable, not that NEXA is
     * impaired. The distinction matters: an operator must never read "no push
     * token" as "the security system is down".
     */
    fun refresh() {
        val messaging = try {
            FirebaseMessaging.getInstance()
        } catch (error: IllegalStateException) {
            _state.value = PushTokenState.Unavailable(
                "Push transport is not configured for this build."
            )
            return
        } catch (error: Exception) {
            _state.value = PushTokenState.Unavailable("Push transport is unavailable.")
            return
        }

        messaging.token
            .addOnSuccessListener { token ->
                if (token.isNullOrBlank()) {
                    _state.value = PushTokenState.Unavailable(
                        "This device has not been issued a delivery token."
                    )
                } else {
                    adopt(PushToken(token))
                }
            }
            .addOnFailureListener {
                // The exception is not logged: some transport errors echo the
                // request, and the request carries the token.
                _state.value = PushTokenState.Unavailable(
                    "NEXA could not obtain a delivery token for this device."
                )
            }
    }

    /** Called by the messaging service when Android reissues the token. */
    suspend fun onTokenRefreshed(token: PushToken) {
        adopt(token)
        register(token)
    }

    private fun adopt(token: PushToken) {
        current = token
        _state.value = PushTokenState.Available(token.fingerprint)
    }

    /**
     * Hands the token to the backend, when there is a backend to hand it to.
     *
     * Today there is not, so this reports [PushRegistrationResult.NotConfigured]
     * and nothing leaves the device.
     */
    private suspend fun register(token: PushToken) {
        when (val result = registrar.register(token)) {
            is PushRegistrationResult.Registered ->
                Log.i(TAG, "Delivery endpoint registered (fp=${token.fingerprint})")
            is PushRegistrationResult.NotConfigured ->
                Log.i(TAG, "No registration endpoint configured; token not transmitted")
            is PushRegistrationResult.Failed ->
                Log.w(TAG, "Delivery endpoint registration failed: ${result.reason}")
        }
    }

    /** Forgets the local token. Used on sign-out and on invalidation. */
    fun forget() {
        current = null
        _state.value = PushTokenState.Unknown
    }

    private const val TAG = "NexaPush"
}
