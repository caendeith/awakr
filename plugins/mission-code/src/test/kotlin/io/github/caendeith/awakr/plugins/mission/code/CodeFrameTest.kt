package io.github.caendeith.awakr.plugins.mission.code

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CodeFrameTest {

    @Test
    fun stripsRowPadding() {
        val width = 3
        val height = 2
        val stride = 5
        val raw = ByteArray(stride * height) { (it + 1).toByte() }
        val stripped = CodeFrame.stripRowPadding(raw, stride, width, height)
        assertEquals(width * height, stripped.size)
        assertArrayEquals(byteArrayOf(1, 2, 3, 6, 7, 8), stripped)
    }

    @Test
    fun keepsThePlaneWhenThereIsNoPadding() {
        val raw = ByteArray(6) { it.toByte() }
        assertArrayEquals(raw, CodeFrame.stripRowPadding(raw, 3, 3, 2))
    }

    @Test
    fun keepsThePlaneWhenItIsShorterThanTheStride() {
        val raw = ByteArray(4) { it.toByte() }
        assertArrayEquals(raw, CodeFrame.stripRowPadding(raw, 9, 3, 2))
    }

    @Test
    fun rotateQuarterTurnSwapsDimensions() {
        val data = ByteArray(6) { it.toByte() }
        val (rotated, width, height) = CodeFrame.rotate(data, 3, 2, 90)
        assertEquals(2, width)
        assertEquals(3, height)
        assertEquals(6, rotated.size)
        // (0,0) moves to the top-right corner of the rotated plane: x*height + (height-1-y).
        assertEquals(data[0], rotated[0 * 2 + (2 - 1 - 0)])
    }

    @Test
    fun rotateFullTurnIsIdentity() {
        val data = ByteArray(6) { it.toByte() }
        val (rotated, width, height) = CodeFrame.rotate(data, 3, 2, 180)
        assertEquals(3, width)
        assertEquals(2, height)
        assertEquals(data[0], rotated[5])
        assertEquals(data[5], rotated[0])
    }

    @Test
    fun rotatePastAFullTurnWraps() {
        val raw = ByteArray(6) { it.toByte() }
        val (rotated, width, height) = CodeFrame.rotate(raw, 3, 2, 450)
        assertEquals(2, width)
        assertEquals(3, height)
        val (back, width2, height2) = CodeFrame.rotate(rotated, width, height, -90)
        assertEquals(3, width2)
        assertEquals(2, height2)
        assertArrayEquals(raw, back)
    }
}
