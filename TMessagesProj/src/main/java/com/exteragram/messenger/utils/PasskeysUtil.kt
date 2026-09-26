package com.exteragram.messenger.utils

import android.app.Activity
import android.os.Build
import androidx.credentials.CredentialManager
import org.json.JSONObject
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.browser.Browser
import org.telegram.ui.Components.Bulletin
import org.telegram.ui.Components.BulletinFactory
import java.security.MessageDigest

object PasskeysUtil {

    @JvmStatic
    fun showUnsupportedBulletin(bulletinFactory: BulletinFactory): Bulletin {
        return bulletinFactory.createSimpleBulletin(
            R.raw.error,
            LocaleController.getString(R.string.PasskeyUnsupportedTitle),
            AndroidUtilities.replaceMultipleTags(
                LocaleController.getString(R.string.PasskeyUnsupportedMessage),
                Runnable { Browser.openUrl(ApplicationLoader.applicationContext, "https://github.com/bitwarden/android") },
                Runnable { Browser.openUrl(ApplicationLoader.applicationContext, "https://github.com/Kunzisoft/KeePassDX") }
            )
        ).show()
    }

    @JvmStatic
    fun computeClientDataHash(clientDataJson: String): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(clientDataJson.toByteArray(Charsets.UTF_8))
    }

    @JvmStatic
    fun generateClientDataJSONRaw(isGet: Boolean, challenge: String, origin: String): String {
        return JSONObject()
            .put("type", if (isGet) "webauthn.get" else "webauthn.create")
            .put("challenge", challenge)
            .put("origin", origin)
            .toString()
    }

    @JvmStatic
    fun openSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT < 34) {
            return
        }
        runCatching {
            CredentialManager.create(activity).createSettingsPendingIntent().send()
        }
    }
}
