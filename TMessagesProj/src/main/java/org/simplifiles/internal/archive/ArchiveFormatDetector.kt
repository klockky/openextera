package org.simplifiles.internal.archive

import org.simplifiles.archive.ArchiveFormat
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

internal object ArchiveFormatDetector {

    fun detect(path: Path): ArchiveFormat? {
        val signature = Files.newInputStream(path).use { readSignature(it) } ?: return null
        val b0 = signature[0].toInt() and 0xFF
        val b1 = signature[1].toInt() and 0xFF
        val b2 = signature[2].toInt() and 0xFF
        val b3 = signature[3].toInt() and 0xFF
        // "PK" followed by a local file header, an empty-archive end record or a spanning marker
        if (b0 == 'P'.code && b1 == 'K'.code && ((b2 == 3 && b3 == 4) || (b2 == 5 && b3 == 6) || (b2 == 7 && b3 == 8))) {
            return ArchiveFormat.ZIP
        }
        return null
    }

    internal fun readSignature(input: InputStream): ByteArray? {
        val signature = ByteArray(4)
        var offset = 0
        while (offset < signature.size) {
            val read = input.read(signature, offset, signature.size - offset)
            if (read < 0) {
                return null
            }
            offset += read
        }
        return signature
    }
}
