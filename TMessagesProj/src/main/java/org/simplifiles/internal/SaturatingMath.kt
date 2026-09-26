package org.simplifiles.internal

internal fun Long.saturatingPlus(other: Long): Long {
    if (Long.MAX_VALUE - this < other) {
        return Long.MAX_VALUE
    }
    return this + other
}
