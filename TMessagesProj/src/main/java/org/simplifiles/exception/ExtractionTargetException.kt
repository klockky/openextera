package org.simplifiles.exception

import java.nio.file.Path

class ExtractionTargetException(path: Path, reason: String) : SimpliFilesException("Invalid extraction target '$path': $reason")
