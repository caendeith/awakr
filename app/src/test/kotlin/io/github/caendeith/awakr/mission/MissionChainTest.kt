package io.github.caendeith.awakr.mission

import io.github.caendeith.awakr.core.alarm.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionChainTest {

    @Test
    fun anAlarmWithoutAChainPlaysItsSingleMission() {
        val alarm = Alarm(hour = 7, minute = 0, missionPluginId = "math", missionConfig = """{"count":3}""")
        val steps = MissionChain.stepsOf(alarm)
        assertEquals(1, steps.size)
        assertEquals("math", steps[0].kind)
        assertEquals("""{"count":3}""", steps[0].config)
    }

    @Test
    fun aChainRoundTrips() {
        val steps = listOf(
            MissionStep("math", """{"difficulty":"hard"}"""),
            MissionStep("shake", """{"seconds":30}"""),
        )
        assertEquals(steps, MissionChain.parse(MissionChain.encode(steps)))
    }

    @Test
    fun malformedJsonYieldsAnEmptyChainInsteadOfThrowing() {
        assertEquals(emptyList<MissionStep>(), MissionChain.parse("not json"))
        assertEquals(emptyList<MissionStep>(), MissionChain.parse("""[{"config":{}}]"""))
        assertEquals(emptyList<MissionStep>(), MissionChain.parse(""))
    }

    @Test
    fun brokenStepsAreDroppedButTheRestSurvives() {
        val raw = """[{"kind":"math","config":{}},{"kind":""},{"nope":1},{"kind":"shake","config":{}}]"""
        assertEquals(listOf("math", "shake"), MissionChain.parse(raw).map { it.kind })
    }

    @Test
    fun theChainNeverExceedsTheStepLimit() {
        var steps = emptyList<MissionStep>()
        repeat(5) { steps = MissionChain.added(steps, "math") }
        assertEquals(MissionChain.MAX_STEPS, steps.size)
        assertEquals(MissionChain.MAX_STEPS, MissionChain.parse(MissionChain.encode(steps)).size)
    }

    @Test
    fun removeAndMoveKeepTheOrderSanity() {
        val steps = listOf(MissionStep("a"), MissionStep("b"), MissionStep("c"))
        assertEquals(listOf("a", "c"), MissionChain.removed(steps, 1).map { it.kind })
        assertEquals(listOf("b", "a", "c"), MissionChain.moved(steps, 0, 1).map { it.kind })
        assertEquals(listOf("a", "c", "b"), MissionChain.moved(steps, 2, -1).map { it.kind })
        assertEquals(steps, MissionChain.moved(steps, 0, -1))
        assertEquals(steps, MissionChain.moved(steps, 2, 1))
        assertEquals(steps, MissionChain.removed(steps, 9))
    }

    @Test
    fun configIsReplacedOnlyForAnExistingStep() {
        val steps = listOf(MissionStep("a"), MissionStep("b"))
        val updated = MissionChain.withConfig(steps, 1, """{"x":1}""")
        assertEquals("""{"x":1}""", updated[1].config)
        assertTrue(updated[0].config == "{}")
        assertEquals(steps, MissionChain.withConfig(steps, 5, """{"x":1}"""))
    }
}
