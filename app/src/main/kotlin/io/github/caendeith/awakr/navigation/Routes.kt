package io.github.caendeith.awakr.navigation

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val PLUGINS = "plugins"
    const val ABOUT = "about"
    const val ONBOARDING = "onboarding"

    const val EDITOR_ARG_ID = "alarmId"
    const val EDITOR = "editor/{$EDITOR_ARG_ID}"
    fun editor(alarmId: Long = 0L): String = "editor/$alarmId"

    const val RING_ARG_ID = "alarmId"
    const val RING = "ring/{$RING_ARG_ID}"
    fun ring(alarmId: Long): String = "ring/$alarmId"
}
