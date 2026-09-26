package org.simplifiles.internal.io

import org.simplifiles.exception.FileOperationException
import org.simplifiles.files.DirectoryTransferOptions
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.streams.asSequence

internal object FileTreeCopier {

    fun validateDirectory(source: Path, options: DirectoryTransferOptions) {
        Files.walk(source).use { stream ->
            var fileCount = 0L
            var byteCount = 0L
            stream.asSequence()
                .filter { included(it, options) }
                .filter { Files.isRegularFile(it) }
                .forEach { file ->
                    fileCount++
                    if (fileCount > options.maxFiles) {
                        throw FileOperationException("Directory exceeds copy limit of ${options.maxFiles} files: $source")
                    }
                    byteCount += Files.size(file)
                    if (byteCount > options.maxBytes) {
                        throw FileOperationException("Directory exceeds copy limit of ${options.maxBytes} bytes: $source")
                    }
                }
        }
    }

    fun copyDirectory(source: Path, target: Path, options: DirectoryTransferOptions) {
        validateDirectory(source, options)
        Files.walk(source).use { stream ->
            stream.asSequence()
                .filter { included(it, options) }
                .sortedBy { it.nameCount }
                .forEach { sourcePath ->
                    val targetPath = target.resolve(source.relativize(sourcePath))
                    if (Files.isDirectory(sourcePath)) {
                        if (Files.exists(targetPath) && !Files.isDirectory(targetPath)) {
                            Files.deleteIfExists(targetPath)
                        }
                        Files.createDirectories(targetPath)
                    } else {
                        Files.createDirectories(targetPath.parent)
                        if (Files.isDirectory(targetPath)) {
                            FileTreeCleaner.deleteRecursively(targetPath)
                        }
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
        }
    }

    private fun included(path: Path, options: DirectoryTransferOptions): Boolean {
        return when (SymlinkSupport.decide(path, options.symlinkPolicy)) {
            SymlinkDecision.INCLUDE -> true
            SymlinkDecision.SKIP -> false
            SymlinkDecision.FAIL -> throw FileOperationException("Directory contains a symbolic link: $path")
        }
    }
}
