package io.github.caendeith.awakr.sdk

/** Additional language shipped as a plugin. */
@AwakrApi
interface LocalePackPlugin : AwakrPlugin {
    /** BCP-47 tag, e.g. "ru". */
    val languageTag: String
}
