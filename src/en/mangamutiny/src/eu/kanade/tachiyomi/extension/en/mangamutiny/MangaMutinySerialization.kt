package eu.kanade.tachiyomi.extension.en.mangamutiny

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private fun JsonElement?.primitiveContent(): String? {
    if (this is JsonNull) return null
    return this?.jsonPrimitive?.content
}
private fun JsonElement?.primitiveInt(): Int? {
    if (this is JsonNull) return null
    return this?.jsonPrimitive?.int
}
private fun JsonElement?.primitiveFloat(): Float? {
    if (this is JsonNull) return null
    return this?.jsonPrimitive?.float
}

private val jsonObjectToMapSerializer = MapSerializer(String.serializer(), JsonElement.serializer())

object PageInfoDS : DeserializationStrategy<Pair<List<SManga>, Int>> {
    override val descriptor: SerialDescriptor = jsonObjectToMapSerializer.descriptor

    override fun deserialize(decoder: Decoder): Pair<List<SManga>, Int> {
        require(decoder is JsonDecoder)
        val json = decoder.json
        val jsonElement = decoder.decodeJsonElement()
        require(jsonElement is JsonObject)
        val items = (jsonElement["items"] as JsonArray).map { json.decodeFromJsonElement(SMangaDS, it) }
        val total = jsonElement["total"]?.jsonPrimitive?.int

        require(total != null)
        return Pair(items, total)
    }
}

object SMangaDS : DeserializationStrategy<SManga> {
    override val descriptor: SerialDescriptor = jsonObjectToMapSerializer.descriptor

    override fun deserialize(decoder: Decoder): SManga {
        require(decoder is JsonDecoder)
        val jsonElement = decoder.decodeJsonElement()
        require(jsonElement is JsonObject)
        val title = jsonElement["title"].primitiveContent()
        val slug = jsonElement["slug"].primitiveContent()
        val thumbnail = jsonElement["thumbnail"].primitiveContent()

        val status: Int = jsonElement["status"].primitiveContent()?.let {
            when (it) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        } ?: SManga.UNKNOWN

        val summary: String? = jsonElement["summary"].primitiveContent()
        val artists: String? = jsonElement["artists"].primitiveContent()
        val authors: String? = jsonElement["authors"].primitiveContent()
        val tags: String? =
            jsonElement["tags"]?.jsonArray?.mapNotNull { it.primitiveContent() }?.joinToString()

        require(title != null && slug != null)
        return SManga.create().apply {
            this.title = title
            this.url = slug
            this.thumbnail_url = thumbnail

            this.status = status
            this.description = summary
            this.artist = artists
            this.author = authors
            this.genre = tags
        }
    }
}

object ListChapterDS : DeserializationStrategy<List<SChapter>> {
    override val descriptor: SerialDescriptor = jsonObjectToMapSerializer.descriptor

    override fun deserialize(decoder: Decoder): List<SChapter> {
        require(decoder is JsonDecoder)
        val json = decoder.json
        val jsonElement = decoder.decodeJsonElement()
        require(jsonElement is JsonObject)

        val jsonElementChapters = jsonElement["chapters"]?.jsonArray
        require(jsonElementChapters != null)

        return jsonElementChapters.map { chapter ->
            json.decodeFromJsonElement(SChapterDS, chapter)
        }.apply {
            if (this.size == 1) this.first().chapter_number = 1F
        }
    }
}

private object SChapterDS : DeserializationStrategy<SChapter> {

    override val descriptor: SerialDescriptor = jsonObjectToMapSerializer.descriptor

    override fun deserialize(decoder: Decoder): SChapter {
        require(decoder is JsonDecoder)
        val jsonElement = decoder.decodeJsonElement()
        require(jsonElement is JsonObject)
        val volume: Int? = jsonElement["volume"].primitiveInt()
        val chapter: Float? = jsonElement["chapter"].primitiveFloat()
        val title: String? = jsonElement["title"].primitiveContent()
        val slug: String? = jsonElement["slug"].primitiveContent()
        val releasedAt: String? = jsonElement["releasedAt"].primitiveContent()

        require(slug != null && releasedAt != null)
        return SChapter.create().apply {
            if (chapter != null) this.chapter_number = chapter
            this.name = chapterTitleBuilder(volume, title, chapter)
            this.url = slug
            this.date_upload = dateFormatter.parse(releasedAt)?.time ?: 0
        }
    }

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }

    /**
     * Converts this Float into a String, removing any trailing .0
     */
    private fun Float.toStringWithoutDotZero(): String = when (this % 1) {
        0F -> this.toInt().toString()
        else -> this.toString()
    }

    private fun chapterTitleBuilder(volume: Int?, title: String?, chapter: Float?): String {
        val chapterTitle = StringBuilder()
        if (volume != null) {
            chapterTitle.append("Vol. $volume ")
        }
        if (chapter != null) {
            chapterTitle.append("Chapter ${chapter.toStringWithoutDotZero()}")
        }
        if (title != null && title != "") {
            if (chapterTitle.isNotEmpty()) chapterTitle.append(": ")
            chapterTitle.append(title)
        }
        return chapterTitle.toString()
    }
}

object ListPageDS : DeserializationStrategy<List<Page>> {
    override val descriptor: SerialDescriptor = jsonObjectToMapSerializer.descriptor

    override fun deserialize(decoder: Decoder): List<Page> {
        require(decoder is JsonDecoder)
        val jsonElement = decoder.decodeJsonElement()
        require(jsonElement is JsonObject)

        val storage: String? = jsonElement["storage"].primitiveContent()
        val manga: String? = jsonElement["manga"].primitiveContent()
        val id: String? = jsonElement["id"].primitiveContent()
        val images: List<String>? =
            jsonElement["images"]?.jsonArray?.mapNotNull { it.primitiveContent() }

        require(storage != null && manga != null && id != null && images != null)
        val chapterUrl = "$storage/$manga/$id/"
        return images.mapIndexed { index, pageSuffix -> Page(index, "", chapterUrl + pageSuffix) }
    }
}
