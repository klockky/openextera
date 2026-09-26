package org.simplifiles

import org.simplifiles.archive.ArchiveSource
import org.simplifiles.files.SimpliDirectory
import org.simplifiles.files.SimpliFile
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

object SimpliFiles {

    @JvmStatic
    fun archive(path: Path): ArchiveSource = ArchiveSource(path)

    @JvmStatic
    fun archive(file: File): ArchiveSource = archive(Paths.get(file.path))

    @JvmStatic
    fun file(path: Path): SimpliFile = SimpliFile(path)

    @JvmStatic
    fun file(file: File): SimpliFile = file(Paths.get(file.path))

    @JvmStatic
    fun directory(path: Path): SimpliDirectory = SimpliDirectory(path)

    @JvmStatic
    fun directory(file: File): SimpliDirectory = directory(Paths.get(file.path))
}
