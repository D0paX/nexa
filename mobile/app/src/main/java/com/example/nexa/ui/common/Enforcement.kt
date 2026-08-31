package com.example.nexa.ui.common

/**
 * Phase 4 vocabulary shared by every feature that talks about enforcement.
 *
 * One definition so the command center, the device inventory and the action
 * flow cannot drift into competing notions of what "paused" or "audit only"
 * means.
 */

/** How an execution will be carried out, if it is carried out at all. */
enum class ExecutionMode {
    /** The action will mutate real firewall state. */
    Enforce,

    /** The action will be simulated. No firewall mutation will occur. */
    AuditOnly,

    /** NEXA cannot determine the execution mode. Never presented as live. */
    Unknown
}

/** The Phase 4 enforcement circuit breaker. */
enum class CircuitBreakerState {
    Closed,
    Open,
    HalfOpen;

    /** Enforcement can only run when the breaker is not open. */
    val allowsExecution: Boolean get() = this != Open
}
