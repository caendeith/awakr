package io.github.caendeith.awakr.plugins.mission.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ColorBoardTest {

    @Test
    fun generatePlacesExactlyTheRequestedTargets() {
        for (targets in 1 until ColorRules.TILES) {
            val board = ColorRules.generate(targets, Random(targets))
            assertEquals(targets, board.total)
        }
    }

    @Test
    fun generateAlwaysLeavesDecoys() {
        val board = ColorRules.generate(ColorRules.TILES - 1, Random(7))
        assertEquals(ColorRules.TILES - 1, board.total)
        assertTrue(board.tiles.any { it != board.target })
    }

    @Test
    fun generateClampsJunkTargets() {
        assertTrue(ColorRules.generate(0, Random(1)).total >= 1)
        val huge = ColorRules.generate(500, Random(1))
        assertEquals(ColorRules.TILES - 1, huge.total)
    }

    @Test
    fun generateUsesOnlyPaletteColours() {
        repeat(20) { seed ->
            val board = ColorRules.generate(3, Random(seed))
            assertEquals(ColorRules.TILES, board.tiles.size)
            assertTrue(board.tiles.all { it in 0 until ColorRules.PALETTE })
            assertNotEquals(-1, board.target)
        }
    }

    @Test
    fun generateAsksForADifferentColourOnEveryBoard() {
        // Sanity check that the target is random rather than a constant, which would make the
        // mission trivial to memorise.
        val targets = (0 until 40).map { ColorRules.generate(2, Random(it)).target }.toSet()
        assertTrue(targets.size > 1)
    }

    @Test
    fun isTargetOnlyMatchesTheTargetColour() {
        val board = ColorBoard(tiles = listOf(1, 0, 1, 2), target = 0)
        assertTrue(board.isTarget(1))
        assertFalse(board.isTarget(0))
        assertFalse(board.isTarget(-1))
        assertFalse(board.isTarget(99))
        assertEquals(1, board.total)
    }
}
