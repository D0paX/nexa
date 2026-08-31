package com.example.nexa.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nexa.theme.*
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.label as trustLabel
import com.example.nexa.ui.common.status as trustStatus
import com.example.nexa.ui.components.*
import com.example.nexa.ui.devices.label as enforcementLabel
import com.example.nexa.ui.devices.status as enforcementStatus
import com.example.nexa.ui.devices.label as presenceLabel
import com.example.nexa.ui.enforcement.*

/**
 * The authoritative confirmation and execution surface for a Phase 4 action.
 *
 * One screen carries the operator through the whole flow — context,
 * confirmation, execution, result — so they always know which phase they are
 * in. Nothing here executes anything: it submits a request and reports what
 * the pipeline says about it.
 *
 * Every field shown comes from the prepared [ActionContext]. The screen
 * fabricates no address, no trust standing and no consequence text.
 */
@Composable
fun ActionConfirmationScreen(
    actionContextId: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActionViewModel = viewModel()
) {
    LaunchedEffect(actionContextId) { viewModel.load(actionContextId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        is ActionUiState.Preparing ->
            LoadingState(message = "Resolving target context...", modifier = modifier)

        is ActionUiState.Unavailable ->
            UnavailableState(
                title = "Action context unavailable",
                message = "NEXA cannot resolve the target context for this action. Nothing has been submitted. Start the action again from the target.",
                modifier = modifier,
                action = {
                    NexaOutlinedButton(
                        text = "Back",
                        onClick = onBack,
                        icon = NexaIcons.Back,
                        modifier = Modifier.widthIn(max = 240.dp)
                    )
                }
            )

        is ActionUiState.AwaitingConfirmation ->
            ConfirmationContent(
                state = current,
                onBack = onBack,
                onConfirm = viewModel::confirm,
                onRefresh = viewModel::refreshTarget,
                modifier = modifier
            )

        is ActionUiState.InFlight ->
            InFlightContent(state = current, modifier = modifier)

        is ActionUiState.Result ->
            ResultContent(state = current, onDone = onDone, modifier = modifier)
    }
}

// ============================================================
// CONFIRMATION
// ============================================================

@Composable
private fun ConfirmationContent(
    state: ActionUiState.AwaitingConfirmation,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = state.context
    val target = context.target
    val available = state.availability is ActionAvailability.Available
    val disabledReason = (state.availability as? ActionAvailability.Disabled)?.reason

    NexaScreen(
        modifier = modifier,
        title = "Confirm Action",
        onBack = onBack,
        backContentDescription = "Cancel action",
        itemSpacing = NexaTokens.SpacingMedium
    ) {
        // Execution mode first: it changes the meaning of everything below.
        item {
            when (context.executionMode) {
                ExecutionMode.AuditOnly -> SimulationBanner()
                ExecutionMode.Enforce -> LiveEnforcementBanner()
                ExecutionMode.Unknown -> UnknownModeBanner()
            }
        }

        item { SectionHeader(text = "Action", level = SectionLevel.Group) }
        item {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NexaIcon(
                            icon = context.action.icon,
                            size = NexaTokens.IconLarge,
                            tint = if (context.action.mutatesEnforcement) NexaDanger else NexaTextSecondary
                        )
                        Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                        Column {
                            Text(context.action.label, style = NexaType.Title, color = NexaTextPrimary)
                            TechnicalValue(context.action.code)
                        }
                    }
                }
            }
        }

        // --- Target ---
        item { SectionHeader(text = "Target", level = SectionLevel.Group) }
        item {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(target.label, style = NexaType.Title, color = NexaTextPrimary)
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                    // Scope is not optional context: the same MAC in another
                    // scope is a different logical target.
                    ContextLine("Network scope") { TechnicalValue(target.scope, emphasized = true) }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    ContextLine("Observed IP") { TechnicalValue(target.ip ?: "not observed") }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    ContextLine("MAC address") { TechnicalValue(target.mac) }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    ContextLine("Presence") {
                        Text(
                            target.presence.presenceLabel,
                            style = NexaType.BodySecondary,
                            color = NexaTextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    ContextLine("Current enforcement") {
                        StatusBadge(
                            status = context.currentEnforcement.enforcementStatus,
                            label = context.currentEnforcement.enforcementLabel.uppercase()
                        )
                    }
                    if (target.bindingId != null) {
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                        ContextLine("Binding") { TechnicalValue(target.bindingId) }
                    }
                    if (target.ownershipScope != null) {
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                        // Ownership is stated against a scope, never an address.
                        ContextLine("Owned in scope") {
                            TechnicalValue(target.ownershipScope, emphasized = true)
                        }
                    }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                    Text(
                        text = "An address is observation context. It is never the target's identity.",
                        style = NexaType.Metadata,
                        color = NexaTextMuted
                    )
                }
            }
        }

        // --- Target snapshot freshness ---
        item { SectionHeader(text = "Target snapshot", level = SectionLevel.Group) }
        item {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Observation", style = NexaType.Body, color = NexaTextPrimary)
                        StatusBadge(
                            status = when (target.observationFreshness) {
                                is DataFreshness.Live -> NexaStatus.Secure
                                is DataFreshness.Stale -> NexaStatus.Warning
                                is DataFreshness.Unknown -> NexaStatus.Unknown
                            },
                            label = when (target.observationFreshness) {
                                is DataFreshness.Live -> "CURRENT"
                                is DataFreshness.Stale -> "STALE"
                                is DataFreshness.Unknown -> "UNKNOWN"
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    ContextLine("Last observed") {
                        Text(target.lastObservedLabel, style = NexaType.BodySecondary, color = NexaTextSecondary)
                    }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    Text(
                        text = "The target will be re-snapshotted and re-authorized by the enforcement pipeline before execution.",
                        style = NexaType.Metadata,
                        color = NexaTextMuted
                    )
                    if (context.targetIsStale) {
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                        Row(verticalAlignment = Alignment.Top) {
                            NexaIcon(icon = NexaIcons.Stale, size = NexaTokens.IconSmall, tint = NexaWarning)
                            Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                            Text(
                                text = "This observation is no longer current. The device it names may not be the device this action would reach.",
                                style = NexaType.Metadata,
                                color = NexaWarning
                            )
                        }
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                        NexaOutlinedButton(
                            text = "Re-resolve target",
                            onClick = onRefresh,
                            icon = NexaIcons.Refresh
                        )
                    }
                }
            }
        }

        // --- Identity, separate from the observation ---
        item { SectionHeader(text = "Identity", level = SectionLevel.Group) }
        item {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                if (target.identityId == null) {
                    Column {
                        Text("No cryptographic identity", style = NexaType.Title, color = NexaTextPrimary)
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
                        Text(
                            text = "This target is an observed device only. NEXA is not asserting a verified identity for it.",
                            style = NexaType.BodySecondary,
                            color = NexaTextSecondary
                        )
                    }
                } else {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NexaIcon(icon = NexaIcons.Identity, size = NexaTokens.IconMedium, tint = NexaTextSecondary)
                            Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                            TechnicalValue(target.identityId, emphasized = true)
                            Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                            StatusBadge(
                                status = target.trust.trustStatus,
                                label = target.trust.trustLabel.uppercase()
                            )
                        }
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                        Text(
                            text = "Trust standing is not authorization. It does not permit this action by itself.",
                            style = NexaType.Metadata,
                            color = NexaTextMuted
                        )
                    }
                }
            }
        }

        // --- Authorization ---
        item { SectionHeader(text = "Authorization", level = SectionLevel.Group) }
        item {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Authorization", style = NexaType.Body, color = NexaTextPrimary)
                        StatusBadge(
                            status = context.authorization.status,
                            label = context.authorization.label.uppercase()
                        )
                    }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    Text(
                        text = context.authorization.explanation,
                        style = NexaType.BodySecondary,
                        color = NexaTextSecondary
                    )
                }
            }
        }

        // --- Circuit breaker, when it matters ---
        if (!context.circuitBreaker.allowsExecution && context.action.mutatesEnforcement) {
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NexaIcon(icon = NexaIcons.Paused, size = NexaTokens.IconMedium, tint = NexaPaused)
                        Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                        Column {
                            Text("Enforcement paused", style = NexaType.Title, color = NexaTextPrimary)
                            Spacer(modifier = Modifier.height(NexaTokens.SpacingHairline))
                            Text(
                                text = "The circuit breaker is open. No enforcement action will execute until it closes.",
                                style = NexaType.BodySecondary,
                                color = NexaTextSecondary
                            )
                        }
                    }
                }
            }
        }

        // --- Consequence and confirmation ---
        item {
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            if (!available && disabledReason != null) {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.Top) {
                        NexaIcon(icon = NexaIcons.Warning, size = NexaTokens.IconMedium, tint = NexaWarning)
                        Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                        Column {
                            Text("This action cannot be requested", style = NexaType.Title, color = NexaTextPrimary)
                            Spacer(modifier = Modifier.height(NexaTokens.SpacingHairline))
                            Text(disabledReason, style = NexaType.BodySecondary, color = NexaTextSecondary)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(NexaTokens.SpacingLarge))
                NexaOutlinedButton(text = "Back", onClick = onBack, icon = NexaIcons.Back)
            } else {
                DestructiveConfirmation(
                    actionName = context.action.code,
                    consequenceText = state.consequence.summary,
                    onConfirm = onConfirm,
                    onCancel = onBack,
                    // Simulation is never dressed as live enforcement.
                    destructive = state.consequence.destructive &&
                        context.executionMode != ExecutionMode.AuditOnly,
                    confirmIcon = context.action.icon
                )
            }
        }

        item { Spacer(modifier = Modifier.height(NexaTokens.SpacingXLarge)) }
    }
}

