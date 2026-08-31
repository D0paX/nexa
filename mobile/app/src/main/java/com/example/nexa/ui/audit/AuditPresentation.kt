package com.example.nexa.ui.audit

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.nexa.theme.GlassVariant
import com.example.nexa.theme.NexaIcons
import com.example.nexa.theme.NexaStatus
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.enforcement.EnforcementAction
import com.example.nexa.ui.enforcement.status

/**
 * How security history is presented.
 *
 * Every operator-facing word about a historical event is decided here, with
 * the event type and its execution mode both in hand. Nothing in this file is
 * called from inside a composable's layout logic, and no source-supplied
 * sentence is ever displayed as a headline — which is what guarantees that a
 * simulated action stays visibly simulated for as long as the record exists.
 */

// ============================================================
// CATEGORY
// ============================================================

val AuditCategory.label: String
    get() = when (this) {
        AuditCategory.Device -> "Device"
        AuditCategory.Trust -> "Trust"
        AuditCategory.Alert -> "Alert"
        AuditCategory.Notification -> "Notification"
        AuditCategory.Action -> "Action"
        AuditCategory.Enforcement -> "Enforcement"
        AuditCategory.System -> "System"
    }

val AuditCategory.icon: ImageVector
    get() = when (this) {
        AuditCategory.Device -> NexaIcons.Devices
        AuditCategory.Trust -> NexaIcons.Identity
        AuditCategory.Alert -> NexaIcons.Alerts
        AuditCategory.Notification -> NexaIcons.NotificationDelivery
        AuditCategory.Action -> NexaIcons.Executing
        AuditCategory.Enforcement -> NexaIcons.Enforcing
        AuditCategory.System -> NexaIcons.CircuitBreaker
    }

// ============================================================
// EVENT TYPE
// ============================================================

/** The neutral name of the event type, used in filters and detail fields. */
val AuditEventType.label: String
    get() = when (this) {
        AuditEventType.DeviceObserved -> "Device observed"
        AuditEventType.DeviceAddressChanged -> "Observed address changed"
        AuditEventType.IdentityCreated -> "Identity created"
        AuditEventType.VerificationCompleted -> "Verification completed"
        AuditEventType.TrustChanged -> "Trust standing changed"
        AuditEventType.ReverificationRequested -> "Reverification required"
        AuditEventType.CredentialSuperseded -> "Credential superseded"
        AuditEventType.IdentityRevoked -> "Identity revoked"
        AuditEventType.AlertRaised -> "Alert raised"
        AuditEventType.AlertAcknowledged -> "Alert acknowledged"
        AuditEventType.AlertResolved -> "Alert resolved"
        AuditEventType.AlertIgnored -> "Alert ignored"
        AuditEventType.NotificationSent -> "Notification sent"
        AuditEventType.NotificationDelivered -> "Notification delivered"
        AuditEventType.NotificationRetrying -> "Notification retrying"
        AuditEventType.NotificationFailed -> "Notification delivery failed"
        AuditEventType.ActionRequested -> "Action requested"
        AuditEventType.ActionAuthorized -> "Action authorized"
        AuditEventType.ActionDenied -> "Action denied"
        AuditEventType.ActionExecuting -> "Action executing"
        AuditEventType.ActionReconciled -> "Action reconciled"
        AuditEventType.ActionSucceeded -> "Action completed"
        AuditEventType.ActionFailed -> "Action failed"
        AuditEventType.ActionOutcomeUnknown -> "Action outcome unknown"
        AuditEventType.RollbackRequested -> "Rollback requested"
        AuditEventType.RollbackCompleted -> "Rollback completed"
        AuditEventType.RollbackFailed -> "Rollback failed"
        AuditEventType.EnforcementBindingCreated -> "Enforcement binding created"
        AuditEventType.EnforcementBindingRemoved -> "Enforcement binding removed"
        AuditEventType.CrashReconciliationCompleted -> "Crash reconciliation completed"
        AuditEventType.CircuitBreakerOpened -> "Circuit breaker opened"
        AuditEventType.CircuitBreakerClosed -> "Circuit breaker closed"
    }

