package eu.kanade.tachiyomi.multisrc.pizzareader

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat
// import java.time.OffsetDateTime
import java.text.SimpleDateFormat
import java.util.Locale

/** Returns the body of a response as a `String`. */
fun Response.asString(): String = body!!.string()

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
        (0 until this).mapNotNull {
            val obj = get(it)
            if (obj != null && obj.toString() != "null") getJSONArray(it).getString(field)
            else null
        }.joinToString(sep)
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
        (0 until this).mapNotNull {
            val obj = get(it)
            if (obj != null && obj.toString() != "null") getJSONObject(it).getString(field)
            else null
        }.joinToString(sep)
    }

/**
 * Creates a [SManga] by parsing a [JSONObject].
 *
 * @param obj The object containing the manga info.
 */
fun SManga.fromJSON(obj: JSONObject) = apply {
    url = obj.getString("url")
    title = obj.getString("title")
    description = obj.getString("description")
    thumbnail_url = obj.getString("thumbnail")
    author = obj.getString("author")
    artist = obj.getString("artist")
    genre = obj.getJSONArray("genres").joinField("slug")
    status = when (obj.getString("status").substring(0, 7)) {
        "In cors" -> SManga.ONGOING
        "On goin" -> SManga.ONGOING
        "Complet" -> SManga.COMPLETED
        "Licenzi" -> SManga.LICENSED
        "License" -> SManga.LICENSED
        else -> SManga.UNKNOWN
    }
}

/**
 * Creates a [SChapter] by parsing a [JSONObject].
 *
 * @param obj The object containing the chapter info.
 */
fun SChapter.fromJSON(obj: JSONObject) = apply {
    url = obj.getString("url")
    chapter_number = obj.optString("chapter", "-1").toFloat() + "0.${obj.optInt("subchapter", 0)}".toFloat()
    // date_upload = OffsetDateTime.parse(obj.getString("published_on")).toEpochSecond()  // API 26
    date_upload = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.ITALY).parse(obj.getString("published_on"))?.time ?: 0L
    scanlator = obj.getJSONArray("teams").joinField("name", " & ")
    name = obj.getString("full_title")
}