// ============================================================
// EXECUTION
// ============================================================

@Composable
private fun InFlightContent(state: ActionUiState.InFlight, modifier: Modifier = Modifier) {
    NexaScreen(modifier = modifier, title = "Executing", itemSpacing = NexaTokens.SpacingMedium) {
        item {
            GlassSurface(variant = GlassVariant.Hero, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = NexaTokens.SpacingSmall)) {
                    Text(
                        text = state.context.action.label.uppercase(),
                        style = NexaType.Metadata,
                        color = NexaTextOnDarkMuted
                    )
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    Text(state.state.label, style = NexaType.Display, color = NexaTextOnDark)
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    Text(
                        text = state.state.explanation,
                        style = NexaType.BodySecondary,
                        color = NexaTextOnDarkMuted
                    )
                }
            }
        }

        item { SectionHeader(text = "Progress", level = SectionLevel.Group) }
        item {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                // State-oriented progress. No fabricated percentage.
                Column {
                    executionProgression.forEachIndexed { index, step ->
                        if (index > 0) Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                        val reached = executionProgression.indexOf(state.state) >= index &&
                            executionProgression.contains(state.state)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NexaIcon(
                                icon = if (reached) NexaIcons.Acknowledge else NexaIcons.Pending,
                                size = NexaTokens.IconSmall,
                                tint = if (reached) NexaSecure else NexaTextMuted
                            )
                            Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                            Text(
                                text = step.label,
                                style = NexaType.BodySecondary,
                                color = if (reached) NexaTextPrimary else NexaTextMuted
                            )
                        }
                    }
                }
            }
        }

        item { TargetRecap(state.context) }
        item { Spacer(modifier = Modifier.height(NexaTokens.SpacingXLarge)) }
    }
}

