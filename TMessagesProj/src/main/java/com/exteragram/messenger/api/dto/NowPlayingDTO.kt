package com.exteragram.messenger.api.dto

data class NowPlayingDTO(
    val trackName: String,
    val artists: List<String>?,
    val albumName: String?,
    val coverUrl: String?,
    val previewUrl: String?,
    val songUrl: String?,
    val isPlaying: Boolean,
    val deviceName: String?,
    val platform: String?,
    val duration: Long?
)
