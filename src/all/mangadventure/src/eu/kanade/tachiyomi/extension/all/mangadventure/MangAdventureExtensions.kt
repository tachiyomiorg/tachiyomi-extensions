package eu.kanade.tachiyomi.extension.all.mangadventure

import android.net.Uri
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import java.text.DecimalFormat
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/** Returns the body of a response as a `String`. */
fun Response.asString(): String = body()!!.string()

/**
 * Formats the number according to [fmt].
 *
 * @param fmt A [DecimalFormat] string.
 * @return A string representation of the number.
 */
fun Number.format(fmt: String): String = DecimalFormat(fmt).format(this)

/**
 * Joins each value of a given [field] of the array using [sep].
 *
 * @param field The index of a [JSONArray].
 * When its type is [String], it is treated as the key of a [JSONObject].
 * @param sep The separator used to join the array.
 * @return The joined string, or `null` if the array is empty.
 */
fun JSONArray.joinField(field: Int, sep: String = ", ") =
    length().takeIf { it != 0 }?.run {
        (0 until this).joinToString(sep) {
            getJSONArray(it).getString(field)
        }
    }

/**
 * Joins each value of a given [field] of the array using [sep].
 *
 * @param field The key of a [JSONObject].
 * @param sep The separator used to join the array.
 * @return The joined string, or `null` if the array is empty.
 */
fun JSONArray.joinField(field: String, sep: String = ", ") =
    length().takeIf { it != 0 }?.run {
        (0 until this).joinToString(sep) {
            getJSONObject(it).getString(field)
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
    name = obj.optString("full_title", buildString {
        obj.optInt("volume").let { if (it != 0) append("Vol. $it, ") }
        append("Ch. ${chapter_number.format("#.#")}: ")
        append(obj.getString("title"))
    })
    if (obj.getBoolean("final")) name += " [END]"
}
