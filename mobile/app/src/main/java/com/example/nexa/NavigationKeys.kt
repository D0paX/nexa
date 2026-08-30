package com.example.nexa

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Overview : NavKey

@Serializable data class DeviceDetail(val mac: String) : NavKey

@Serializable data class AlertDetail(val id: String) : NavKey

@Serializable data class ActionConfirmation(
    val action: String,
    val targetMac: String,
    val actionLabel: String
) : NavKey
