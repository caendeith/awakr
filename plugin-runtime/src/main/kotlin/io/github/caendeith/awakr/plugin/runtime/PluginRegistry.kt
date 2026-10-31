package io.github.caendeith.awakr.plugin.runtime

import io.github.caendeith.awakr.sdk.AwakrApiVersion
import io.github.caendeith.awakr.sdk.AwakrPlugin
import io.github.caendeith.awakr.sdk.Capability
import io.github.caendeith.awakr.sdk.LocalePackPlugin
import io.github.caendeith.awakr.sdk.MissionPlugin
import io.github.caendeith.awakr.sdk.SoundPackPlugin
import io.github.caendeith.awakr.sdk.ThemePlugin

/** A plugin that could not be activated, surfaced in Settings → Plugins instead of crashing. */
data class PluginIssue(val pluginId: String, val reason: String)

/**
 * Resolved set of build-time plugins.
 *
 * Plugins are registered explicitly by the host application: no reflection, no dynamic code
 * loading, R8-safe. [build] validates API versions and duplicate ids.
 */
class PluginRegistry private constructor(
    val missions: List<MissionPlugin>,
    val soundPacks: List<SoundPackPlugin>,
    val themes: List<ThemePlugin>,
    val locales: List<LocalePackPlugin>,
    val issues: List<PluginIssue>,
) {
    val all: List<AwakrPlugin> = missions + soundPacks + themes + locales

    fun mission(kind: String): MissionPlugin? = missions.firstOrNull { it.kind == kind }

    companion object {
        /**
         * @param declaredPermissions permissions the host app declares in its manifest. When
         * provided, a plugin asking for anything outside that set is reported as an issue: the
         * runtime request would be rejected by the platform and the mission could never start.
         */
        fun build(
            plugins: List<AwakrPlugin>,
            declaredPermissions: Set<String>? = null,
        ): PluginRegistry {
            val issues = mutableListOf<PluginIssue>()
            val seenIds = mutableSetOf<String>()
            val accepted = plugins.filter { plugin ->
                val manifest = plugin.manifest
                when {
                    manifest.apiVersion != AwakrApiVersion.CURRENT -> {
                        issues += PluginIssue(manifest.id, "Incompatible plugin API ${manifest.apiVersion}")
                        false
                    }
                    !seenIds.add(manifest.id) -> {
                        issues += PluginIssue(manifest.id, "Duplicate plugin id")
                        false
                    }
                    else -> true
                }
            }
            if (declaredPermissions != null) {
                accepted.forEach { plugin ->
                    plugin.manifest.requiredPermissions
                        .filterNot { it in declaredPermissions }
                        .forEach { missing ->
                            issues += PluginIssue(
                                plugin.manifest.id,
                                "Missing manifest permission $missing",
                            )
                        }
                }
            }
            return PluginRegistry(
                missions = accepted.filterIsInstance<MissionPlugin>()
                    .filter { Capability.MISSION in it.manifest.capabilities }
                    .sortedBy { it.manifest.name.resId },
                soundPacks = accepted.filterIsInstance<SoundPackPlugin>()
                    .filter { Capability.SOUND_PACK in it.manifest.capabilities },
                themes = accepted.filterIsInstance<ThemePlugin>()
                    .filter { Capability.THEME in it.manifest.capabilities },
                locales = accepted.filterIsInstance<LocalePackPlugin>()
                    .filter { Capability.LOCALE in it.manifest.capabilities },
                issues = issues.toList(),
            )
        }
    }
}