/**
 * The shape of the event.
 *
 * Type is recognizable without reading the words and without relying on color,
 * which matters more in history than anywhere else: a timeline is scanned, not
 * read.
 */
val AuditEventType.icon: ImageVector
    get() = when (this) {
        AuditEventType.DeviceObserved -> NexaIcons.Devices
        AuditEventType.DeviceAddressChanged -> NexaIcons.AddressChange
        AuditEventType.IdentityCreated -> NexaIcons.Identity
        AuditEventType.VerificationCompleted -> NexaIcons.Secure
        AuditEventType.TrustChanged -> NexaIcons.TrustChange
        AuditEventType.ReverificationRequested -> NexaIcons.Reverification
        AuditEventType.CredentialSuperseded -> NexaIcons.Credential
        AuditEventType.IdentityRevoked -> NexaIcons.Revoked
        AuditEventType.AlertRaised -> NexaIcons.Critical
        AuditEventType.AlertAcknowledged -> NexaIcons.Acknowledge
        AuditEventType.AlertResolved -> NexaIcons.Resolve
        AuditEventType.AlertIgnored -> NexaIcons.Ignore
        AuditEventType.NotificationSent -> NexaIcons.NotificationDelivery
        AuditEventType.NotificationDelivered -> NexaIcons.Delivered
        AuditEventType.NotificationRetrying -> NexaIcons.Reverification
        AuditEventType.NotificationFailed -> NexaIcons.Critical
        AuditEventType.ActionRequested -> NexaIcons.Pending
        AuditEventType.ActionAuthorized -> NexaIcons.Acknowledge
        AuditEventType.ActionDenied -> NexaIcons.Denied
        AuditEventType.ActionExecuting -> NexaIcons.Executing
        AuditEventType.ActionReconciled -> NexaIcons.Reconciled
        AuditEventType.ActionSucceeded -> NexaIcons.Completed
        AuditEventType.ActionFailed -> NexaIcons.Critical
        AuditEventType.ActionOutcomeUnknown -> NexaIcons.Unknown
        AuditEventType.RollbackRequested -> NexaIcons.Rollback
        AuditEventType.RollbackCompleted -> NexaIcons.Rollback
        AuditEventType.RollbackFailed -> NexaIcons.Critical
        AuditEventType.EnforcementBindingCreated -> NexaIcons.Enforcing
        AuditEventType.EnforcementBindingRemoved -> NexaIcons.Release
        AuditEventType.CrashReconciliationCompleted -> NexaIcons.Reconciled
        AuditEventType.CircuitBreakerOpened -> NexaIcons.Paused
        AuditEventType.CircuitBreakerClosed -> NexaIcons.CircuitBreaker
    }

// ============================================================
// OUTCOME
// ============================================================

val AuditOutcome.label: String
    get() = when (this) {
        AuditOutcome.Succeeded -> "Succeeded"
        AuditOutcome.Failed -> "Failed"
        AuditOutcome.Pending -> "In progress"
        AuditOutcome.Informational -> "Recorded"
        AuditOutcome.Unknown -> "Unknown"
    }

val AuditOutcome.status: NexaStatus
    get() = when (this) {
        AuditOutcome.Succeeded -> NexaStatus.Secure
        AuditOutcome.Failed -> NexaStatus.Danger
        AuditOutcome.Pending -> NexaStatus.Information
        AuditOutcome.Informational -> NexaStatus.Information
        AuditOutcome.Unknown -> NexaStatus.Unknown
    }

// ============================================================
// SOURCE
// ============================================================

val AuditSource.label: String
    get() = when (this) {
        AuditSource.Observation -> "Observation"
        AuditSource.TrustService -> "Trust service"
        AuditSource.SecurityEvent -> "Security event"
        AuditSource.Alert -> "Alert"
        AuditSource.NotificationService -> "Notification service"
        AuditSource.ActionPipeline -> "Action pipeline"
        AuditSource.EnforcementSubsystem -> "Enforcement subsystem"
    }

// ============================================================
// ACTION NAMING
// ============================================================

