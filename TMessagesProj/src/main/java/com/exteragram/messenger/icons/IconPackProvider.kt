package com.exteragram.messenger.icons

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.R
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * Exposes custom icon pack images (rasterized to PNG) to other apps, e.g. SystemUI for the notification small icon.
 */
class IconPackProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: return true
        if (ApplicationLoader.applicationContext == null) {
            ApplicationLoader.applicationContext = appContext
        }
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun getType(uri: Uri): String? {
        return if (isIconUri(uri)) "image/png" else null
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") {
            throw SecurityException("Icon pack files are read-only")
        }
        if (!isIconUri(uri)) {
            throw FileNotFoundException(uri.toString())
        }
        val file = runCatching { resolveSource(uri)?.let { getRasterizedIcon(it) } }.getOrNull()
            ?: getFallbackIcon()
            ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun isIconUri(uri: Uri): Boolean {
        val segments = uri.pathSegments
        return segments.size == 3 && segments[0] == PATH_ICON
    }

    private fun resolveSource(uri: Uri): File? {
        if (!isIconUri(uri)) {
            return null
        }
        val segments = uri.pathSegments
        val file = IconPackStorage.resolveIconFile(segments[1], segments[2]) ?: return null
        return if (file.extension.lowercase(Locale.ROOT) in SUPPORTED_EXTENSIONS) file else null
    }

    private fun getFallbackIcon(): File? {
        val densityDpi = context?.resources?.displayMetrics?.densityDpi ?: return null
        return materializeCacheFile("default_$densityDpi.png") { context, out ->
            val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.notification) ?: return@materializeCacheFile false
            try {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun getRasterizedIcon(source: File): File? {
        val densityDpi = context?.resources?.displayMetrics?.densityDpi ?: return null
        val key = "${source.canonicalPath}:${source.lastModified()}:${source.length()}:$densityDpi"
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { String.format("%02x", it.toInt() and 0xff) }
        return materializeCacheFile("$hash.png") { context, out ->
            val bitmap = IconManager.createBitmapFromFile(
                source.absolutePath,
                R.drawable.notification,
                context.resources.displayMetrics.densityDpi,
                null
            ) ?: return@materializeCacheFile false
            try {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun materializeCacheFile(name: String, writer: (Context, FileOutputStream) -> Boolean): File? {
        val context = context ?: return null
        val dir = File(context.cacheDir, "notification_icons")
        if (!dir.exists() && !dir.mkdirs()) {
            return null
        }
        val target = File(dir, name)
        if (target.isFile && target.length() > 0) {
            return target
        }
        synchronized(IconPackProvider::class.java) {
            if (target.isFile && target.length() > 0) {
                return target
            }
            val tempFile = File(dir, "$name.tmp")
            try {
                val written = FileOutputStream(tempFile).use { writer(context, it) }
                if (!written) {
                    return null
                }
                if (!tempFile.renameTo(target)) {
                    tempFile.copyTo(target, overwrite = true)
                    tempFile.delete()
                }
                return target
            } catch (e: Exception) {
                return null
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }
    }

    companion object {
        private const val PATH_ICON = "icon"
        private val SUPPORTED_EXTENSIONS = setOf("png", "webp", "jpg", "jpeg", "svg")

        fun getIconUri(packId: String, resourceName: String): Uri? {
            val file = IconPackStorage.resolveIconFile(packId, resourceName) ?: return null
            if (file.extension.lowercase(Locale.ROOT) !in SUPPORTED_EXTENSIONS) {
                return null
            }
            return Uri.Builder()
                .scheme("content")
                .authority(ApplicationLoader.getApplicationId() + ".icon_pack_provider")
                .appendPath(PATH_ICON)
                .appendPath(packId)
                .appendPath(resourceName)
                .appendQueryParameter("v", "${file.lastModified()}_${file.length()}")
                .build()
        }
    }
}
