package org.simplifiles.exception

import java.nio.file.Path

class ArchiveWriteException(path: Path, reason: String) : SimpliFilesException("Cannot write archive '$path': $reason")
