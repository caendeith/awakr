package io.github.caendeith.awakr.sdk

/** Static description of a plugin, resolved at build time. */
@AwakrApi
data class PluginManifest(
    val id: String,
    val name: StringRes,
    val version: String,
    val capabilities: Set<Capability>,
    val apiVersion: Int = AwakrApiVersion.CURRENT,
    val requiredPermissions: List<String> = emptyList(),
    val settings: List<SettingField> = emptyList(),
)

/** Base type of every plugin. */
@AwakrApi
interface AwakrPlugin {
    val manifest: PluginManifest
}
