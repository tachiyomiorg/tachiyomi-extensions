package eu.kanade.tachiyomi.extension.en.arcrelight

import eu.kanade.tachiyomi.source.model.Filter

/** Array containing the possible statuses of a manga */
private val STATUSES = arrayOf("Any", "Completed", "Ongoing")

/** List containing the possible categories of a manga */
private val CATEGORIES = listOf(
    Category("4-Koma"),
    Category("Chaos;Head"),
    Category("Collection"),
    Category("Comedy"),
    Category("Drama"),
    Category("Jubilee),
    Category("Mystery"),
    Category("Psychological"),
    Category("Robotics;Notes"),
    Category("Romance"),
    Category("Sci-Fi"),
    Category("Seinen"),
    Category("Shounen"),
    Category("Steins;Gate"),
    Category("Supernatural"),
    Category("Tragedy")
)

/**
 * Filter representing the status of a manga.
 *
 * @constructor Creates a [Filter.Select] object with [STATUSES].
 */
class Status : Filter.Select<String>("Status", STATUSES) {
    /** Returns the [state] as a string. */
    fun string() = values[state].toLowerCase()
}

/**
 * Filter representing a manga category.
 *
 * @property name The display name of the category.
 * @constructor Creates a [Filter.TriState] object using [name].
 */
class Category(name: String) : Filter.TriState(name) {
    /** Returns the [state] as a string, or null if [isIgnored]. */
    fun optString() = when (state) {
        STATE_INCLUDE -> name.toLowerCase()
        STATE_EXCLUDE -> "-" + name.toLowerCase()
        else -> null
    }
}

/**
 * Filter representing the [categories][Category] of a manga.
 *
 * @constructor Creates a [Filter.Group] object with [CATEGORIES].
 */
class CategoryList : Filter.Group<Category>("Categories", CATEGORIES)

/**
 * Filter representing the name of an author or artist.
 *
 * @constructor Creates a [Filter.Text] object.
 */
class Person : Filter.Text("Author/Artist")

