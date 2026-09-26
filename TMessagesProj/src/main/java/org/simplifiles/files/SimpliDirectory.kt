package org.simplifiles.files

import org.simplifiles.archive.ArchiveSaveOptions
import org.simplifiles.exception.FileOperationException
import org.simplifiles.internal.archive.zip.ZipArchiveWriter
import org.simplifiles.internal.files.SafePathResolver
import org.simplifiles.internal.io.FileTreeCleaner
import org.simplifiles.internal.io.FileTreeCopier
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

data class SimpliDirectory(val path: Path) {

    val file: File
        get() = File(path.toString())

    val exists: Boolean
        get() = Files.isDirectory(path)

    fun exists(): Boolean = exists

    @Throws(IOException::class)
    fun create(): SimpliDirectory {
        Files.createDirectories(path)
        return this
    }

    @Throws(IOException::class)
    fun deleteRecursively(): Boolean {
        if (!exists) {
            return false
        }
        FileTreeCleaner.deleteRecursively(path)
        return true
    }

    @Throws(IOException::class)
    fun resolveInside(relativePath: String): Path = SafePathResolver.resolveInside(path, relativePath)

    @Throws(IOException::class)
    fun file(relativePath: String): SimpliFile = SimpliFile(resolveInside(relativePath))

    @Throws(IOException::class)
    fun zipTo(target: Path, options: ArchiveSaveOptions): SimpliFile {
        if (!exists) {
            throw FileOperationException("Directory does not exist: $path")
        }
        ZipArchiveWriter.write(path, target, options)
        return SimpliFile(target)
    }

    @Throws(IOException::class)
    fun zipTo(target: File, options: ArchiveSaveOptions): SimpliFile = zipTo(Paths.get(target.path), options)

    @Throws(IOException::class)
    fun copyTo(target: Path, overwritePolicy: OverwritePolicy): SimpliDirectory {
        return copyTo(target, toDirectoryTransferOptions(overwritePolicy))
    }

    @Throws(IOException::class)
    fun copyTo(target: Path, options: DirectoryTransferOptions): SimpliDirectory {
        ensureExists()
        rejectSelfTarget(target, "copied")
        validateBeforeReplacing(target, options)
        if (Files.exists(target) && !prepareExistingTarget(target, options)) {
            return SimpliDirectory(target)
        }
        FileTreeCopier.copyDirectory(path, target, options)
        return SimpliDirectory(target)
    }

    @Throws(IOException::class)
    fun copyTo(target: File, overwritePolicy: OverwritePolicy): SimpliDirectory = copyTo(Paths.get(target.path), overwritePolicy)

    @Throws(IOException::class)
    fun moveTo(target: Path, overwritePolicy: OverwritePolicy): SimpliDirectory {
        return moveTo(target, toDirectoryTransferOptions(overwritePolicy))
    }

    @Throws(IOException::class)
    fun moveTo(target: Path, options: DirectoryTransferOptions): SimpliDirectory {
        ensureExists()
        rejectSelfTarget(target, "moved")
        validateBeforeReplacing(target, options)
        if (Files.exists(target) && !prepareExistingTarget(target, options)) {
            return SimpliDirectory(target)
        }
        if (options.overwritePolicy == DirectoryOverwritePolicy.MERGE && Files.exists(target)) {
            FileTreeCopier.copyDirectory(path, target, options)
            FileTreeCleaner.deleteRecursively(path)
            return SimpliDirectory(target)
        }
        FileTreeCopier.validateDirectory(path, options)
        target.parent?.let { Files.createDirectories(it) }
        Files.move(path, target, StandardCopyOption.REPLACE_EXISTING)
        return SimpliDirectory(target)
    }

    @Throws(IOException::class)
    fun moveTo(target: File, overwritePolicy: OverwritePolicy): SimpliDirectory = moveTo(Paths.get(target.path), overwritePolicy)

    /**
     * Applies the overwrite policy to an existing target. Returns false when the transfer must be skipped.
     */
    private fun prepareExistingTarget(target: Path, options: DirectoryTransferOptions): Boolean {
        return when (options.overwritePolicy) {
            DirectoryOverwritePolicy.ERROR -> throw FileOperationException("Target already exists: $target")
            DirectoryOverwritePolicy.SKIP -> false
            DirectoryOverwritePolicy.REPLACE -> {
                FileTreeCleaner.deleteRecursively(target)
                true
            }
            DirectoryOverwritePolicy.MERGE -> true
        }
    }

    private fun ensureExists() {
        if (!exists) {
            throw FileOperationException("Directory does not exist: $path")
        }
    }

    private fun rejectSelfTarget(target: Path, action: String) {
        if (target.toAbsolutePath().normalize().startsWith(path.toAbsolutePath().normalize())) {
            throw FileOperationException("Directory cannot be $action into itself: $target")
        }
    }

    private fun validateBeforeReplacing(target: Path, options: DirectoryTransferOptions) {
        if (Files.exists(target) && options.overwritePolicy == DirectoryOverwritePolicy.REPLACE) {
            FileTreeCopier.validateDirectory(path, options)
        }
    }

    private fun toDirectoryTransferOptions(overwritePolicy: OverwritePolicy): DirectoryTransferOptions {
        val directoryPolicy = when (overwritePolicy) {
            OverwritePolicy.ERROR -> DirectoryOverwritePolicy.ERROR
            OverwritePolicy.REPLACE -> DirectoryOverwritePolicy.REPLACE
            OverwritePolicy.SKIP -> DirectoryOverwritePolicy.SKIP
        }
        return DirectoryTransferOptions(overwritePolicy = directoryPolicy)
    }
}
