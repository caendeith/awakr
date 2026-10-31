package io.github.caendeith.awakr.sdk

/**
 * Declarative settings schema rendered by the host.
 * Values are stored as JSON in `plugin_state.settings_json`.
 */
@AwakrApi
sealed interface SettingField {
    val key: String
    val title: StringRes

    data class Toggle(
        override val key: String,
        override val title: StringRes,
        val default: Boolean = false,
    ) : SettingField

    data class Slider(
        override val key: String,
        override val title: StringRes,
        val min: Float,
        val max: Float,
        val step: Float = 1f,
        val default: Float = min,
        val unit: String? = null,
    ) : SettingField

    data class Select(
        override val key: String,
        override val title: StringRes,
        val options: List<Option>,
        val default: String = "",
    ) : SettingField {
        data class Option(val value: String, val label: StringRes)
    }

    data class TextInput(
        override val key: String,
        override val title: StringRes,
        val default: String = "",
        val maxLength: Int = 64,
        val pattern: String? = null,
    ) : SettingField

    data class Info(
        override val key: String,
        override val title: StringRes,
        val body: StringRes,
    ) : SettingField
}
