package com.example.aichat.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSigningIdentityTest {
    @Test
    fun `accepts the same single signer`() {
        assertTrue(hasCompatibleUpdateSignature(single("A"), single("A")))
    }

    @Test
    fun `accepts a forward signing key rotation`() {
        val installed = single(current = "A", history = setOf("A"))
        val archive = single(current = "B", history = setOf("A", "B"))

        assertTrue(hasCompatibleUpdateSignature(installed, archive))
    }

    @Test
    fun `rejects signing histories that branched from an old key`() {
        val installed = single(current = "B", history = setOf("A", "B"))
        val archive = single(current = "C", history = setOf("A", "C"))

        assertFalse(hasCompatibleUpdateSignature(installed, archive))
    }

    @Test
    fun `rejects an update signed only with a predecessor key`() {
        val installed = single(current = "B", history = setOf("A", "B"))
        val archive = single(current = "A", history = setOf("A"))

        assertFalse(hasCompatibleUpdateSignature(installed, archive))
    }

    @Test
    fun `multiple signers must match exactly`() {
        val installed = multiple("A", "B")

        assertTrue(hasCompatibleUpdateSignature(installed, multiple("A", "B")))
        assertFalse(hasCompatibleUpdateSignature(installed, multiple("A", "C")))
        assertFalse(hasCompatibleUpdateSignature(installed, single("A")))
    }

    private fun single(
        current: String,
        history: Set<String> = setOf(current),
    ) = UpdateSigningIdentity(
        hasMultipleSigners = false,
        currentSignerDigests = setOf(current),
        signingHistoryDigests = history,
    )

    private fun multiple(vararg current: String) = UpdateSigningIdentity(
        hasMultipleSigners = true,
        currentSignerDigests = current.toSet(),
        signingHistoryDigests = current.toSet(),
    )
}
