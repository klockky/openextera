package org.simplifiles.internal.files

import org.simplifiles.exception.UnsafePathException
import java.nio.file.Path
import java.nio.file.Paths

internal object SafePathResolver {

    private val WINDOWS_ABSOLUTE_PATH = Regex("^[A-Za-z]:[/\\\\].*")

    fun resolveInside(root: Path, relativePath: String): Path {
        if (relativePath.isBlank()) {
            throw UnsafePathException(relativePath, "path must not be blank")
        }
        if (relativePath.startsWith("/") || relativePath.startsWith("\\") || WINDOWS_ABSOLUTE_PATH.matches(relativePath)) {
            throw UnsafePathException(relativePath, "path must be relative")
        }
        if (relativePath.split('/', '\\').any { it == ".." }) {
            throw UnsafePathException(relativePath, "path must not contain parent traversal")
        }
        val relative = Paths.get(relativePath)
        if (relative.isAbsolute) {
            throw UnsafePathException(relativePath, "path must be relative")
        }
        val normalizedRoot = root.toAbsolutePath().normalize()
        val resolved = normalizedRoot.resolve(relative).normalize()
        if (!resolved.startsWith(normalizedRoot)) {
            throw UnsafePathException(relativePath, "path escapes root")
        }
        return resolved
    }
}
