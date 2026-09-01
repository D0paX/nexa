package com.example.nexa.ui.realtime

import kotlinx.coroutines.flow.Flow

/**
 * The seam between NEXA and whatever carries realtime frames.
 *
 * The UI never learns whether this is a WebSocket, server-sent events, long
 * polling or a test harness. Swapping the transport must not touch a screen,
 * a view model or a reducer.
 *
 * ---------------------------------------------------------------------------
 * BACKEND CONTRACT — NOT YET IMPLEMENTED SERVER-SIDE
 * ---------------------------------------------------------------------------
 *
 * NEXA's Phase 1-4 backend publishes no realtime endpoint today. The
 * abstraction and a deterministic preview transport exist so the client
 * architecture is real; the server side is not, and this comment is the
 * specification rather than a description of something that runs.
 *
 * What the server must provide:
 *
 *   Transport      an authenticated, ordered, server-to-client stream. The
 *                  client requires ordering metadata, not ordered delivery.
 *
 *   Snapshot       GET <base>/api/v1/realtime/snapshot?scope=...
 *                  returns current state plus the sequence it is current as
 *                  of. The client anchors to that sequence and applies events
 *                  after it. Without this the client would have to rebuild
 *                  state from an event history it cannot verify is complete.
 *
 *   Stream         <base>/api/v1/realtime/stream?scope=...&fromSequence=N
 *                  emits envelopes with the fields in [RealtimeEvent]:
 *                  eventId, schemaVersion, sequence, eventType, occurredAt,
 *                  scope, subjectId, and the typed payload fields.
 *
 *   Sequence       a per-connection monotonic counter, gapless within a
 *                  subscription. Gaps are how the client learns it has missed
 *                  something; a server that reuses or skips numbers casually
 *                  makes that detection useless.
 *
 *   Event ids      stable and globally unique, so the same event delivered
 *                  twice — or once here and once by push — deduplicates.
 *
 *   Resync         the client re-requests the snapshot and reconnects from
 *                  its sequence. The server must not expect the client to
 *                  reconstruct anything.
 *
 *   Authentication the operator session, not a device token. The stream must
 *                  emit only scopes that session may see; the client filters
 *                  again, but a client-side filter is a safety net and not an
 *                  access control.
 */
interface RealtimeTransport {

    /** Frames as they arrive, unvalidated and unordered. */
    val frames: Flow<Map<String, String>>

    val connectionState: Flow<RealtimeConnectionState>

    /**
     * Opens the stream from a known position.
     *
     * [fromSequence] is where the client's snapshot ended, so the server can
     * resume rather than replay everything.
     */
    suspend fun connect(scopes: Set<String>, fromSequence: Long)

    suspend fun disconnect()
}

/**
 * What the transport is doing.
 *
 * Deliberately its own vocabulary, separate from every security state in the
 * product. A console that let "disconnected" bleed into its posture display
 * would be telling operators the network is unsafe when the truth is only
 * that the console has stopped hearing about it.
 */
enum class RealtimeConnectionState {
    Disconnected,
    Connecting,
    Connected,

    /** Lost and retrying under backoff. */
    Reconnecting,

    /** Connected, but the client knows its state is incomplete. */
    Degraded,

    /** Retries exhausted. Not a security verdict. */
    Failed;

    val isLive: Boolean get() = this == Connected
    val isTrying: Boolean get() = this == Connecting || this == Reconnecting
}

/**
 * Reconnect timing, in one place.
 *
 * Exponential with a ceiling, reset on a successful connection. A tight retry
 * loop against a struggling server is a denial of service delivered by the
 * client, and hardcoded sleeps scattered through a codebase are impossible to
 * reason about later.
 */
object RealtimeBackoff {
    const val INITIAL_DELAY_MS = 1_000L
    const val MAX_DELAY_MS = 60_000L
    const val MULTIPLIER = 2.0

    /** Delay before attempt [attempt], counting from 1. */
    fun delayFor(attempt: Int): Long {
        if (attempt <= 1) return INITIAL_DELAY_MS
        var delay = INITIAL_DELAY_MS.toDouble()
        repeat(attempt - 1) { delay *= MULTIPLIER }
        return delay.toLong().coerceAtMost(MAX_DELAY_MS)
    }
}

/**
 * How current the displayed state is.
 *
 * Derived from when the last event was applied, not from whether a socket is
 * open — a connection that is up but silent for a minute is not proof that
 * nothing happened.
 */
enum class RealtimeFreshness {
    Live,
    Aging,
    Stale,
    Unknown;

    companion object {
        const val AGING_AFTER_MS = 30_000L
        const val STALE_AFTER_MS = 120_000L

        fun of(
            connection: RealtimeConnectionState,
            lastEventAtMillis: Long?,
            nowMillis: Long
        ): RealtimeFreshness {
            if (connection != RealtimeConnectionState.Connected) return Stale
            if (lastEventAtMillis == null) return Unknown
            val age = nowMillis - lastEventAtMillis
            return when {
                age >= STALE_AFTER_MS -> Stale
                age >= AGING_AFTER_MS -> Aging
                else -> Live
            }
        }
    }
}
