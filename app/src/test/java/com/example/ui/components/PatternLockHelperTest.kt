package com.example.ui.components

import com.example.data.model.StoredCredential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternLockHelperTest {

    @Test
    fun hashPattern_isDeterministic() {
        val pattern = listOf(0, 1, 2, 4, 6)
        val hash1 = PatternLockHelper.hashPattern(pattern)
        val hash2 = PatternLockHelper.hashPattern(pattern)
        assertEquals(hash1, hash2)
        assertTrue(hash1.length == 64) // SHA-256 hex string length
    }

    @Test
    fun hashPattern_differsForDifferentPatterns() {
        val p1 = listOf(0, 1, 2, 4, 6)
        val p2 = listOf(0, 1, 2, 4, 7)
        val p3 = listOf(6, 4, 2, 1, 0) // reversed
        assertNotEquals(PatternLockHelper.hashPattern(p1), PatternLockHelper.hashPattern(p2))
        assertNotEquals(PatternLockHelper.hashPattern(p1), PatternLockHelper.hashPattern(p3))
    }

    @Test
    fun hashPattern_customSaltChangesHash() {
        val pattern = listOf(1, 4, 7, 8)
        val defaultHash = PatternLockHelper.hashPattern(pattern)
        val saltedHash = PatternLockHelper.hashPattern(pattern, salt = "custom_salt_123")
        assertNotEquals(defaultHash, saltedHash)
    }

    @Test
    fun storedCredential_holdsDomainAndUsername() {
        val cred = StoredCredential("google.com", "alwaysonagentlite@gmail.com")
        assertEquals("google.com", cred.domain)
        assertEquals("alwaysonagentlite@gmail.com", cred.username)
    }
}
