package com.example.nexa

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import com.example.nexa.ui.components.EmptyState
import com.example.nexa.ui.components.NavItem
import com.example.nexa.ui.components.NexaBottomNavigationBar
import com.example.nexa.ui.main.ActionConfirmationScreen
import com.example.nexa.ui.main.AlertDetailScreen
import com.example.nexa.ui.main.DeviceDetailScreen
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
                        NavItem("Overview", Icons.Outlined.Home, currentKey == Overview) { if (currentKey != Overview) backStack.add(Overview) },
                        NavItem("Devices", Icons.Outlined.PhoneAndroid, currentKey == Devices) { if (currentKey != Devices) backStack.add(Devices) },
                        NavItem("Alerts", Icons.Outlined.Notifications, currentKey == Alerts) { if (currentKey != Alerts) backStack.add(Alerts) },
                        NavItem("Audit", Icons.Outlined.History, currentKey == Audit) { if (currentKey != Audit) backStack.add(Audit) }
                    )
                )
            }
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
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
                        EmptyState("Devices", "Placeholder for Devices view.", modifier = Modifier.safeDrawingPadding())
                    }
                    entry<Alerts> {
                        EmptyState("Alerts", "Placeholder for Alerts view.", modifier = Modifier.safeDrawingPadding())
                    }
                    entry<Audit> {
                        EmptyState("Audit", "Placeholder for Audit log.", modifier = Modifier.safeDrawingPadding())
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
