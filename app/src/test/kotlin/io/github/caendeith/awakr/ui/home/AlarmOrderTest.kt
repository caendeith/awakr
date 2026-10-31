package io.github.caendeith.awakr.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmOrderTest {

    private val ids = listOf(1L, 2L, 3L, 4L)

    @Test
    fun movingDownShiftsTheOthersUp() {
        assertEquals(listOf(1L, 3L, 4L, 2L), ids.movedTo(from = 1, to = 3))
    }

    @Test
    fun movingUpShiftsTheOthersDown() {
        assertEquals(listOf(4L, 1L, 2L, 3L), ids.movedTo(from = 3, to = 0))
    }

    @Test
    fun movingToTheSameIndexChangesNothing() {
        assertEquals(ids, ids.movedTo(from = 2, to = 2))
    }

    @Test
    fun outOfRangeTargetsAreClamped() {
        // Clamping means the move stops at the edge; an edge-to-edge move is "stay put".
        assertEquals(ids, ids.movedTo(from = 3, to = 9))
        assertEquals(ids, ids.movedTo(from = 0, to = -5))
        assertEquals(listOf(2L, 1L, 3L, 4L), ids.movedTo(from = 1, to = -5))
        assertEquals(listOf(1L, 2L, 4L, 3L), ids.movedTo(from = 2, to = 9))
    }

    @Test
    fun reorderingNeverLosesOrDuplicatesAnItem() {
        for (from in ids.indices) {
            for (to in ids.indices) {
                val moved = ids.movedTo(from, to)
                assertEquals(ids.size, moved.size)
                assertEquals(ids.toSet(), moved.toSet())
                assertEquals(ids[from], moved[to])
            }
        }
    }

    @Test
    fun anEmptyListSurvives() {
        assertEquals(emptyList<Long>(), emptyList<Long>().movedTo(0, 1))
    }
}
