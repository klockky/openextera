package org.simplifiles.archive

fun interface CancellationToken {

    fun isCancellationRequested(): Boolean

    companion object {
        fun none(): CancellationToken = CancellationToken { false }
    }
}
