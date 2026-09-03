package com.example.nexa.ui.realtime

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PREVIEW REALTIME — NOT LIVE SYSTEM STATE
 *
 * A deterministic transport for developing and reviewing the realtime path
 * without a server. Every frame below is fabricated. Nothing here observed a
 * network, evaluated an identity, or executed anything, and no frame reflects
 * the state of any real system.
 *
 * It emits through the same seam a real transport would, so what gets
 * exercised is the actual validation, sequencing and reduction — a preview
 * that injected state directly would be testing a path that never runs.
 *
 * The scenario deliberately includes the awkward cases: a duplicate, an
 * out-of-order pair, and a sequence gap, because those are the ones a happy
 * path never reveals.
 */
class PreviewRealtimeTransport : RealtimeTransport {

    private val _frames = MutableSharedFlow<Map<String, String>>(extraBufferCapacity = 64)
    override val frames: Flow<Map<String, String>> = _frames.asSharedFlow()

    private val _connectionState = MutableStateFlow(RealtimeConnectionState.Disconnected)
    override val connectionState: Flow<RealtimeConnectionState> = _connectionState.asStateFlow()

    val currentConnectionState: RealtimeConnectionState get() = _connectionState.value

    override suspend fun connect(scopes: Set<String>, fromSequence: Long) {
        _connectionState.value = RealtimeConnectionState.Connecting
        delay(CONNECT_DELAY_MS)
        _connectionState.value = RealtimeConnectionState.Connected
    }

    override suspend fun disconnect() {
        _connectionState.value = RealtimeConnectionState.Disconnected
    }

    /** Emits the scripted scenario, paced so a reviewer can watch it land. */
    suspend fun playScenario() {
        PreviewRealtimeScenario.frames.forEach { frame ->
            _frames.emit(frame)
            delay(STEP_DELAY_MS)
        }
    }

    suspend fun emit(frame: Map<String, String>) {
        _frames.emit(frame)
    }

    /** Simulates a drop, for exercising reconnect and staleness. */
    suspend fun simulateDrop() {
        _connectionState.value = RealtimeConnectionState.Reconnecting
        delay(CONNECT_DELAY_MS)
        _connectionState.value = RealtimeConnectionState.Connected
    }

    private companion object {
        const val CONNECT_DELAY_MS = 400L
        const val STEP_DELAY_MS = 900L
    }
}

/**
 * PREVIEW REALTIME — NOT LIVE SYSTEM STATE
 *
 * The scripted frames, as raw wire maps so they go through the parser.
 * Sequences are the publisher's; the awkward ones are deliberate.
 */
object PreviewRealtimeScenario {

    private const val SCOPE_SECURE = "VLAN_SECURE"
    private const val SCOPE_GUEST = "VLAN_GUEST"
    private const val SCOPE_BUILD = "VLAN_BUILD"
    private const val NOW = 1_788_000_000L

    /** The sequence a snapshot would be current as of. */
    const val SNAPSHOT_SEQUENCE = 1000L

