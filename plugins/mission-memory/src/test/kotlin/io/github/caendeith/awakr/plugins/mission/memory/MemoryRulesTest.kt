package io.github.caendeith.awakr.plugins.mission.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MemoryRulesTest {

    @Test
    fun generateUsesRequestedLength() {
        for (length in 1..MemoryRules.TILES) {
            assertEquals(length, MemoryRules.generate(length, Random(length)).size)
        }
    }

    @Test
    fun generateStaysOnTheGrid() {
        val sequence = MemoryRules.generate(7, Random(42))
        assertTrue(sequence.all { it in 0 until MemoryRules.TILES })
    }

    @Test
    fun generateNeverRepeatsATileImmediately() {
        repeat(50) { seed ->
            val sequence = MemoryRules.generate(7, Random(seed))
            sequence.zipWithNext().forEach { (first, second) ->
                assertTrue("seed=$seed sequence=$sequence", first != second)
            }
        }
    }

    @Test
    fun generateClampsJunkLength() {
        assertEquals(1, MemoryRules.generate(0, Random(1)).size)
        assertEquals(MemoryRules.TILES, MemoryRules.generate(500, Random(1)).size)
    }

    @Test
    fun matchesFollowsTheSequence() {
        val sequence = listOf(3, 15, 0)
        assertTrue(MemoryRules.matches(sequence, 0, 3))
        assertTrue(MemoryRules.matches(sequence, 1, 15))
        assertTrue(MemoryRules.matches(sequence, 2, 0))
        assertFalse(MemoryRules.matches(sequence, 0, 15))
    }

    @Test
    fun matchesIsFalsePastTheEnd() {
        val sequence = listOf(3, 15)
        assertFalse(MemoryRules.matches(sequence, 2, 3))
        assertFalse(MemoryRules.matches(sequence, -1, 3))
    }
}
