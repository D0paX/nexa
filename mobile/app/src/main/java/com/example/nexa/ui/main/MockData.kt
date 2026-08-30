package com.example.nexa.ui.main

object MockData {
    val activeDevices = 42
    val activeAlerts = 3
    val systemStatus = "ENFORCING"
    
    val recentAlerts = listOf(
        AlertItemData("ALRT-1092", "Suspicious Port Scan", "00:1A:2B:3C:4D:5E", "CRITICAL", "2m ago"),
        AlertItemData("ALRT-1091", "Untrusted MAC in Trusted VLAN", "00:5E:4D:3C:2B:1A", "WARNING", "14m ago"),
        AlertItemData("ALRT-1090", "Device Offline", "AA:BB:CC:DD:EE:FF", "INFORMATION", "1h ago")
    )
}

data class AlertItemData(
    val id: String,
    val description: String,
    val targetMac: String,
    val severity: String, // CRITICAL, WARNING, INFORMATION
    val timeAgo: String
)
