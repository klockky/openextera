package org.simplifiles.exception

import org.simplifiles.archive.ValidationReport

class ArchiveValidationException(val report: ValidationReport) : SimpliFilesException(
    "Archive failed validation: " + (report.issues.firstOrNull()?.message ?: "unknown validation issue")
)
