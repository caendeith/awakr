package io.github.caendeith.awakr.sdk

/** Extra colour scheme provided by a theme plugin. */
@AwakrApi
interface ThemePlugin : AwakrPlugin {
    /** True for dark-first themes. */
    val isDark: Boolean
}
