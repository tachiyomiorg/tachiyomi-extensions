package eu.kanade.tachiyomi.extension.ko.mangashowme

internal fun String.substringBetween(prefix: String, suffix: String): String = {
    this.substringAfter(prefix).substringBefore(suffix)
}()
