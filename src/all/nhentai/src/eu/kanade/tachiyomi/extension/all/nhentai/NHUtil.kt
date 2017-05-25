package eu.kanade.tachiyomi.extension.all.nhentai

/**
 * Append Strings to StringBuilder with '+' operator
 */
operator fun StringBuilder.plusAssign(other: String) {
    append(other)
}

/**
 * Return null if String is blank, otherwise returns the original String
 * @returns null if the String is blank, otherwise returns the original String
 */
fun String?.nullIfBlank(): String? = if (isNullOrBlank())
    null
else
    this
