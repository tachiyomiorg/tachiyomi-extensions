package eu.kanade.tachiyomi.extension.all.nhentai

/**
 * Append Strings to StringBuilder with '+' operator
 */
operator fun StringBuilder.plusAssign(other: String) { append(other) }