// ============================================================
// RESULT
// ============================================================

@Composable
private fun ResultContent(
    state: ActionUiState.Result,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val serious = state.state == ExecutionState.RollbackFailed

    NexaScreen(modifier = modifier, title = "Action Result", itemSpacing = NexaTokens.SpacingMedium) {
        item {
            GlassSurface(
                variant = if (serious) GlassVariant.Destructive else GlassVariant.Hero,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = NexaTokens.SpacingSmall)) {
                    Text(
                        text = state.context.action.code,
                        style = NexaType.Metadata,
                        color = NexaTextOnDarkMuted
                    )
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    Text(
                        text = state.state.label.uppercase(),
                        style = NexaType.Display,
                        color = state.state.status.style.onDark
                    )
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    Text(state.detail, style = NexaType.BodySecondary, color = NexaTextOnDark)
                }
            }
        }

        // Execution success is not reconciliation. They are reported apart.
        item { SectionHeader(text = "Enforcement state", level = SectionLevel.Group) }
        item {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ContextLine("Execution") {
                        StatusBadge(
                            status = state.state.status,
                            label = state.state.label.uppercase()
                        )
                    }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    ContextLine("Reconciliation") {
                        StatusBadge(
                            status = if (state.reconciled) NexaStatus.Secure else NexaStatus.Warning,
                            label = if (state.reconciled) "RECONCILED" else "NOT CONFIRMED"
                        )
                    }
                    if (!state.reconciled && state.state == ExecutionState.Succeeded) {
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                        Text(
                            text = "Execution completed, but the resulting enforcement state has not been confirmed. Do not treat this target as enforced until reconciliation completes.",
                            style = NexaType.Metadata,
                            color = NexaWarning
                        )
                    }
                    if (state.context.executionMode == ExecutionMode.AuditOnly) {
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                        Text(
                            text = "This ran in AUDIT_ONLY. No firewall mutation occurred.",
                            style = NexaType.Metadata,
                            color = NexaSimulation
                        )
                    }
                }
            }
        }

        if (serious) {
            item {
                GlassSurface(variant = GlassVariant.Strong, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.Top) {
                        NexaIcon(icon = NexaIcons.Critical, size = NexaTokens.IconLarge, tint = NexaCritical)
                        Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                        Column {
                            Text("Rollback failed", style = NexaType.Title, color = NexaCritical)
                            Spacer(modifier = Modifier.height(NexaTokens.SpacingHairline))
                            Text(
                                text = "The action did not complete and the target did not return to its prior state. This was not cancelled. Inspect the target before taking further action.",
                                style = NexaType.BodySecondary,
                                color = NexaTextSecondary
                            )
                        }
                    }
                }
            }
        }

        item { TargetRecap(state.context) }

        item {
            Spacer(modifier = Modifier.height(NexaTokens.SpacingLarge))
            NexaOutlinedButton(text = "Done", onClick = onDone, icon = NexaIcons.Acknowledge)
        }
        item { Spacer(modifier = Modifier.height(NexaTokens.SpacingXLarge)) }
    }
}

