package io.github.caendeith.awakr.plugins.mission.math

import kotlin.random.Random

internal data class MathProblem(val text: String, val answer: Int)

/** Tiny arithmetic generator; pure so it can be unit tested. */
internal object MathPuzzles {

    fun generate(difficulty: String, count: Int, random: Random = Random.Default): List<MathProblem> {
        require(count >= 1) { "count must be positive" }
        return List(count) { one(difficulty, random) }
    }

    private fun one(difficulty: String, random: Random): MathProblem = when (difficulty) {
        "hard" -> {
            val a = random.nextInt(13, 40)
            val b = random.nextInt(3, 13)
            if (random.nextBoolean()) {
                MathProblem("${a} × ${b}", a * b)
            } else {
                MathProblem("${a * b} ÷ ${b}", a)
            }
        }

        "medium" -> when (random.nextInt(3)) {
            0 -> {
                val a = random.nextInt(10, 60)
                val b = random.nextInt(10, 40)
                MathProblem("${a} + ${b}", a + b)
            }

            1 -> {
                val a = random.nextInt(30, 90)
                val b = random.nextInt(10, 30)
                MathProblem("${a} − ${b}", a - b)
            }

            else -> {
                val a = random.nextInt(3, 10)
                val b = random.nextInt(3, 10)
                MathProblem("${a} × ${b}", a * b)
            }
        }

        else -> when (random.nextInt(2)) {
            0 -> {
                val a = random.nextInt(2, 11)
                val b = random.nextInt(2, 11)
                MathProblem("${a} + ${b}", a + b)
            }

            else -> {
                val a = random.nextInt(6, 20)
                val b = random.nextInt(1, 6)
                MathProblem("${a} − ${b}", a - b)
            }
        }
    }
}
