package org.simplifiles.internal.archive.zip

import org.simplifiles.archive.ArchiveEntryInfo
import org.simplifiles.archive.ArchiveFormat
import org.simplifiles.archive.ArchiveInspection
import org.simplifiles.exception.CorruptedArchiveException
import org.simplifiles.internal.archive.ArchivePathAnalyzer
import java.io.IOException
import java.nio.file.Path
import java.util.zip.ZipFile

internal object ZipArchiveReader {

    fun inspect(path: Path): ArchiveInspection {
        try {
            return ZipFile(path.toFile()).use { zip ->
                val entries = zip.entries().asSequence().map { entry ->
                    ArchiveEntryInfo(
                        path = entry.name,
                        normalizedPath = ArchivePathAnalyzer.analyze(entry.name).normalizedPath,
                        isDirectory = entry.isDirectory,
                        compressedSize = entry.compressedSize,
                        uncompressedSize = entry.size,
                        compressionMethod = entry.method
                    )
                }.toList()
                ArchiveInspection(ArchiveFormat.ZIP, entries)
            }
        } catch (e: IOException) {
            throw CorruptedArchiveException(path, e)
        }
    }
}
