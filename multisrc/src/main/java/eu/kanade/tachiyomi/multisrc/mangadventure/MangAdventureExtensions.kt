package eu.kanade.tachiyomi.multisrc.mangadventure

import android.net.Uri
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.DecimalFormat

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
 * @param field The index of a [JsonArray].
 * @param sep The separator used to join the array.
 * @return The joined string, or `null` if the array is empty.
 */
fun JsonArray.joinField(field: Int, sep: String = ", ") =
    size.takeIf { it != 0 }?.run {
        joinToString(sep) { it.jsonArray[field].jsonPrimitive.content }
    }

/**
 * Joins each value of a given [field] of the array using [sep].
 *
 * @param field The key of a [JsonObject].
 * @param sep The separator used to join the array.
 * @return The joined string, or `null` if the array is empty.
 */
fun JsonArray.joinField(field: String, sep: String = ", ") =
    size.takeIf { it != 0 }?.run {
        joinToString(sep) { it.jsonObject[field]!!.jsonPrimitive.content }
    }

/** The slug of a manga. */
val SManga.slug: String
    get() = Uri.parse(url).lastPathSegment!!

/**
 * Creates a [SManga] by parsing a [JsonObject].
 *
 * @param obj The object containing the manga info.
 */
fun SManga.fromJSON(obj: JsonObject) = apply {
    url = obj["url"]!!.jsonPrimitive.content
    title = obj["title"]!!.jsonPrimitive.content
    description = obj["description"]!!.jsonPrimitive.content
    thumbnail_url = obj["cover"]!!.jsonPrimitive.content
    author = obj["authors"]!!.jsonArray.joinField(0)
    artist = obj["artists"]!!.jsonArray.joinField(0)
    genre = obj["categories"]!!.jsonArray.joinField("name")
    status = if (obj["completed"]!!.jsonPrimitive.boolean)
        SManga.COMPLETED else SManga.ONGOING
}

/** The unique path of a chapter. */
val SChapter.path: String
    get() = url.substringAfter("/reader/")

/**
 * Creates a [SChapter] by parsing a [JsonObject].
 *
 * @param obj The object containing the chapter info.
 */
fun SChapter.fromJSON(obj: JsonObject) = apply {
    url = obj["url"]!!.jsonPrimitive.content
    chapter_number = obj["chapter"]?.jsonPrimitive?.content?.toFloat() ?: -1f
    date_upload = MangAdventure.httpDateToTimestamp(
        obj["date"]!!.jsonPrimitive.content
    )
    scanlator = obj["groups"]!!.jsonArray.joinField("name", " & ")
    name = obj["full_title"]?.jsonPrimitive?.contentOrNull ?: buildString {
        obj["volume"]?.jsonPrimitive?.intOrNull?.let {
            if (it != 0) append("Vol. $it, ")
        }
        append("Ch. ${chapter_number.format("#.#")}: ")
        append(obj["title"]!!.jsonPrimitive.content)
    }
    if (obj["final"]!!.jsonPrimitive.boolean) name += " [END]"
}
