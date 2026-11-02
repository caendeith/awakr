package io.github.caendeith.awakr.plugins.mission.memory

import kotlin.random.Random

/**
 * Pure rules of the memory mission: a sequence of tile indices on a 4x4 grid.
 *
 * Kept free of Android and Compose APIs so it can be unit tested.
 */
internal object MemoryRules {

    /** Tiles per row and row count; the grid is square. */
    const val GRID = 4

    /** Number of tiles on the grid. */
    const val TILES = GRID * GRID

    /**
     * Builds a sequence of [length] tile indices in `0 until TILES`.
     *
     * Two equal tiles in a row are skipped: the player has no way to tell whether the tile
     * flashed once or twice, so a repeat would be an unfair trap.
     */
    fun generate(length: Int, random: Random = Random.Default): List<Int> {
        val size = length.coerceIn(1, TILES)
        val sequence = ArrayList<Int>(size)
        var previous = -1
        repeat(size) {
            var tile = random.nextInt(TILES)
            while (tile == previous) tile = random.nextInt(TILES)
            sequence += tile
            previous = tile
        }
        return sequence
    }

    /** Whether tapping [tile] continues [sequence] at [progress]. */
    fun matches(sequence: List<Int>, progress: Int, tile: Int): Boolean =
        progress in sequence.indices && sequence[progress] == tile
}
