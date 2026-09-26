package org.simplifiles.files

class DirectoryTransferOptions @JvmOverloads constructor(
    val overwritePolicy: DirectoryOverwritePolicy = DirectoryOverwritePolicy.ERROR,
    val maxFiles: Long = Long.MAX_VALUE,
    val maxBytes: Long = Long.MAX_VALUE,
    val symlinkPolicy: SymlinkPolicy = SymlinkPolicy.SKIP
) {

    init {
        require(maxFiles >= 0) { "maxFiles must not be negative." }
        require(maxBytes >= 0) { "maxBytes must not be negative." }
    }

    override fun toString(): String {
        return "DirectoryTransferOptions(overwritePolicy=$overwritePolicy, maxFiles=$maxFiles, maxBytes=$maxBytes, symlinkPolicy=$symlinkPolicy)"
    }

    companion object {
        fun defaults(): DirectoryTransferOptions = DirectoryTransferOptions()
    }
}
