package com.exteragram.messenger.debug

import com.exteragram.messenger.config.BooleanPref

object DebugConfig {
    @JvmStatic var debugCameraMetrics by BooleanPref(false)
    @JvmStatic var forceCompactSavedMusic by BooleanPref(false)
    @JvmStatic var disableApiRequests by BooleanPref(false)
    @JvmStatic var disableChatFadeWallpaperBlend by BooleanPref(false)
    @JvmStatic var chatFadeUseWhiteBackground by BooleanPref(false)
}
