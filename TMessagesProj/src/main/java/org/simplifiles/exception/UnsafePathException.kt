package org.simplifiles.exception

class UnsafePathException(path: String, reason: String) : SimpliFilesException("Unsafe path '$path': $reason")