// ============================================================
// SHARED PIECES
// ============================================================

@Composable
private fun TargetRecap(context: ActionContext) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text("Target", style = NexaType.Metadata, color = NexaTextMuted)
            Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
            Text(context.target.label, style = NexaType.Body, color = NexaTextPrimary)
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            ContextLine("Scope") { TechnicalValue(context.target.scope, emphasized = true) }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            ContextLine("MAC") { TechnicalValue(context.target.mac) }
            if (context.target.identityId != null) {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                ContextLine("Identity") { TechnicalValue(context.target.identityId) }
            }
        }
    }
}

@Composable
private fun LiveEnforcementBanner() {
    GlassSurface(variant = GlassVariant.Destructive, modifier = Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexaIcon(icon = NexaIcons.Enforcing, size = NexaTokens.IconLarge, tint = NexaDangerOnDark)
                Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                Text("LIVE ENFORCEMENT", style = NexaType.Headline, color = NexaDangerOnDark)
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            Text(
                text = "THIS WILL MUTATE FIREWALL STATE",
                style = NexaType.Metadata,
                color = NexaTextOnDarkMuted
            )
        }
    }
}

@Composable
private fun UnknownModeBanner() {
    GlassSurface(variant = GlassVariant.Hero, modifier = Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexaIcon(icon = NexaIcons.Unknown, size = NexaTokens.IconLarge, tint = NexaTextOnDarkMuted)
                Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                Text("EXECUTION MODE UNKNOWN", style = NexaType.Headline, color = NexaTextOnDark)
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            Text(
                text = "NEXA cannot confirm whether this would simulate or mutate firewall state.",
                style = NexaType.Metadata,
                color = NexaTextOnDarkMuted
            )
        }
    }
}

@Composable
private fun ContextLine(label: String, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = NexaType.BodySecondary, color = NexaTextSecondary)
        value()
    }
}
