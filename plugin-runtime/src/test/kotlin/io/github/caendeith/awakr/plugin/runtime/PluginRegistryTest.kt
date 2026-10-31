package io.github.caendeith.awakr.plugin.runtime

import androidx.compose.runtime.Composable
import io.github.caendeith.awakr.sdk.AwakrApiVersion
import io.github.caendeith.awakr.sdk.Capability
import io.github.caendeith.awakr.sdk.MissionPlugin
import io.github.caendeith.awakr.sdk.PluginManifest
import io.github.caendeith.awakr.sdk.StringRes
import io.github.caendeith.awakr.sdk.ThemePlugin
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRegistryTest {

    private class FakeMission(
        override val kind: String,
        override val manifest: PluginManifest,
    ) : MissionPlugin {
        @Composable
        override fun Editor(config: JsonObject, onChange: (JsonObject) -> Unit) = Unit

        @Composable
        override fun Mission(config: JsonObject, onSolved: () -> Unit) = Unit
    }

    private fun manifest(
        id: String,
        capability: Capability = Capability.MISSION,
        apiVersion: Int = AwakrApiVersion.CURRENT,
        nameRes: Int = 1,
    ) = PluginManifest(
        id = id,
        name = StringRes(nameRes, id),
        version = "1.0",
        capabilities = setOf(capability),
        apiVersion = apiVersion,
    )

    @Test
    fun buildsFromListAndFindsByKind() {
        val math = FakeMission("math", manifest("mission-math"))
        val registry = PluginRegistry.build(listOf(math))
        assertEquals(1, registry.missions.size)
        assertNotNull(registry.mission("math"))
        assertNull(registry.mission("typing"))
        assertTrue(registry.issues.isEmpty())
    }

    @Test
    fun rejectsIncompatibleApiVersion() {
        val old = FakeMission("old", manifest("mission-old", apiVersion = 999))
        val registry = PluginRegistry.build(listOf(old))
        assertTrue(registry.missions.isEmpty())
        assertEquals(1, registry.issues.size)
        assertTrue(registry.issues.first().reason.contains("API"))
    }

    @Test
    fun rejectsDuplicateIds() {
        val first = FakeMission("math", manifest("mission-math"))
        val second = FakeMission("typing", manifest("mission-math"))
        val registry = PluginRegistry.build(listOf(first, second))
        assertEquals(1, registry.missions.size)
        assertEquals(1, registry.issues.size)
        assertTrue(registry.issues.first().reason.contains("Duplicate"))
    }

    @Test
    fun splitsByCapability() {
        val mission = FakeMission("math", manifest("mission-math"))
        val theme = object : ThemePlugin {
            override val isDark = true
            override val manifest = manifest("theme-default", Capability.THEME, nameRes = 2)
        }
        val registry = PluginRegistry.build(listOf(mission, theme))
        assertEquals(1, registry.missions.size)
        assertEquals(1, registry.themes.size)
        assertEquals(2, registry.all.size)
    }

    @Test
    fun defaultValidatePassesConfigThrough() {
        val math = FakeMission("math", manifest("mission-math"))
        val config = JsonObject(emptyMap())
        assertEquals(config, math.validate(config))
    }
}
