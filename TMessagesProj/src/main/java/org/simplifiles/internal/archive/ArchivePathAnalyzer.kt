package org.simplifiles.internal.archive

internal object ArchivePathAnalyzer {

    /**
     * Normalizes an archive entry name lexically: both '/' and '\' separate segments,
     * empty and "." segments are dropped, any ".." segment marks the path as traversing.
     */
    fun analyze(path: String): ArchivePathAnalysis {
        val isAbsolute = path.startsWith("/") || path.startsWith("\\") || isWindowsDriveAbsolute(path)
        if (path.isEmpty()) {
            return ArchivePathAnalysis(null, isEmpty = true, isAbsolute = isAbsolute, containsParentTraversal = false)
        }
        val normalized = StringBuilder(path.length)
        var containsParentTraversal = false
        var hasSegments = false
        var segmentStart = 0
        for (i in 0..path.length) {
            if (i < path.length && path[i] != '/' && path[i] != '\\') {
                continue
            }
            val segmentLength = i - segmentStart
            if (segmentLength > 0 && !isCurrentDirectorySegment(path, segmentStart, segmentLength)) {
                if (isParentDirectorySegment(path, segmentStart, segmentLength)) {
                    containsParentTraversal = true
                } else if (!containsParentTraversal) {
                    if (normalized.isNotEmpty()) {
                        normalized.append('/')
                    }
                    normalized.append(path, segmentStart, i)
                }
                hasSegments = true
            }
            segmentStart = i + 1
        }
        val normalizedPath = if (containsParentTraversal || normalized.isEmpty()) null else normalized.toString()
        return ArchivePathAnalysis(
            normalizedPath = normalizedPath,
            isEmpty = !containsParentTraversal && !hasSegments,
            isAbsolute = isAbsolute,
            containsParentTraversal = containsParentTraversal
        )
    }

    private fun isCurrentDirectorySegment(path: String, start: Int, length: Int): Boolean {
        return length == 1 && path[start] == '.'
    }

    private fun isParentDirectorySegment(path: String, start: Int, length: Int): Boolean {
        return length == 2 && path[start] == '.' && path[start + 1] == '.'
    }

    private fun isWindowsDriveAbsolute(path: String): Boolean {
        return path.length >= 2 && path[1] == ':' && path[0].isLetter() &&
            (path.length == 2 || path[2] == '/' || path[2] == '\\')
    }
}
