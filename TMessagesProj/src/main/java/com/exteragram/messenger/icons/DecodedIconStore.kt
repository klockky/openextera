package com.exteragram.messenger.icons

import android.graphics.Bitmap
import android.system.Os
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.Utilities
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Disk cache of already decoded (rasterized and scaled) icon bitmaps, stored as raw ARGB_8888 pixels.
 */
object DecodedIconStore {

    private const val MAGIC = 0x45544731 // "ETG1"
    private const val HEADER_SIZE = 16
    private const val PRUNE_EVERY_BYTES = 4L * 1024 * 1024
    private const val MAX_CACHE_SIZE = 32L * 1024 * 1024
    private const val TRIMMED_CACHE_SIZE = 24L * 1024 * 1024

    @Volatile
    private var directory: File? = null
    private val writtenSincePrune = AtomicLong(0)
    private val pruning = AtomicBoolean(false)

    private fun directory(): File? {
        directory?.let { return it }
        return try {
            val dir = File(ApplicationLoader.applicationContext.cacheDir, "icon_bitmaps")
            if (!dir.isDirectory && !dir.mkdirs()) {
                return null
            }
            directory = dir
            dir
        } catch (e: Exception) {
            FileLog.e("Failed to open the decoded icon cache", e)
            null
        }
    }

    private fun entryName(source: File, width: Int, height: Int, density: Int): String {
        var lastModified: Long
        var size: Long
        try {
            val stat = Os.stat(source.path)
            lastModified = stat.st_mtime
            size = stat.st_size
        } catch (e: Throwable) {
            lastModified = source.lastModified()
            size = source.length()
        }
        var hash = 1125899906842597L
        for (c in source.path) {
            hash = 31 * hash + c.code
        }
        hash = 31 * hash + lastModified
        hash = 31 * hash + size
        hash = 31 * hash + width
        hash = 31 * hash + height
        hash = 31 * hash + density
        return java.lang.Long.toHexString(hash)
    }

    fun get(source: File, width: Int, height: Int, density: Int): Bitmap? {
        val dir = directory() ?: return null
        val entry = File(dir, entryName(source, width, height, density))
        val expectedSize = width.toLong() * height.toLong() * 4 + HEADER_SIZE
        return try {
            RandomAccessFile(entry, "r").use { file ->
                val channel = file.channel
                if (channel.size() != expectedSize) {
                    return null
                }
                val buffer = ByteBuffer.allocateDirect(expectedSize.toInt()).order(ByteOrder.nativeOrder())
                while (buffer.hasRemaining()) {
                    if (channel.read(buffer) < 0) {
                        return null
                    }
                }
                buffer.flip()
                if (buffer.getInt() != MAGIC || buffer.getInt() != width || buffer.getInt() != height) {
                    return null
                }
                buffer.getInt() // density
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)
                bitmap.density = density
                bitmap
            }
        } catch (e: FileNotFoundException) {
            null
        } catch (e: Throwable) {
            runCatching { entry.delete() }
            null
        }
    }

    fun put(source: File, width: Int, height: Int, density: Int, bitmap: Bitmap) {
        if (bitmap.width != width || bitmap.height != height || bitmap.config != Bitmap.Config.ARGB_8888) {
            return
        }
        val dir = directory() ?: return
        val entry = File(dir, entryName(source, width, height, density))
        if (entry.isFile) {
            return
        }
        val tempFile = File(dir, entry.name + ".tmp")
        val size = width * height * 4 + HEADER_SIZE
        try {
            val buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
            buffer.putInt(MAGIC)
            buffer.putInt(width)
            buffer.putInt(height)
            buffer.putInt(density)
            bitmap.copyPixelsToBuffer(buffer)
            buffer.flip()
            RandomAccessFile(tempFile, "rw").use { file ->
                val channel = file.channel
                while (buffer.hasRemaining()) {
                    channel.write(buffer)
                }
            }
            if (!tempFile.renameTo(entry)) {
                tempFile.delete()
                return
            }
            if (writtenSincePrune.addAndGet(size.toLong()) >= PRUNE_EVERY_BYTES) {
                writtenSincePrune.set(0)
                Utilities.globalQueue.postRunnable { prune(dir) }
            }
        } catch (e: Throwable) {
            runCatching {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }
    }

    private fun prune(dir: File) {
        if (!pruning.compareAndSet(false, true)) {
            return
        }
        try {
            val files = dir.listFiles() ?: return
            var totalSize = files.sumOf { it.length() }
            if (totalSize > MAX_CACHE_SIZE) {
                files.sortBy { it.lastModified() }
                for (file in files) {
                    if (totalSize <= TRIMMED_CACHE_SIZE) {
                        break
                    }
                    val length = file.length()
                    if (file.delete()) {
                        totalSize -= length
                    }
                }
            }
        } catch (e: Exception) {
            FileLog.e("Failed to prune the decoded icon cache", e)
        } finally {
            pruning.set(false)
        }
    }
}