/**
 * The noun for the action a record concerns.
 *
 * Resolved through the existing [EnforcementAction] vocabulary so audit and
 * the action flow cannot drift into two names for the same operation. An
 * unrecognized code is echoed verbatim rather than softened into "action" —
 * an operator reading history needs to see exactly what was requested.
 */
fun auditActionNoun(actionCode: String?): String =
    when (EnforcementAction.entries.firstOrNull { it.code == actionCode }) {
        EnforcementAction.QuarantineDevice -> "Quarantine"
        EnforcementAction.ReleaseQuarantine -> "Release"
        EnforcementAction.RequireReverification -> "Reverification"
        null -> actionCode ?: "Action"
    }

// ============================================================
// HEADLINE
// ============================================================

/**
 * What the row says happened.
 *
 * Always derived, never stored. A simulated run reads as a simulation in every
 * tense and forever after — there is no code path by which "Quarantine
 * simulation completed" can decay into "Quarantine completed" because the
 * event has become old.
 */
fun auditHeadline(entry: AuditEntry): String {
    val base = when (entry.category) {
        AuditCategory.Action -> actionHeadline(entry)
        else -> entry.type.label
    }
    // A mode NEXA could not read is stated, not guessed in either direction.
    return if (entry.hasUnknownMode) "$base — execution mode unknown" else base
}

private fun actionHeadline(entry: AuditEntry): String {
    val noun = auditActionNoun(entry.actionCode)
    val simulated = entry.isSimulated
    return when (entry.type) {
        AuditEventType.ActionRequested ->
            if (simulated) "$noun simulation requested" else "$noun requested"
        AuditEventType.ActionAuthorized ->
            if (simulated) "$noun simulation authorized" else "$noun authorized"
        // Authorization is evaluated for real in both modes, so it reads the
        // same way in both. What differs is what would have happened next.
        AuditEventType.ActionDenied -> "$noun denied by authorization"
        AuditEventType.ActionExecuting ->
            if (simulated) "$noun simulation running" else "$noun executing"
        AuditEventType.ActionReconciled -> "$noun reconciled"
        AuditEventType.ActionSucceeded ->
            if (simulated) "$noun simulation completed" else "$noun completed"
        AuditEventType.ActionFailed ->
            if (simulated) "$noun simulation failed" else "$noun failed"
        AuditEventType.ActionOutcomeUnknown ->
            if (simulated) "$noun simulation outcome unknown" else "$noun outcome unknown"
        AuditEventType.RollbackRequested ->
            if (simulated) "Simulated rollback requested" else "Rollback requested"
        AuditEventType.RollbackCompleted ->
            if (simulated) "Simulated rollback completed" else "Rollback completed"
        AuditEventType.RollbackFailed ->
            if (simulated) "Simulated rollback failed" else "Rollback failed"
        else -> entry.type.label
    }
}

// ============================================================
// EXPLANATION
// ============================================================

/**
 * What the event actually means.
 *
 * Factual: it reports what the authoritative record says and stops there. It
 * never concludes that the system is secure, and every AUDIT_ONLY sentence
 * states in plain words that no firewall mutation occurred.
 */
fun auditExplanation(entry: AuditEntry): String {
    if (entry.hasUnknownMode) return unknownModeExplanation(entry)
    if (entry.isSimulated) return simulatedExplanation(entry)
    return factualExplanation(entry)
}

private fun unknownModeExplanation(entry: AuditEntry): String {
    val noun = auditActionNoun(entry.actionCode)
    return "The execution mode of this $noun run was not recorded. NEXA cannot state whether firewall state was mutated, and will not assume either way. Recorded outcome: ${entry.outcome.label.lowercase()}."
}

