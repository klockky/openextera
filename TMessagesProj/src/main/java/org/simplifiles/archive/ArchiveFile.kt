package org.simplifiles.archive

import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

class ArchiveFile internal constructor(
    private val root: Path,
    val path: String,
    val absolutePath: Path
) {

    val exists: Boolean
        get() = Files.exists(absolutePath)

    val size: Long
        @Throws(IOException::class)
        get() = Files.size(absolutePath)

    fun exists(): Boolean = exists

    @Throws(IOException::class)
    fun readBytes(): ByteArray = Files.readAllBytes(absolutePath)

    @JvmOverloads
    @Throws(IOException::class)
    fun readText(charset: Charset = Charsets.UTF_8): String = String(readBytes(), charset)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is ArchiveFile) {
            return false
        }
        return root == other.root && absolutePath == other.absolutePath
    }

    override fun hashCode(): Int = root.hashCode() * 31 + absolutePath.hashCode()

    override fun toString(): String = "ArchiveFile(path=$path, absolutePath=$absolutePath)"
}
