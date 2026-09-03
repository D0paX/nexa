package com.example.nexa.ui.realtime

/**
 * Decides whether, and in what order, a validated event may be applied.
 *
 * Three problems every realtime stream has, solved once here rather than in
 * each screen:
 *
 *  - **Duplicates.** A transport that retries will deliver the same event
 *    twice. Applied twice, a counter double-counts and a history shows the
 *    same incident as two.
 *  - **Reordering.** Packets arrive out of order. Applied in arrival order, a
 *    rollback can land before the failure that caused it, and the console
 *    ends up telling an operator a story that never happened.
 *  - **Loss.** Events go missing. Applied as though nothing were missing, the
 *    state is quietly, confidently wrong — the worst outcome available to a
 *    security console, and the reason gaps trigger a resync rather than a
 *    shrug.
 *
 * Ordering comes from the publisher's [RealtimeEvent.sequence] and never from
 * arrival time.
 *
 * Pure and single-threaded by contract: the store serialises calls to it.
 */
class RealtimeSequencer(
    /** How many out-of-order events may wait before the gap is called lost. */
    private val gapTolerance: Int = DEFAULT_GAP_TOLERANCE,
    /** How far ahead a sequence may jump before the gap is called lost. */
    private val maxGap: Long = DEFAULT_MAX_GAP,
    /** How many recent event ids are remembered for deduplication. */
    private val dedupWindow: Int = DEFAULT_DEDUP_WINDOW
) {

    private var lastApplied: Long = -1L
    private val seen = LinkedHashSet<String>()
    private val buffered = sortedMapOf<Long, RealtimeEvent>()

    val lastAppliedSequence: Long get() = lastApplied
    val bufferedCount: Int get() = buffered.size

    /**
     * Anchors the sequencer to a snapshot.
     *
     * Called after an initial sync or a resync. Everything the snapshot
     * already includes is at or below [sequence], so anything at or below it
     * arriving later is a replay.
     */
    fun reset(sequence: Long) {
        lastApplied = sequence
        seen.clear()
        buffered.clear()
    }

    fun offer(event: RealtimeEvent): SequencerOutcome {
        // Deduplication first, and by event id: two frames describing the same
        // event are the same event however they reached the device.
        if (!seen.add(event.eventId)) return SequencerOutcome.Duplicate(event.eventId)
        trimSeen()

        // Already covered by the snapshot or by an earlier application. Not an
        // error — a transport replaying its buffer after a reconnect is
        // normal — but it must not be applied a second time.
        if (event.sequence <= lastApplied) return SequencerOutcome.Replay(event.sequence)

        if (event.sequence == lastApplied + 1) {
            val run = mutableListOf(event)
            lastApplied = event.sequence
            // Anything buffered that now follows contiguously can go too.
            while (true) {
                val next = buffered.remove(lastApplied + 1) ?: break
                run += next
                lastApplied = next.sequence
            }
            return SequencerOutcome.Apply(run)
        }

        // Ahead of the expected sequence: hold it and wait for the gap to
        // close on its own.
        buffered[event.sequence] = event
        val gapWidth = event.sequence - lastApplied
        return if (buffered.size >= gapTolerance || gapWidth > maxGap) {
            // The missing events are not arriving. Guessing what they said is
            // not an option, so the client asks for a fresh snapshot.
            SequencerOutcome.GapDetected(
                expectedSequence = lastApplied + 1,
                highestSeen = buffered.lastKey()
            )
        } else {
            SequencerOutcome.Buffered(event.sequence)
        }
    }

    /**
     * Whether events are waiting on a gap that has not closed.
     *
     * The connection layer can poll this after a quiet period and resync
     * rather than waiting for the buffer to fill.
     */
    fun pendingGap(): LongRange? {
        if (buffered.isEmpty()) return null
        return (lastApplied + 1) until buffered.firstKey()
    }

    private fun trimSeen() {
        while (seen.size > dedupWindow) {
            val oldest = seen.first()
            seen.remove(oldest)
        }
    }

    companion object {
        const val DEFAULT_GAP_TOLERANCE = 8
        const val DEFAULT_MAX_GAP = 64L

        /**
         * Bounded on purpose. An unbounded set of seen ids is a memory leak
         * that grows for as long as the app is open; the sequence check below
         * it catches anything that falls out of the window.
         */
        const val DEFAULT_DEDUP_WINDOW = 512
    }
}

/** What the sequencer decided about an event. */
sealed interface SequencerOutcome {
    /** Apply these, in this order. */
    data class Apply(val events: List<RealtimeEvent>) : SequencerOutcome

    data class Duplicate(val eventId: String) : SequencerOutcome

    /** Already covered by the snapshot or a previous application. */
    data class Replay(val sequence: Long) : SequencerOutcome

    /** Held until the events before it arrive. */
    data class Buffered(val sequence: Long) : SequencerOutcome

    /** Events are missing and are not coming. The client must resync. */
    data class GapDetected(val expectedSequence: Long, val highestSeen: Long) : SequencerOutcome
}