private fun simulatedExplanation(entry: AuditEntry): String {
    val noun = auditActionNoun(entry.actionCode)
    return when (entry.type) {
        AuditEventType.ActionRequested ->
            "A simulated $noun was requested in AUDIT_ONLY. No firewall mutation occurred."
        AuditEventType.ActionAuthorized ->
            "The authorization engine approved the simulated request. Authorization was evaluated for real; no firewall mutation occurred."
        AuditEventType.ActionDenied ->
            "The authorization engine refused the request. The simulation never ran, and no firewall mutation occurred."
        AuditEventType.ActionExecuting ->
            "NEXA evaluated this $noun as a simulation. No firewall mutation occurred."
        AuditEventType.ActionSucceeded ->
            "The $noun simulation completed. No firewall mutation occurred and the target's enforcement state was unchanged."
        AuditEventType.ActionFailed ->
            "The $noun simulation did not complete. No firewall mutation occurred, because none would have been attempted in this mode."
        AuditEventType.ActionOutcomeUnknown ->
            "NEXA could not determine the outcome of this simulation. No firewall mutation occurred."
        AuditEventType.RollbackRequested ->
            "The simulation failed and a simulated rollback was requested. No firewall mutation occurred."
        AuditEventType.RollbackCompleted ->
            "The simulated prior state was restored. No firewall mutation occurred at any point."
        AuditEventType.RollbackFailed ->
            "The simulation failed and its simulated rollback also failed. No firewall mutation occurred, but the simulated outcome is inconsistent and should be investigated."
        else ->
            "This record was produced by a simulated run. No firewall mutation occurred."
    }
}

private fun factualExplanation(entry: AuditEntry): String {
    val noun = auditActionNoun(entry.actionCode)
    return when (entry.type) {
        // --- Observation ---
        AuditEventType.DeviceObserved ->
            "NEXA observed this device on the network. Observation says nothing about its trust standing."
        AuditEventType.DeviceAddressChanged ->
            "The address NEXA observes for this device changed. The device identity is unchanged; an address is observation, not identity."

        // --- Trust ---
        AuditEventType.IdentityCreated ->
            "A cryptographic identity was created for this device. No key material is shown here or held by this client."
        AuditEventType.VerificationCompleted ->
            "The identity proved possession of its credential. Verification is not authorization for any action."
        AuditEventType.TrustChanged ->
            "The trust standing recorded for this identity changed. Trust standing is separate from enforcement state."
        AuditEventType.ReverificationRequested ->
            "NEXA required this identity to verify again. This changes no firewall state."
        AuditEventType.CredentialSuperseded ->
            "A new credential replaced the previous one for this identity. The superseded credential is no longer accepted."
        AuditEventType.IdentityRevoked ->
            "Trust in this identity was withdrawn. Revocation is a trust decision and is not itself an enforcement action."

        // --- Alert lifecycle ---
        AuditEventType.AlertRaised ->
            "A security event met the threshold to raise an alert."
        AuditEventType.AlertAcknowledged ->
            "An operator acknowledged this alert. Acknowledgement does not resolve it."
        AuditEventType.AlertResolved ->
            "The incident was closed."
        AuditEventType.AlertIgnored ->
            "The alert was set aside without being resolved."

        // --- Notification delivery ---
        AuditEventType.NotificationSent ->
            "A notification about the alert was sent. Delivery was not yet confirmed."
        AuditEventType.NotificationDelivered ->
            "The notification about the alert was delivered. This says nothing about whether the alert was handled."
        AuditEventType.NotificationRetrying ->
            "Notification delivery failed and was retried. The alert itself is unaffected."
        AuditEventType.NotificationFailed ->
            "Notification delivery failed. The alert itself is unaffected and remains in whatever state it was in."

        // --- Action lifecycle ---
        AuditEventType.ActionRequested ->
            "A $noun was requested through the Phase 4 action pipeline."
        AuditEventType.ActionAuthorized ->
            "The authorization engine approved this request."
        AuditEventType.ActionDenied ->
            "The authorization engine refused this request. Execution never started."
        AuditEventType.ActionExecuting ->
            "The enforcement pipeline applied this action."
        AuditEventType.ActionReconciled ->
            "The resulting system state was confirmed against the requested action."
        AuditEventType.ActionSucceeded ->
            "The action completed."
        AuditEventType.ActionFailed ->
            "The action did not complete. The resulting state was not confirmed."
        AuditEventType.ActionOutcomeUnknown ->
            "NEXA could not determine the outcome of this action. It is not recorded as either success or failure."
        AuditEventType.RollbackRequested ->
            "The action failed and a rollback was requested."
        AuditEventType.RollbackCompleted ->
            "The prior state was restored."
        AuditEventType.RollbackFailed ->
            "The action failed AND its rollback failed. The target did not return to its prior state. This requires operator attention."

        // --- Enforcement bindings ---
        AuditEventType.EnforcementBindingCreated ->
            "An enforcement binding was created for this target and is owned by the recorded scope."
        AuditEventType.EnforcementBindingRemoved ->
            "The enforcement binding for this target was removed."
        AuditEventType.CrashReconciliationCompleted ->
            "After an interrupted run, NEXA reconciled its recorded bindings against actual system state."

        // --- Circuit breaker ---
        AuditEventType.CircuitBreakerOpened ->
            "The enforcement circuit breaker opened. Enforcement was halted while it stayed open."
        AuditEventType.CircuitBreakerClosed ->
            "The enforcement circuit breaker closed. Enforcement was permitted again."
    }
}

