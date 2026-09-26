package org.simplifiles.internal.io

import org.simplifiles.files.SymlinkPolicy
import java.nio.file.Files
import java.nio.file.Path

internal object SymlinkSupport {

    fun decide(path: Path, policy: SymlinkPolicy): SymlinkDecision {
        if (!Files.isSymbolicLink(path)) {
            return SymlinkDecision.INCLUDE
        }
        return when (policy) {
            SymlinkPolicy.FOLLOW -> SymlinkDecision.INCLUDE
            SymlinkPolicy.SKIP -> SymlinkDecision.SKIP
            SymlinkPolicy.ERROR -> SymlinkDecision.FAIL
        }
    }
}
