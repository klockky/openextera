package org.simplifiles.exception

import java.nio.file.Path

class CorruptedArchiveException(path: Path, cause: Throwable?) : SimpliFilesException("Archive is corrupted or unreadable: $path", cause)