// ============================================================
// MODE
// ============================================================

/** The compact execution-mode marker on a row. Null when none was recorded. */
data class AuditModeBadge(
    val label: String,
    val status: NexaStatus,
    val icon: ImageVector
)

/**
 * The mode marker.
 *
 * The tone comes from the shared enforcement vocabulary rather than being
 * chosen here, so simulation and live look the same in history as they did in
 * the flow that produced them.
 *
 * The icon is the mode's own, deliberately not the tone's default. Live
 * enforcement is charged with danger, and danger's default shape is the
 * quarantine block — which on a *release* record would put a "blocked" glyph
 * beside an event that unblocked something. The badge says which mode ran; it
 * must not also imply what the action was.
 *
 * A record with no mode gets no badge. It is not an execution, and marking it
 * "live" would be an invention.
 */
fun auditModeBadge(entry: AuditEntry): AuditModeBadge? = when (entry.executionMode) {
    ExecutionMode.AuditOnly -> AuditModeBadge(
        label = "SIMULATED",
        status = ExecutionMode.AuditOnly.status,
        icon = NexaIcons.Simulated
    )
    ExecutionMode.Enforce -> AuditModeBadge(
        label = "LIVE",
        status = ExecutionMode.Enforce.status,
        icon = NexaIcons.Enforcing
    )
    ExecutionMode.Unknown -> AuditModeBadge(
        label = "MODE UNKNOWN",
        status = ExecutionMode.Unknown.status,
        icon = NexaIcons.Unknown
    )
    null -> null
}

val ExecutionMode.auditLabel: String
    get() = when (this) {
        ExecutionMode.AuditOnly -> "AUDIT_ONLY (simulated)"
        ExecutionMode.Enforce -> "ENFORCE (live)"
        ExecutionMode.Unknown -> "UNKNOWN"
    }

// ============================================================
// TONE & MATERIAL
// ============================================================

/**
 * The state tone of a record.
 *
 * A simulated run is never shown in live danger tone. Nothing was mutated, so
 * a failure there is a fact about the plan rather than about the network, and
 * painting it the same red as a real enforcement failure would teach the
 * operator to read the two as equivalent.
 */
fun auditStatus(entry: AuditEntry): NexaStatus {
    val base = when (entry.type) {
        // Its own category: the system did not return to its prior state.
        AuditEventType.RollbackFailed -> NexaStatus.Critical
        AuditEventType.IdentityRevoked -> NexaStatus.Danger
        AuditEventType.CircuitBreakerOpened -> NexaStatus.Paused
        else -> entry.outcome.status
    }
    if (entry.isSimulated && (base == NexaStatus.Danger || base == NexaStatus.Critical)) {
        return NexaStatus.Warning
    }
    return base
}

/**
 * Material weight.
 *
 * Most history is quiet. A failure, a revocation or a halted breaker earns a
 * denser surface; a simulation stays quiet whatever it simulated.
 */
