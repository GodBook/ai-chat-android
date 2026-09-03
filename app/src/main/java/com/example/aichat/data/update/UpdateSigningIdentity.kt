package com.example.aichat.data.update

/** Certificate data needed to decide whether Android can safely replace this app. */
internal data class UpdateSigningIdentity(
    val hasMultipleSigners: Boolean,
    val currentSignerDigests: Set<String>,
    val signingHistoryDigests: Set<String>,
)

/**
 * A rotated single-signer update must descend from the currently installed
 * signer. Merely sharing an older certificate is insufficient because two
 * signing lineages can branch from the same historical key.
 */
internal fun hasCompatibleUpdateSignature(
    installed: UpdateSigningIdentity,
    archive: UpdateSigningIdentity,
): Boolean {
    if (installed.currentSignerDigests.isEmpty() || archive.currentSignerDigests.isEmpty()) {
        return false
    }
    if (installed.hasMultipleSigners || archive.hasMultipleSigners) {
        return installed.hasMultipleSigners &&
            archive.hasMultipleSigners &&
            installed.currentSignerDigests == archive.currentSignerDigests
    }
    return archive.signingHistoryDigests.containsAll(installed.currentSignerDigests)
}
