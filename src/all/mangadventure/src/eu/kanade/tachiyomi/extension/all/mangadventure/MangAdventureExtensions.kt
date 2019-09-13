package eu.kanade.tachiyomi.extension.all.mangadventure

import android.net.Uri
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat

/** Returns the body of a response as a `String`. */
fun Response.asString(): String = body()!!.string()

/**
 * Joins each value of a given [field] of the array using [sep].
 *
 * @param field
 * When its type is [Int], it is treated as the index of a [JSONArray].
 * When its type is [String], it is treated as the key of a [JSONObject].
 * @param sep The separator used to join the array.
 * @param T Must be either [Int] or [String].
 * @return The joined string, or null if the array is empty.
 * @throws IllegalArgumentException when [field] is of an invalid type.
 */
fun <T> JSONArray.joinField(field: T, sep: String = ", "): String? {
    require(field is Int || field is String) {
        "field must be a String or Int"
    }
    return length().takeIf { it != 0 }?.let { len ->
        (0 until len).joinToString(sep) {
            when (field) {
                is Int -> getJSONArray(it).getString(field)
                is String -> getJSONObject(it).getString(field)
                else -> "" // this is here to appease the compiler
            }
        }
    }
}

/** The slug of a manga. */
val SManga.slug: String
    get() = Uri.parse(url).lastPathSegment!!

/**
 * Creates a [SManga] by parsing a [JSONObject].
 *
 * @param obj The object containing the manga info.
 */
fun SManga.fromJSON(obj: JSONObject) = apply {
    url = obj.getString("url")
    title = obj.getString("title")
    description = obj.getString("description")
    thumbnail_url = obj.getString("cover")
    author = obj.getJSONArray("authors")?.joinField(0)
    artist = obj.getJSONArray("artists")?.joinField(0)
    genre = obj.getJSONArray("categories")?.joinField("name")
    status = if (obj.getBoolean("completed"))
        SManga.COMPLETED else SManga.ONGOING
}

/** The unique path of a chapter. */
val SChapter.path: String
    get() = url.substringAfter("/reader/")

/**
 * Creates a [SChapter] by parsing a [JSONObject].
 *
 * @param obj The object containing the chapter info.
 */
fun SChapter.fromJSON(obj: JSONObject) = apply {
    url = obj.getString("url")
    chapter_number = obj.optString("chapter", "0").toFloat()
    date_upload = MangAdventure.httpDateToTimestamp(obj.getString("date"))
    scanlator = obj.getJSONArray("groups")?.joinField("name", " & ")
    name = buildString {
        obj.optInt("volume").let { if (it != 0) append("Vol.$it ") }
        append("Ch.${DecimalFormat("#.#").format(chapter_number)} - ")
        append(obj.getString("title"))
        if (obj.getBoolean("final")) append(" [END]")
    }
}
