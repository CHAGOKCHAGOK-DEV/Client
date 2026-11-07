package com.chagok.chagok.model

enum class PermissionStatus { GRANTED, NEEDED, DENIED, CHECKING }

enum class PermissionKind { VEHICLE, MIC, VOICE }

data class PermissionState(
    val vehicle: PermissionStatus = PermissionStatus.CHECKING,
    val mic: PermissionStatus = PermissionStatus.CHECKING,
    val voice: PermissionStatus = PermissionStatus.CHECKING,
) {
    val allGranted: Boolean
        get() = vehicle == PermissionStatus.GRANTED &&
                mic == PermissionStatus.GRANTED &&
                voice == PermissionStatus.GRANTED

    val anyDenied: Boolean
        get() = vehicle == PermissionStatus.DENIED ||
                mic == PermissionStatus.DENIED ||
                voice == PermissionStatus.DENIED

    fun with(kind: PermissionKind, status: PermissionStatus) = when (kind) {
        PermissionKind.VEHICLE -> copy(vehicle = status)
        PermissionKind.MIC -> copy(mic = status)
        PermissionKind.VOICE -> copy(voice = status)
    }
}