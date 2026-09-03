package com.example.nexa.ui.realtime

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns the one realtime connection.
 *
 * One connection for the application, opened when an operator is looking at
 * it and closed when they are not. Screens do not open their own — several
 * sockets reporting to several places is how two surfaces end up disagreeing,
 * and it is a waste of a radio besides.
 *
 * Background delivery is not this class's job. Android does not usefully
 * support holding a stream open behind the app, and NEXA already has a
 * transport for that: push. This one exists to keep an open console current.
 */
object RealtimeConnectionManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var transport: RealtimeTransport? = null

    @Volatile
    private var scopes: Set<String> = emptySet()

    private var connectionJob: Job? = null
    private var frameJob: Job? = null
    private var resyncJob: Job? = null
    private var stallJob: Job? = null

    private var attempt = 0

    /** Installs the transport. A real one replaces the preview here. */
    fun configure(transport: RealtimeTransport, scopes: Set<String>) {
        this.transport = transport
        this.scopes = scopes
    }

    /**
     * Called when the application comes to the foreground.
     *
     * Always resyncs first. Time has passed, the client may have missed
     * events, and continuing from a stale sequence would leave a screen
     * confidently showing state nobody confirmed.
     */
    fun onForeground() {
        val active = transport ?: return
        if (connectionJob?.isActive == true) return

        connectionJob = scope.launch {
            observeFrames(active)
            observeResync(active)
            watchForStalledGap()
            connectWithBackoff(active)
        }
    }

    /** Called when the application goes to the background. */
    fun onBackground() {
        scope.launch {
            frameJob?.cancel()
            resyncJob?.cancel()
            stallJob?.cancel()
            connectionJob?.cancel()
            connectionJob = null
            transport?.disconnect()
            RealtimeStore.onConnectionState(RealtimeConnectionState.Disconnected)
        }
    }

    /**
     * Notices a gap that never closes.
     *
     * A buffer filling up is the loud way to discover missing events. A
     * couple of events waiting on one that is never coming is the quiet way,
     * and without this the client would sit indefinitely holding them while
     * the feed looked perfectly healthy.
     */
    private fun watchForStalledGap() {
        stallJob?.cancel()
        stallJob = scope.launch {
            var stalledSince: LongRange? = null
            var ticks = 0
            while (true) {
                delay(STALL_CHECK_INTERVAL_MS)
                val gap = RealtimeStore.stalledGap()
                if (gap == null) {
                    stalledSince = null
                    ticks = 0
                    continue
                }
                if (gap == stalledSince) {
                    ticks += 1
                    if (ticks >= STALL_TICKS_BEFORE_RESYNC) {
                        RealtimeStore.requestResync("sequence gap did not close")
                        stalledSince = null
                        ticks = 0
                    }
                } else {
                    stalledSince = gap
                    ticks = 1
                }
            }
        }
    }

    private fun observeFrames(active: RealtimeTransport) {
        frameJob?.cancel()
        frameJob = scope.launch {
            active.frames.collect { frame -> RealtimeStore.submit(frame) }
        }
    }

    /**
     * Watches for gaps.
     *
     * A gap means events are missing, and the only safe response is a fresh
     * snapshot. Guessing what the missing events said is precisely the thing
     * a security console must never do.
     */
    private fun observeResync(active: RealtimeTransport) {
        resyncJob?.cancel()
        resyncJob = scope.launch {
            var lastHandled = RealtimeStore.resyncRequests.value
            RealtimeStore.resyncRequests.collect { requests ->
                if (requests > lastHandled) {
                    lastHandled = requests
                    Log.w(TAG, "Resync requested; re-anchoring to a fresh snapshot")
                    resync(active)
                }
            }
        }
    }

    /**
     * Re-anchors to a snapshot and resumes.
     *
     * With no server the snapshot sequence comes from the preview scenario.
     * Against a real backend this is the snapshot request documented on
     * [RealtimeTransport].
     */
    private suspend fun resync(active: RealtimeTransport) {
        RealtimeStore.onConnectionState(RealtimeConnectionState.Connecting)
        active.disconnect()
        RealtimeStore.onSnapshot(PreviewRealtimeScenario.SNAPSHOT_SEQUENCE, scopes)
        active.connect(scopes, PreviewRealtimeScenario.SNAPSHOT_SEQUENCE)
        RealtimeStore.onConnectionState(RealtimeConnectionState.Connected)
    }

    /**
     * Connects, retrying under bounded exponential backoff.
     *
     * A tight retry loop against a struggling server is a denial of service
     * the client inflicts, so every delay comes from [RealtimeBackoff] and the
     * attempt counter resets only on success.
     */
    private suspend fun connectWithBackoff(active: RealtimeTransport) {
        while (true) {
            try {
                RealtimeStore.onConnectionState(
                    if (attempt == 0) {
                        RealtimeConnectionState.Connecting
                    } else {
                        RealtimeConnectionState.Reconnecting
                    }
                )
                // Snapshot first, then the stream from where it ended. Without
                // an anchor the client could not tell a gap from a fresh start.
                RealtimeStore.onSnapshot(PreviewRealtimeScenario.SNAPSHOT_SEQUENCE, scopes)
                active.connect(scopes, PreviewRealtimeScenario.SNAPSHOT_SEQUENCE)
                RealtimeStore.onConnectionState(RealtimeConnectionState.Connected)
                attempt = 0
                return
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                attempt += 1
                val wait = RealtimeBackoff.delayFor(attempt)
                // The error is not logged in full: a transport failure can
                // echo the request, and the request carries session context.
                Log.w(TAG, "Realtime connect failed (attempt $attempt); retrying in ${wait}ms")
                RealtimeStore.onConnectionState(RealtimeConnectionState.Reconnecting)
                delay(wait)
            }
        }
    }

    private const val TAG = "NexaRealtime"

    /** How often a stalled gap is checked for. */
    private const val STALL_CHECK_INTERVAL_MS = 4_000L

    /** How many consecutive checks a gap may persist before a resync. */
    private const val STALL_TICKS_BEFORE_RESYNC = 2
}
