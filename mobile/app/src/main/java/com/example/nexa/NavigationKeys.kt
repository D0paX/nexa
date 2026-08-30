package com.example.nexa

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Root contexts
@Serializable data object Overview : NavKey
@Serializable data object Devices : NavKey
@Serializable data object Alerts : NavKey
@Serializable data object Audit : NavKey

// Drill-downs
@Serializable data class DeviceDetail(val mac: String) : NavKey
@Serializable data class AlertDetail(val id: String) : NavKey

// High-impact Actions
@Serializable data class ActionConfirmation(
    val action: String,
    val targetMac: String,
    val actionLabel: String
) : NavKey
