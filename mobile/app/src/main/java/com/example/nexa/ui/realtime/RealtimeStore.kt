package com.example.nexa.ui.realtime

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The shared realtime read model.
 *
 * One store, one connection, one ordering. Screens observe it; none of them
 * sees a frame, and none of them decides what an event means. That is the
 * whole point: three surfaces independently interpreting the same packet is
 * how a console starts disagreeing with itself.
 *
 * Frames are validated, sequenced and reduced off the main thread. Every
 * mutation runs under a mutex, so concurrent arrivals cannot interleave into
 * a half-applied state, and the state itself is immutable so a screen reading
 * during an update sees one version or the next, never a mixture.
 */
object RealtimeStore {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val sequencer = RealtimeSequencer()

    private val _state = MutableStateFlow(RealtimeState())
    val state: StateFlow<RealtimeState> = _state.asStateFlow()

    private val _connection = MutableStateFlow(RealtimeConnectionState.Disconnected)
    val connection: StateFlow<RealtimeConnectionState> = _connection.asStateFlow()

    private val _lastEventAtMillis = MutableStateFlow<Long?>(null)
    val lastEventAtMillis: StateFlow<Long?> = _lastEventAtMillis.asStateFlow()

    private val _resyncRequests = MutableStateFlow(0)

    /** Increments whenever a gap forces a resync. Observed by the connection layer. */
    val resyncRequests: StateFlow<Int> = _resyncRequests.asStateFlow()

    /**
     * The scopes this client is entitled to see.
     *
     * A second line after the server's own filtering. It is a safety net, not
     * an access control: the authority is the session the stream was opened
     * with. What it does buy is that a publisher bug cannot quietly show one
     * operator another segment's activity.
     */
    @Volatile
    private var subscribedScopes: Set<String> = emptySet()

    fun subscribe(scopes: Set<String>) {
        subscribedScopes = scopes
    }

    fun onConnectionState(next: RealtimeConnectionState) {
        _connection.value = next
    }

    /**
     * Anchors the store to a snapshot.
     *
     * Called on first sync and after every resync. Everything the snapshot
     * covers is at or below [sequence]; events after it are applied on top.
     */
    fun onSnapshot(sequence: Long, scopes: Set<String>) {
        scope.launch {
            mutex.withLock {
                subscribedScopes = scopes
                sequencer.reset(sequence)
                // Overlays are cleared: they described changes on top of the
                // previous snapshot, and this is a new one.
                _state.value = RealtimeState(lastAppliedSequence = sequence)
            }
        }
    }

    /** A frame off the wire. Validated, sequenced and reduced here. */
    fun onFrame(frame: Map<String, String>, nowMillis: Long = System.currentTimeMillis()) {
        scope.launch {
            mutex.withLock { handleFrame(frame, nowMillis) }
        }
    }

    /** Synchronous entry point, for tests and for the preview transport. */
    suspend fun submit(frame: Map<String, String>, nowMillis: Long = System.currentTimeMillis()) {
        mutex.withLock { handleFrame(frame, nowMillis) }
    }

    private fun handleFrame(frame: Map<String, String>, nowMillis: Long) {
        when (val parsed = RealtimeEventParser.parse(frame)) {
            is RealtimeParseResult.Rejected -> {
                // Reason and field only. A frame that failed validation is
                // exactly the frame whose contents should not be copied
                // anywhere, including a log.
                Log.w(TAG, "Rejected frame: ${parsed.reason} (${parsed.detail})")
            }

            is RealtimeParseResult.UnsupportedVersion -> {
                // Held, not treated as corruption: the right response is a
                // client update, and reading it with the old parser could
                // apply a field that now means something else.
                Log.w(TAG, "Frame from unsupported schema ${parsed.version}; ignored")
            }

            is RealtimeParseResult.Accepted -> applyEvent(parsed.event, nowMillis)
        }
    }

    private fun applyEvent(event: RealtimeEvent, nowMillis: Long) {
        if (subscribedScopes.isNotEmpty() && event.scope !in subscribedScopes) {
            Log.w(TAG, "Dropped event ${event.eventId}: scope not subscribed")
            return
        }

        when (val outcome = sequencer.offer(event)) {
            is SequencerOutcome.Duplicate ->
                Log.i(TAG, "Duplicate event ${outcome.eventId} ignored")

            is SequencerOutcome.Replay ->
                Log.i(TAG, "Replayed sequence ${outcome.sequence} ignored")

            is SequencerOutcome.Buffered ->
                Log.i(TAG, "Buffered sequence ${outcome.sequence} awaiting predecessors")

            is SequencerOutcome.GapDetected -> {
                // Missing events cannot be guessed at, so the client stops
                // pretending its state is complete and asks for a snapshot.
                Log.w(
                    TAG,
                    "Sequence gap from ${outcome.expectedSequence} to ${outcome.highestSeen}; resync"
                )
                _connection.value = RealtimeConnectionState.Degraded
                _resyncRequests.value = _resyncRequests.value + 1
            }

            is SequencerOutcome.Apply -> {
                var next = _state.value
                outcome.events.forEach { applicable ->
                    when (val result = RealtimeReducer.reduce(next, applicable)) {
                        is ReduceResult.Applied -> next = result.next
                        is ReduceResult.Ignored ->
                            Log.w(
                                TAG,
                                "Event ${applicable.eventId} ignored: ${result.reason}"
                            )
                    }
                }
                // Published once for the whole run, so a batch of related
                // changes never renders as an impossible intermediate.
                _state.value = next
                _lastEventAtMillis.value = nowMillis
            }
        }
    }

    /**
     * Whether events are stuck waiting on a gap that has not closed.
     *
     * The buffer filling is one way a loss is noticed; a small gap that
     * simply never closes is the other, and it is the quieter one. The
     * connection layer polls this so a stalled stream cannot sit forever
     * looking healthy while a screen shows state nobody confirmed.
     */
    suspend fun stalledGap(): LongRange? = mutex.withLock { sequencer.pendingGap() }

    /** Asks the connection layer for a fresh snapshot. */
    fun requestResync(reason: String) {
        Log.w(TAG, "Resync requested: $reason")
        _connection.value = RealtimeConnectionState.Degraded
        _resyncRequests.value = _resyncRequests.value + 1
    }

    /** Test and lifecycle hygiene. */
    suspend fun reset() {
        mutex.withLock {
            sequencer.reset(-1L)
            _state.value = RealtimeState()
            _connection.value = RealtimeConnectionState.Disconnected
            _lastEventAtMillis.value = null
            _resyncRequests.value = 0
            subscribedScopes = emptySet()
        }
    }

    private const val TAG = "NexaRealtime"
}
