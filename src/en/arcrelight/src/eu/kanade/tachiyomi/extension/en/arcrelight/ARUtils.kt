package eu.kanade.tachiyomi.extension.en.arcrelight

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The HTTP date format specified in
 * [RFC 1123](https://tools.ietf.org/html/rfc1123#page-55).
 */
private const val HTTP_DATE = "EEE, dd MMM yyyy HH:mm:ss zzz"

/**
 * Converts a date in the [HTTP_DATE] format to a Unix timestamp.
 *
 * @param date The date to convert.
 * @return The timestamp of the date.
 */
fun httpDateToTimestamp(date: String) =
        SimpleDateFormat(HTTP_DATE, Locale.US).parse(date).time

/**
 * Joins each value of a given [field] of the array using [sep].
 *
 * @param field
 * When its type is [Int], it is treated as the index of a [JSONArray].
 * When its type is [String], it is treated as the key of a [JSONObject].
 * @param sep The separator used to join the array.
 * @return The joined string, or null if the array is empty.
 * @throws IllegalArgumentException when [field] is of an invalid type.
 */
fun JSONArray.joinField(field: Any, sep: String = ", "): String? {
    if (!(field is Int || field is String))
        throw IllegalArgumentException("field must be a String or Int")
    if (this.length() == 0) return null
    val list = mutableListOf<String>()
    for (i in 0 until this.length()) {
        when (field) {
            is Int -> list.add(this.getJSONArray(i).getString(field))
            is String -> list.add(this.getJSONObject(i).getString(field))
        }
    }
    return list.joinToString(sep)
}

/**
 * Creates a [SManga] by parsing a [JSONObject].
 *
 * @param obj The object containing the manga info.
 */
fun SManga.fromJSON(obj: JSONObject) {
    url = obj.getString("url")
    title = obj.getString("title")
    description = obj.getString("description")
    thumbnail_url = obj.getString("cover")
    author = obj.getJSONArray("authors")?.joinField(0)
    artist = obj.getJSONArray("artists")?.joinField(0)
    genre = obj.getJSONArray("categories")?.joinField("name")
    status = when (obj.getBoolean("completed")) {
        true -> SManga.COMPLETED
        false -> SManga.ONGOING
    }
}

/**
 * Creates a [SChapter] by parsing a [JSONObject].
 *
 * @param obj The object containing the chapter info.
 */
fun SChapter.fromJSON(obj: JSONObject) {
    url = obj.getString("url")
    chapter_number = obj.optString("chapter", "0").toFloat()
    date_upload = httpDateToTimestamp(obj.getString("date"))
    scanlator = obj.getJSONArray("groups")?.joinField("name", " & ")
    name = buildString {
        obj.optInt("volume").let { if (it != 0) append("Vol.$it ") }
        append("Ch.${DecimalFormat("#.#").format(chapter_number)} - ")
        append(obj.getString("title"))
        if (obj.getBoolean("final")) append(" [END]")
    }
}

