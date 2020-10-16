package eu.kanade.tachiyomi.annotations

/**
 * Annotation used to mark a Source (i.e. individual sources) or a SourceFactory (i.e. all sources
 * within it) as NSFW. Used within the Tachiyomi app to prevent loading sources when parental
 * controls are enabled.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Nsfw