    val frames: List<Map<String, String>> = listOf(
        // --- A device is observed ---
        frame(
            eventId = "RT-1001", sequence = 1001, type = "DEVICE_OBSERVED",
            scope = SCOPE_SECURE, subjectId = "DEV-1005",
            extra = mapOf(
                "presence" to "PRESENT",
                "observedAddress" to "10.20.5.90",
                "lastSeen" to "just now"
            )
        ),

        // --- An alert is raised, then acknowledged ---
        frame(
            eventId = "RT-1002", sequence = 1002, type = "ALERT_RAISED",
            scope = SCOPE_SECURE, subjectId = "ALRT-1092",
            extra = mapOf("lifecycle" to "NEW")
        ),
        frame(
            eventId = "RT-1003", sequence = 1003, type = "ALERT_ACKNOWLEDGED",
            scope = SCOPE_SECURE, subjectId = "ALRT-1092",
            extra = mapOf("lifecycle" to "ACKNOWLEDGED")
        ),

        // --- Delivery fails. The alert above is untouched. ---
        frame(
            eventId = "RT-1004", sequence = 1004, type = "DELIVERY_STATE_CHANGED",
            scope = SCOPE_SECURE, subjectId = "NTF-7004",
            extra = mapOf(
                "deliveryState" to "FAILED",
                "attemptCount" to "3",
                "failureReason" to "Device token rejected by the push service."
            )
        ),

        // --- A duplicate. Exactly one application must result. ---
        frame(
            eventId = "RT-1004", sequence = 1004, type = "DELIVERY_STATE_CHANGED",
            scope = SCOPE_SECURE, subjectId = "NTF-7004",
            extra = mapOf(
                "deliveryState" to "FAILED",
                "attemptCount" to "3",
                "failureReason" to "Device token rejected by the push service."
            )
        ),

        // --- A live action moving through its lifecycle ---
        frame(
            eventId = "RT-1005", sequence = 1005, type = "ACTION_STATE_CHANGED",
            scope = SCOPE_SECURE, subjectId = "ACT-9127",
            extra = mapOf(
                "executionState" to "EXECUTING",
                "executionMode" to "ENFORCE",
                "actionCode" to "QUARANTINE_DEVICE"
            )
        ),

        // --- Out of order: 1007 arrives before 1006 and must wait ---
        frame(
            eventId = "RT-1007", sequence = 1007, type = "ACTION_STATE_CHANGED",
            scope = SCOPE_SECURE, subjectId = "ACT-9127",
            extra = mapOf(
                "executionState" to "SUCCEEDED",
                "executionMode" to "ENFORCE",
                "reconciled" to "true",
                "actionCode" to "QUARANTINE_DEVICE"
            )
        ),
        frame(
            eventId = "RT-1006", sequence = 1006, type = "ACTION_STATE_CHANGED",
            scope = SCOPE_SECURE, subjectId = "ACT-9127",
            extra = mapOf(
                "executionState" to "RECONCILING",
                "executionMode" to "ENFORCE",
                "actionCode" to "QUARANTINE_DEVICE"
            )
        ),

        // --- The device the action was about becomes quarantined ---
        frame(
            eventId = "RT-1008", sequence = 1008, type = "DEVICE_ENFORCEMENT_CHANGED",
            scope = SCOPE_SECURE, subjectId = "DEV-1005",
            extra = mapOf("enforcement" to "QUARANTINED")
        ),

        // --- Trust withdrawn. It grants and revokes no authorization. ---
        frame(
            eventId = "RT-1009", sequence = 1009, type = "IDENTITY_REVOKED",
            scope = SCOPE_GUEST, subjectId = "TID-9E12",
            extra = mapOf("trust" to "REVOKED")
        ),

        // --- A simulated action. It stays simulated. ---
        frame(
            eventId = "RT-1010", sequence = 1010, type = "ACTION_STATE_CHANGED",
            scope = SCOPE_GUEST, subjectId = "ACT-9004",
            extra = mapOf(
                "executionState" to "SUCCEEDED",
                "executionMode" to "AUDIT_ONLY",
                "actionCode" to "RELEASE_QUARANTINE"
            )
        ),

        // --- The breaker opens. Enforcement is halted. ---
        frame(
            eventId = "RT-1011", sequence = 1011, type = "CIRCUIT_BREAKER_CHANGED",
            scope = SCOPE_BUILD, subjectId = null,
            extra = mapOf("circuitBreaker" to "OPEN")
        ),

        // --- A malformed frame. It must be refused, not crash anything. ---
        mapOf(
            "schemaVersion" to "1",
            "eventId" to "RT-1012",
            "sequence" to "1012",
            "eventType" to "ALERT_RAISED",
            "occurredAt" to NOW.toString(),
            "scope" to SCOPE_SECURE,
            "subjectId" to "ALRT-1093",
            "lifecycle" to "EXPLODED"
        ),

        // --- A gap: 1013 is never sent, and the run jumps ahead ---
        frame(
            eventId = "RT-1014", sequence = 1014, type = "ALERT_RESOLVED",
            scope = SCOPE_SECURE, subjectId = "ALRT-1091",
            extra = mapOf("lifecycle" to "RESOLVED")
        ),
        frame(
            eventId = "RT-1015", sequence = 1015, type = "ALERT_IGNORED",
            scope = SCOPE_SECURE, subjectId = "ALRT-1087",
            extra = mapOf("lifecycle" to "IGNORED")
        )
    )

    /** The scopes a preview session subscribes to. */
    val scopes: Set<String> = setOf(SCOPE_SECURE, SCOPE_GUEST, SCOPE_BUILD)

    fun frame(
        eventId: String,
        sequence: Long,
        type: String,
        scope: String,
        subjectId: String?,
        extra: Map<String, String> = emptyMap(),
        occurredAt: Long = NOW,
        schemaVersion: Int = REALTIME_SCHEMA_VERSION
    ): Map<String, String> = buildMap {
        put("schemaVersion", schemaVersion.toString())
        put("eventId", eventId)
        put("sequence", sequence.toString())
        put("eventType", type)
        put("occurredAt", occurredAt.toString())
        put("scope", scope)
        subjectId?.let { put("subjectId", it) }
        putAll(extra)
    }
}
