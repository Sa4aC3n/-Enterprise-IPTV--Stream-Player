package com.enterprise.iptv.domain.cast

/**
 * يمثل جهاز استقبال البث التلفزيوني المكتشف محلياً عبر الشبكة (Smart TV / DLNA / Chromecast)
 */
data class CastDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val location: String = "",
    val avTransportControlUrl: String = "",
    val renderingControlUrl: String = "",
    val manufacturer: String = "",
    val modelName: String = "",
    val deviceType: CastDeviceType = CastDeviceType.DLNA_SMART_TV,
    val isConnected: Boolean = false
)

enum class CastDeviceType {
    DLNA_SMART_TV,
    CHROMECAST,
    ANDROID_TV,
    ROKU,
    GENERIC_RENDERER
}

sealed interface CastState {
    data object Disconnected : CastState
    data class Connecting(val device: CastDevice) : CastState
    data class Connected(val device: CastDevice, val mediaTitle: String) : CastState
    data class Playing(val device: CastDevice, val mediaTitle: String) : CastState
    data class Paused(val device: CastDevice, val mediaTitle: String) : CastState
    data class Error(val message: String) : CastState
}
