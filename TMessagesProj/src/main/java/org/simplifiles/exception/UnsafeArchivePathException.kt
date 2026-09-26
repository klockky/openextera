package org.simplifiles.exception

class UnsafeArchivePathException(path: String, reason: String) : SimpliFilesException("Unsafe archive path '$path': $reason")
