package com.example.nexa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.nexa.theme.NexaIcons
import com.example.nexa.ui.components.NavItem
import com.example.nexa.ui.components.NexaBottomNavigationBar
import com.example.nexa.ui.main.ActionConfirmationScreen
import com.example.nexa.ui.main.AlertDetailScreen
import com.example.nexa.ui.main.AlertsScreen
import com.example.nexa.ui.main.AuditDetailScreen
import com.example.nexa.ui.main.AuditScreen
import com.example.nexa.ui.main.DeviceDetailScreen
import com.example.nexa.ui.main.DevicesScreen
import com.example.nexa.ui.main.IdentitiesScreen
import com.example.nexa.ui.main.IdentityDetailScreen
import com.example.nexa.ui.main.LinkProblemScreen
import com.example.nexa.ui.main.NotificationCenterScreen
import com.example.nexa.ui.main.NotificationDetailScreen
import com.example.nexa.ui.main.OverviewScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexa.ui.deeplink.DeepLinkRouter
import com.example.nexa.ui.deeplink.toNavKey
import com.example.nexa.ui.navigation.NavigationDirectionResolver
import com.example.nexa.ui.navigation.navigationTransform
import com.example.nexa.ui.navigation.rememberReducedMotion

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Overview)

    val currentKey by remember {
        derivedStateOf { backStack.lastOrNull() }
    }

    val isRoot = currentKey == Overview || currentKey == Devices ||
        currentKey == Alerts || currentKey == Audit

    val reducedMotion = rememberReducedMotion()

    // A link asks to be taken somewhere. The destination is pushed onto the
    // existing stack rather than replacing it, so Back returns to wherever the
    // operator already was — or to Overview on a cold start. A link that
    // resolves to the screen already on top does nothing, which is what keeps
    // a repeated tap from stacking duplicates.
    val pendingLink by DeepLinkRouter.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingLink) {
        val resolution = pendingLink ?: return@LaunchedEffect
        val key = resolution.toNavKey()
        val top = backStack.lastOrNull()
        when {
            // The same link twice does nothing, so a repeated tap cannot
            // stack duplicates.
            top == key -> Unit
            // One failed link followed by another replaces the message rather
            // than burying it, so Back does not walk through a pile of them.
            key is LinkProblem && top is LinkProblem -> {
                backStack.removeLastOrNull()
                backStack.add(key)
            }
            else -> backStack.add(key)
        }
        DeepLinkRouter.consume()
    }

    // Direction is resolved once per destination change and read by both
    // transition specs, so push and pop agree about which way the application
    // is travelling instead of each guessing separately.
    val resolver = remember { NavigationDirectionResolver() }
    val direction = remember(currentKey, backStack.size) {
        resolver.resolve(currentKey, backStack.size)
    }

    Scaffold(
        bottomBar = {
            if (isRoot) {
                NexaBottomNavigationBar(
                    items = listOf(
                        NavItem("Overview", NexaIcons.Overview, currentKey == Overview) { if (currentKey != Overview) backStack.add(Overview) },
                        NavItem("Devices", NexaIcons.Devices, currentKey == Devices) { if (currentKey != Devices) backStack.add(Devices) },
                        NavItem("Alerts", NexaIcons.Alerts, currentKey == Alerts) { if (currentKey != Alerts) backStack.add(Alerts) },
                        NavItem("Audit", NexaIcons.Audit, currentKey == Audit) { if (currentKey != Audit) backStack.add(Audit) }
                    ),
                    modifier = Modifier.padding(
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    )
                )
            }
        },
        containerColor = Color.Transparent
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                transitionSpec = { navigationTransform(direction, reducedMotion) },
                popTransitionSpec = { navigationTransform(direction, reducedMotion) },
                predictivePopTransitionSpec = { _ -> navigationTransform(direction, reducedMotion) },
                entryProvider = entryProvider {
                    entry<Overview> {
                        OverviewScreen(
                            onItemClick = { navKey -> backStack.add(navKey) },
                            modifier = Modifier.safeDrawingPadding()
                        )
                    }
                    entry<Devices> {
                        DevicesScreen(
                            onItemClick = { navKey -> backStack.add(navKey) },
                            modifier = Modifier.safeDrawingPadding()
                        )
                    }
                    entry<Alerts> {
                        AlertsScreen(
                            onItemClick = { navKey -> backStack.add(navKey) },
                            modifier = Modifier.safeDrawingPadding()
                        )
                    }
                    entry<Audit> {
                        AuditScreen(
                            onItemClick = { navKey -> backStack.add(navKey) },
                            modifier = Modifier.safeDrawingPadding()
                        )
                    }
                    entry<AuditDetail> { auditDetail ->
                        AuditDetailScreen(
                            eventId = auditDetail.eventId,
                            onBack = { backStack.removeLastOrNull() },
                            onNavigate = { navKey -> backStack.add(navKey) },
                            modifier = Modifier.safeDrawingPadding()
                        )
                    }
                    entry<DeviceDetail> { deviceDetail ->
                        DeviceDetailScreen(
                            mac = deviceDetail.mac,
                            onBack = { backStack.removeLastOrNull() },
                            onNavigate = { navKey -> backStack.add(navKey) },
                            modifier = Modifier.safeDrawingPadding()
                        )
                    }
                    entry<Identities> {
                        IdentitiesScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onNavigate = { navKey -> backStack.add(navKey) },
                            modifier = Modifier.safeDrawingPadding()
                        )
                    }
                    entry<IdentityDetail> { identityDetail ->
                        IdentityDetailScreen(
                            identityId = identityDetail.identityId,
                            onBack = { backStack.removeLastOrNull() },
                            onNavigate = { navKey -> backStack.add(navKey) },
                            modifier = Modifier.safeDrawingPadding()
                        )
                    }
                    entry<NotificationCenter> {
                        NotificationCenterScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onNavigate = { navKey -> backStack.add(navKey) },
                            modifier = Modifier.safeDrawingPadding()
                        )
                    }
                    entry<NotificationDetail> { notificationDetail ->
                        NotificationDetailScreen(
                            deliveryId = notificationDetail.deliveryId,
                            onBack = { backStack.removeLastOrNull() },
                            onNavigate = { navKey -> backStack.add(navKey) },
                            modifier = Modifier.safeDrawingPadding()
                        )
                    }
                    entry<AlertDetail> { alertDetail ->
                        AlertDetailScreen(
                            alertId = alertDetail.id,
                            onBack = { backStack.removeLastOrNull() },
                            onNavigate = { navKey -> backStack.add(navKey) },
                            modifier = Modifier.safeDrawingPadding()
                        )
                    }
                    entry<LinkProblem> { problem ->
                        LinkProblemScreen(
                            title = problem.title,
                            message = problem.message,
                            onBack = { backStack.removeLastOrNull() },
                            modifier = Modifier.safeDrawingPadding()
                        )
                    }
                    entry<ActionConfirmation> { actionConfirmation ->
                        ActionConfirmationScreen(
                            actionContextId = actionConfirmation.actionContextId,
                            onBack = { backStack.removeLastOrNull() },
                            onDone = { backStack.removeLastOrNull() },
                            modifier = Modifier.safeDrawingPadding()
                        )
                    }
                }
            )
        }
    }
}
