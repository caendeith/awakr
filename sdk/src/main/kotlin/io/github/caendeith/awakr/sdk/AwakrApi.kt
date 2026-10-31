package io.github.caendeith.awakr.sdk

/** Marks the public plugin SDK surface. Breaking changes require a new [AwakrApiVersion]. */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class AwakrApi(val since: Int = 1)

/** Supported plugin SDK versions. */
object AwakrApiVersion {
    const val CURRENT: Int = 1
}