fun auditSurfaceFor(entry: AuditEntry): GlassVariant = when {
    entry.isSimulated -> GlassVariant.Standard
    entry.type == AuditEventType.RollbackFailed -> GlassVariant.Strong
    entry.type == AuditEventType.IdentityRevoked -> GlassVariant.Strong
    entry.type == AuditEventType.CircuitBreakerOpened -> GlassVariant.Strong
    entry.outcome == AuditOutcome.Failed -> GlassVariant.Strong
    else -> GlassVariant.Standard
}

// ============================================================
// ROW COMPOSITION
// ============================================================

/** Target and scope, stated plainly and making no claim about state. */
fun auditRowSubtitle(entry: AuditEntry): String {
    val scope = entry.target.scopeOrNull
    val label = entry.target.displayLabel
    return if (scope != null && scope != label) "$label · $scope" else label
}

/** The technical identifier shown under a row, when the target has one. */
fun auditRowTechnical(entry: AuditEntry): String? = entry.target.technical

// ============================================================
// DETAIL FIELDS
// ============================================================

/**
 * The record's fields.
 *
 * Only fields that exist are emitted; a missing value is absent rather than
 * rendered as "unknown", because an empty row invites an operator to read a
 * gap as a fact. Nothing here is secret: the model carries no key material,
 * credential or token to leak.
 */
fun auditDetailFields(entry: AuditEntry): List<AuditField> {
    val fields = mutableListOf<AuditField>()

    fields += AuditField("EVENT ID", entry.id, technical = true)
    fields += AuditField("EVENT TYPE", entry.type.label)
    fields += AuditField("CATEGORY", entry.category.label)
    fields += AuditField("OCCURRED", entry.occurredAtLabel)
    fields += AuditField("SOURCE", entry.source.label)

    fields += AuditField("TARGET", entry.target.displayLabel)
    when (val target = entry.target) {
        is AuditTarget.Device -> {
            fields += AuditField("MAC", target.mac, technical = true)
            // Labelled as observation so it is never read as the target's identity.
            target.ip?.let { fields += AuditField("OBSERVED ADDRESS", it, technical = true) }
            fields += AuditField("SCOPE", target.scope, technical = true)
        }
        is AuditTarget.Identity -> {
            fields += AuditField("IDENTITY", target.identityId, technical = true)
            target.scope?.let { fields += AuditField("SCOPE", it, technical = true) }
        }
        is AuditTarget.Scope -> fields += AuditField("SCOPE", target.scope, technical = true)
        is AuditTarget.Subsystem -> Unit
        AuditTarget.Unresolved -> Unit
    }

    entry.actionCode?.let { fields += AuditField("ACTION", it, technical = true) }
    entry.executionMode?.let { fields += AuditField("EXECUTION MODE", it.auditLabel) }
    fields += AuditField("OUTCOME", entry.outcome.label)
    entry.previousState?.let { fields += AuditField("PREVIOUS STATE", it) }
    entry.resultingState?.let { fields += AuditField("RESULTING STATE", it) }
    entry.correlationId?.let { fields += AuditField("CORRELATION", it, technical = true) }
    entry.alertId?.let { fields += AuditField("SOURCE ALERT", it, technical = true) }
    entry.note?.let { fields += AuditField("NOTE", it) }

    return fields
}

/**
 * Where the record can take the operator.
 *
 * To the things it refers to, never into the action flow: that screen requests
 * executions, and history should not be one tap from repeating itself.
 */
fun auditLinks(entry: AuditEntry): List<AuditLink> {
    val links = mutableListOf<AuditLink>()
    entry.alertId?.let { links += AuditLink.Alert(it) }
    when (val target = entry.target) {
        is AuditTarget.Device -> links += AuditLink.Device(target.mac)
        is AuditTarget.Identity -> links += AuditLink.Identity(target.identityId)
        else -> Unit
    }
    return links
}

val AuditLink.label: String
    get() = when (this) {
        is AuditLink.Alert -> "View alert"
        is AuditLink.Device -> "View observed device"
        is AuditLink.Identity -> "View identity"
    }

val AuditLink.icon: ImageVector
    get() = when (this) {
        is AuditLink.Alert -> NexaIcons.Alerts
        is AuditLink.Device -> NexaIcons.Devices
        is AuditLink.Identity -> NexaIcons.Identity
    }
