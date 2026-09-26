package org.simplifiles.archive

import org.simplifiles.internal.archive.ArchivePathResolver
import org.simplifiles.internal.io.FileTreeCleaner
import java.io.IOException
import java.nio.file.Path

class ExtractedArchive internal constructor(
    val root: Path,
    private val cleanupOnClose: Boolean
) : AutoCloseable {

    @Throws(IOException::class)
    fun file(path: String): ArchiveFile {
        val resolved = ArchivePathResolver.resolve(root, path)
        return ArchiveFile(root, root.relativize(resolved).toString().replace('\\', '/'), resolved)
    }

    @Throws(IOException::class)
    override fun close() {
        if (cleanupOnClose) {
            FileTreeCleaner.deleteRecursively(root)
        }
    }
}
