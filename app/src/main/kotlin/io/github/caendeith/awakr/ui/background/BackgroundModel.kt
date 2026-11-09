package io.github.caendeith.awakr.ui.background

import io.github.caendeith.awakr.R

/** A parsed ring-background id. */
sealed interface BackgroundRef {
    /** One of the procedural backgrounds shipped with the app. */
    data class Builtin(val key: String) : BackgroundRef

    /** A photo the user added; [path] is an absolute path inside the app's files dir. */
    data class Local(val path: String) : BackgroundRef
}

/**
 * Background id syntax: `builtin:<key>` for the procedural backgrounds, `file:<absolute path>`
 * for the user's own photos, blank for "follow the app setting". Parsing is pure so the picker
 * and the ring screen cannot disagree about what an id means.
 */
object BackgroundIds {
    const val BUILTIN_PREFIX = "builtin:"
    const val FILE_PREFIX = "file:"

    fun builtin(key: String): String = BUILTIN_PREFIX + key

    fun file(path: String): String = FILE_PREFIX + path

    /** Null for blank ids, unknown prefixes and ids without a value. */
    fun parse(id: String?): BackgroundRef? {
        val trimmed = id?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return when {
            trimmed.startsWith(BUILTIN_PREFIX) -> trimmed.removePrefix(BUILTIN_PREFIX)
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.let(BackgroundRef::Builtin)

            trimmed.startsWith(FILE_PREFIX) -> trimmed.removePrefix(FILE_PREFIX)
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.let(BackgroundRef::Local)

            else -> null
        }
    }

    fun builtinKey(id: String?): String? = (parse(id) as? BackgroundRef.Builtin)?.key

    fun filePath(id: String?): String? = (parse(id) as? BackgroundRef.Local)?.path

    /** Canonical id of a parsed reference. */
    fun idOf(ref: BackgroundRef): String = when (ref) {
        is BackgroundRef.Builtin -> builtin(ref.key)
        is BackgroundRef.Local -> file(ref.path)
    }
}

/** The two groups the built-in backgrounds are shown in. */
enum class BackgroundFamily { GRADIENTS, SCENES }

/**
 * One procedural background. [nameRes] points at a string in `strings_bg.xml`; the drawing code
 * lives in [ProceduralBackground] and is switched on [key].
 */
data class BuiltinBackground(
    val key: String,
    val nameRes: Int,
    val family: BackgroundFamily,
)

/** Built-in backgrounds, in display order. Nothing here needs a bitmap asset. */
object BackgroundCatalog {
    val gradients: List<BuiltinBackground> = listOf(
        BuiltinBackground("ink", R.string.bg_ink, BackgroundFamily.GRADIENTS),
        BuiltinBackground("amber", R.string.bg_amber, BackgroundFamily.GRADIENTS),
        BuiltinBackground("ember", R.string.bg_ember, BackgroundFamily.GRADIENTS),
        BuiltinBackground("aurora", R.string.bg_aurora, BackgroundFamily.GRADIENTS),
        BuiltinBackground("dawn", R.string.bg_dawn, BackgroundFamily.GRADIENTS),
        BuiltinBackground("void", R.string.bg_void, BackgroundFamily.GRADIENTS),
        BuiltinBackground("moss", R.string.bg_moss, BackgroundFamily.GRADIENTS),
        BuiltinBackground("rose", R.string.bg_rose, BackgroundFamily.GRADIENTS),
    )

    val scenes: List<BuiltinBackground> = listOf(
        BuiltinBackground("starry", R.string.bg_starry, BackgroundFamily.SCENES),
        BuiltinBackground("mountains", R.string.bg_mountains, BackgroundFamily.SCENES),
        BuiltinBackground("dunes", R.string.bg_dunes, BackgroundFamily.SCENES),
        BuiltinBackground("fog", R.string.bg_fog, BackgroundFamily.SCENES),
    )

    val all: List<BuiltinBackground> = gradients + scenes

    fun of(family: BackgroundFamily): List<BuiltinBackground> = all.filter { it.family == family }

    fun byKey(key: String?): BuiltinBackground? =
        key?.let { wanted -> all.firstOrNull { it.key == wanted } }

    fun idOf(background: BuiltinBackground): String = BackgroundIds.builtin(background.key)
}

/**
 * Decides which background a ringing alarm shows.
 *
 * The alarm wins over the app setting, and an id that no longer resolves (a deleted photo, a key
 * dropped from the catalog) falls through to the next candidate. Null means "keep the plain ring
 * screen", which is the shipped look.
 */
object BackgroundResolver {
    fun resolve(
        alarmId: String?,
        settingsId: String?,
        isUsable: (BackgroundRef) -> Boolean = { true },
    ): String? {
        for (candidate in listOf(alarmId, settingsId)) {
            val ref = BackgroundIds.parse(candidate) ?: continue
            if (isUsable(ref)) return BackgroundIds.idOf(ref)
        }
        return null
    }
}
