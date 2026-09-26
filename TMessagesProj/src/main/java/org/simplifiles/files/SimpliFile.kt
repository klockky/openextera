package org.simplifiles.files

import org.simplifiles.exception.FileOperationException
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

data class SimpliFile(val path: Path) {

    val file: File
        get() = File(path.toString())

    val exists: Boolean
        get() = Files.exists(path)

    fun exists(): Boolean = exists

    @Throws(IOException::class)
    fun readBytes(): ByteArray = Files.readAllBytes(path)

    @Throws(IOException::class)
    fun readBytes(maxBytes: Long): ByteArray {
        require(maxBytes >= 0) { "maxBytes must not be negative." }
        if (Files.size(path) > maxBytes) {
            throw FileOperationException("File exceeds read limit of $maxBytes bytes: $path")
        }
        return readBytes()
    }

    @JvmOverloads
    @Throws(IOException::class)
    fun readText(maxBytes: Long, charset: Charset = Charsets.UTF_8): String = String(readBytes(maxBytes), charset)

    @JvmOverloads
    @Throws(IOException::class)
    fun writeTextAtomic(text: String, charset: Charset = Charsets.UTF_8) {
        writeBytesAtomic(text.toByteArray(charset))
    }

    @Throws(IOException::class)
    fun writeBytesAtomic(bytes: ByteArray) {
        val parent = path.parent ?: Paths.get(".").toAbsolutePath().normalize()
        Files.createDirectories(parent)
        val fileName = path.fileName?.toString() ?: throw FileOperationException("File path must include a file name.")
        val tempFile = Files.createTempFile(parent, ".$fileName.", ".tmp")
        try {
            Files.write(tempFile, bytes, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
            try {
                Files.move(tempFile, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (t: Throwable) {
            Files.deleteIfExists(tempFile)
            throw t
        }
    }

    @Throws(IOException::class)
    fun delete(): Boolean = Files.deleteIfExists(path)

    @Throws(IOException::class)
    fun copyTo(target: Path, overwritePolicy: OverwritePolicy): SimpliFile {
        target.parent?.let { Files.createDirectories(it) }
        when (overwritePolicy) {
            OverwritePolicy.ERROR -> {
                if (Files.exists(target)) {
                    throw FileOperationException("Target already exists: $target")
                }
                Files.copy(path, target)
            }
            OverwritePolicy.REPLACE -> Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING)
            OverwritePolicy.SKIP -> {
                if (!Files.exists(target)) {
                    Files.copy(path, target)
                }
            }
        }
        return SimpliFile(target)
    }

    @Throws(IOException::class)
    fun copyTo(target: File, overwritePolicy: OverwritePolicy): SimpliFile = copyTo(Paths.get(target.path), overwritePolicy)
}
