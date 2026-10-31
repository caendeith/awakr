package io.github.caendeith.awakr.plugins.mission.math

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathPuzzlesTest {

    @Test
    fun generatesExactlyTheRequestedNumberOfProblems() {
        for (count in 1..5) {
            assertEquals(count, MathPuzzles.generate("easy", count, Random(count)).size)
        }
    }

    @Test
    fun rejectsNonPositiveCount() {
        val error = runCatching { MathPuzzles.generate("easy", 0, Random(1)) }.exceptionOrNull()
        assertTrue("expected IllegalArgumentException, got $error", error is IllegalArgumentException)
    }

    private fun eval(text: String): Int {
        val (left, right) = text.split(" + ", " − ", " × ", " ÷ ")
        val op = text.first { it in "+−×÷" }
        return when (op) {
            '+' -> left.trim().toInt() + right.trim().toInt()
            '−' -> left.trim().toInt() - right.trim().toInt()
            '×' -> left.trim().toInt() * right.trim().toInt()
            else -> left.trim().toInt() / right.trim().toInt()
        }
    }

    @Test
    fun answersAlwaysMatchThePrintedExpression() {
        val random = Random(42)
        for (difficulty in listOf("easy", "medium", "hard")) {
            repeat(200) {
                for (problem in MathPuzzles.generate(difficulty, 3, random)) {
                    assertEquals("${difficulty}: ${problem.text}", eval(problem.text), problem.answer)
                }
            }
        }
    }

    @Test
    fun hardDivisionAlwaysHasNoRemainder() {
        val random = Random(7)
        repeat(300) {
            for (problem in MathPuzzles.generate("hard", 3, random)) {
                if (problem.text.contains("÷")) {
                    val (left, right) = problem.text.split(" ÷ ")
                    assertEquals(0, left.trim().toInt() % right.trim().toInt())
                }
            }
        }
    }

    @Test
    fun easyAnswersStayInTheHeadArithmeticRange() {
        val random = Random(11)
        repeat(200) {
            for (problem in MathPuzzles.generate("easy", 5, random)) {
                assertTrue(problem.answer in 0..99)
            }
        }
    }

    @Test
    fun sameSeedProducesTheSameSequence() {
        val first = MathPuzzles.generate("medium", 5, Random(123))
        val second = MathPuzzles.generate("medium", 5, Random(123))
        assertEquals(first, second)
    }
}
