package com.example.nexa.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexa.theme.NexaIcons
import com.example.nexa.theme.NexaStatus
import com.example.nexa.theme.style
import com.example.nexa.ui.realtime.RealtimeConnectionState
import com.example.nexa.ui.realtime.RealtimeFreshness
import com.example.nexa.ui.realtime.RealtimeStore
import kotlinx.coroutines.delay

/**
 * Whether NEXA is currently hearing about changes.
 *
 * A small badge, not a network dashboard. The one thing it must never do is
 * read as a security verdict: a stream being down means this console has
 * stopped being told things, not that the network is unsafe. The wording is
 * chosen accordingly — "live", "reconnecting", "stale" describe the feed.
 *
 * It is absent when everything is fine and current. A permanent green badge
 * teaches an operator to stop looking at it, which costs exactly the moment
 * it was there for.
 */
@Composable
fun RealtimeIndicator(modifier: Modifier = Modifier) {
    val connection by RealtimeStore.connection.collectAsStateWithLifecycle()
    val lastEventAt by RealtimeStore.lastEventAtMillis.collectAsStateWithLifecycle()

    // Freshness is a function of elapsed time, so it is re-evaluated on a slow
    // tick rather than only when an event happens to arrive. A feed that has
    // gone quiet must be able to age into "stale" on its own.
    val freshness by produceState(
        initialValue = RealtimeFreshness.Unknown,
        connection,
        lastEventAt
    ) {
        while (true) {
            value = RealtimeFreshness.of(connection, lastEventAt, System.currentTimeMillis())
            delay(FRESHNESS_TICK_MS)
        }
    }

    val presentation = realtimeIndicator(connection, freshness) ?: return

    StatusBadge(
        text = presentation.label,
        color = presentation.status.style.onLight,
        icon = presentation.icon,
        modifier = modifier
    )
}

/** What the badge says, or null when there is nothing worth saying. */
internal data class RealtimeIndicatorPresentation(
    val label: String,
    val status: NexaStatus,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

/**
 * The badge's wording, derived outside composition so it can be asserted.
 *
 * Returns null for a healthy live feed. Nothing needs saying then, and the
 * absence keeps the badge meaningful when it does appear.
 */
internal fun realtimeIndicator(
    connection: RealtimeConnectionState,
    freshness: RealtimeFreshness
): RealtimeIndicatorPresentation? = when {
    connection == RealtimeConnectionState.Connected && freshness == RealtimeFreshness.Stale ->
        RealtimeIndicatorPresentation("FEED STALE", NexaStatus.Warning, NexaIcons.Stale)

    connection == RealtimeConnectionState.Connected -> null

    connection == RealtimeConnectionState.Connecting ||
        connection == RealtimeConnectionState.Reconnecting ->
        RealtimeIndicatorPresentation("RECONNECTING", NexaStatus.Information, NexaIcons.Reconnecting)

    connection == RealtimeConnectionState.Degraded ->
        RealtimeIndicatorPresentation("FEED INCOMPLETE", NexaStatus.Warning, NexaIcons.Reconnecting)

    connection == RealtimeConnectionState.Failed ->
        RealtimeIndicatorPresentation("FEED OFFLINE", NexaStatus.Offline, NexaIcons.Offline)

    else -> RealtimeIndicatorPresentation("FEED OFFLINE", NexaStatus.Offline, NexaIcons.Offline)
}

private const val FRESHNESS_TICK_MS = 5_000L
