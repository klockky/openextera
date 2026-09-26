package org.simplifiles.internal.archive

import org.simplifiles.exception.UnsafeArchivePathException
import java.nio.file.Path

internal object ArchivePathResolver {

    /**
     * Resolves an archive entry path inside [root]. Absolute entry paths (when allowed) are
     * re-rooted under [root]; parent traversal and anything escaping [root] is rejected.
     */
    fun resolve(root: Path, entryPath: String, allowAbsolutePaths: Boolean = false): Path {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val analysis = ArchivePathAnalyzer.analyze(entryPath)
        val normalizedPath = analysis.normalizedPath
        if (analysis.isAbsolute && !allowAbsolutePaths) {
            throw UnsafeArchivePathException(entryPath, "path must be relative")
        }
        if (analysis.containsParentTraversal) {
            throw UnsafeArchivePathException(entryPath, "path must not contain parent traversal")
        }
        if (analysis.isEmpty || normalizedPath == null) {
            throw UnsafeArchivePathException(entryPath, "path must not be empty")
        }
        val resolved = normalizedRoot.resolve(normalizedPath).normalize()
        if (!resolved.startsWith(normalizedRoot)) {
            throw UnsafeArchivePathException(entryPath, "path escapes archive root")
        }
        return resolved
    }
}
