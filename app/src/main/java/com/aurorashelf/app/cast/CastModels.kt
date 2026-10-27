package com.aurorashelf.app.cast

/** Represents a discovered DLNA / UPnP rendering device (e.g. Smart TV, TV Box). */
data class CastDevice(
    val id: String,
    val name: String,
    val manufacturer: String = "",
    val modelName: String = "",
    val location: String,
    val avTransportUrl: String,
    val renderingControlUrl: String? = null,
    val ipAddress: String = "",
)

/** Represents the real-time casting state. */
sealed interface CastState {
    data object Idle : CastState

    data class Connecting(val device: CastDevice) : CastState

    data class Connected(
        val device: CastDevice,
        val mediaUrl: String,
        val title: String,
        val isPlaying: Boolean = true,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
    ) : CastState

    data class Error(val message: String) : CastState
}
