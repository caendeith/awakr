package io.github.caendeith.awakr.sdk

/** Alarm sound provided by a sound pack plugin. */
@AwakrApi
data class SoundDescriptor(
    val id: String,
    val title: StringRes,
    val uri: String,
    val durationMs: Long,
)

@AwakrApi
interface SoundPackPlugin : AwakrPlugin {
    fun sounds(): List<SoundDescriptor>
}
