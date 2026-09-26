package org.simplifiles.internal.io

import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

internal object FileTreeCleaner {

    /** Deletes everything inside [root] but keeps [root] itself. Symbolic links are removed, never followed. */
    fun deleteContents(root: Path) {
        if (!Files.exists(root)) {
            return
        }
        Files.walk(root).use { stream ->
            stream.asSequence()
                .filter { it != root }
                .sortedByDescending { it.nameCount }
                .forEach { Files.deleteIfExists(it) }
        }
    }

    /** Deletes [root] and everything inside it. Symbolic links are removed, never followed. */
    fun deleteRecursively(root: Path) {
        if (!Files.exists(root)) {
            return
        }
        Files.walk(root).use { stream ->
            stream.asSequence()
                .sortedByDescending { it.nameCount }
                .forEach { Files.deleteIfExists(it) }
        }
    }
}
