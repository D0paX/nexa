package com.example.nexa

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.nexa.ui.main.ActionConfirmationScreen
import com.example.nexa.ui.main.AlertDetailScreen
import com.example.nexa.ui.main.DeviceDetailScreen
import com.example.nexa.ui.main.OverviewScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Overview)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Overview> {
          OverviewScreen(
              onItemClick = { navKey -> backStack.add(navKey) },
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
      },
  )
}
