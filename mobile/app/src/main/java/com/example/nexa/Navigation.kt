package com.example.nexa

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.nexa.theme.NexaIcons
import com.example.nexa.ui.components.EmptyState
import com.example.nexa.ui.components.NavItem
import com.example.nexa.ui.components.NexaBottomNavigationBar
import com.example.nexa.ui.main.ActionConfirmationScreen
import com.example.nexa.ui.main.AlertDetailScreen
import com.example.nexa.ui.main.DeviceDetailScreen
import com.example.nexa.ui.main.DevicesScreen
import com.example.nexa.ui.main.OverviewScreen

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Overview)
    
    val currentKey by remember {
        derivedStateOf { backStack.lastOrNull() }
    }
    
    val isRoot = currentKey == Overview || currentKey == Devices || currentKey == Alerts || currentKey == Audit

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
                    modifier = Modifier.padding(bottom = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                )
            }
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
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
                        EmptyState("Alerts", "Placeholder for Alerts view.", modifier = Modifier.safeDrawingPadding(), icon = NexaIcons.Alerts)
                    }
                    entry<Audit> {
                        EmptyState("Audit", "Placeholder for Audit log.", modifier = Modifier.safeDrawingPadding(), icon = NexaIcons.Audit)
                    }
                    entry<DeviceDetail> { deviceDetail ->
                        DeviceDetailScreen(
                            mac = deviceDetail.mac,
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
                    entry<ActionConfirmation> { actionConfirmation ->
                        ActionConfirmationScreen(
                            action = actionConfirmation.action,
                            targetMac = actionConfirmation.targetMac,
                            actionLabel = actionConfirmation.actionLabel,
                            onBack = { backStack.removeLastOrNull() },
                            modifier = Modifier.safeDrawingPadding()
                        )
                    }
                }
            )
        }
    }
}
