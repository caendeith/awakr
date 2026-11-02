package io.github.caendeith.awakr.plugins.mission.color

import kotlin.random.Random

/**
 * Pure board of the colour mission: every tile holds a palette index, one of them is the target
 * colour the player has to clear completely.
 */
internal class ColorBoard(val tiles: List<Int>, val target: Int) {

    /** How many tiles share the target colour — the number of correct taps needed. */
    val total: Int get() = tiles.count { it == target }

    /** Whether the tile at [index] has the target colour. Out-of-range indices never match. */
    fun isTarget(index: Int): Boolean = tiles.getOrNull(index) == target
}

/**
 * Pure generator for the colour mission. Kept free of Android and Compose APIs so it can be unit
 * tested.
 */
internal object ColorRules {

    /** Tiles per row and row count; the grid is square. */
    const val GRID = 4

    /** Number of tiles on the grid. */
    const val TILES = GRID * GRID

    /** Number of distinct tile colours, the target included. */
    const val PALETTE = 4

    /**
     * Builds a full board with exactly [targets] tiles of one random target colour; the rest get
     * one of the other palette colours.
     *
     * [targets] is clamped so that at least one target tile and at least one decoy always exist:
     * a board without targets would be solved by doing nothing.
     */
    fun generate(targets: Int, random: Random = Random.Default): ColorBoard {
        val target = random.nextInt(PALETTE)
        val count = targets.coerceIn(1, TILES - 1)
        val holes = (0 until TILES).shuffled(random).take(count).toHashSet()
        val tiles = List(TILES) { index ->
            if (index in holes) {
                target
            } else {
                // Pick any colour except the target one.
                val slot = random.nextInt(PALETTE - 1)
                if (slot >= target) slot + 1 else slot
            }
        }
        return ColorBoard(tiles, target)
    }
}
