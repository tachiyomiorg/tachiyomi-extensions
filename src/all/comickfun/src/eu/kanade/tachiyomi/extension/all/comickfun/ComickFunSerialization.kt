package eu.kanade.tachiyomi.extension.all.comickfun

import android.os.Build
import android.text.Html
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.serializer
import java.text.SimpleDateFormat
import kotlin.math.pow
import kotlin.math.truncate

/**
 * A serializer of type T which selects the value of type T by traversing down a chain of json objects
 *
 * e.g
 *  {
 *     "user": {
 *          "name": {
 *              "first": "John",
 *              "last": "Smith"
 *          }
 *     }
 *  }
 *
 * deepSelectDeserializer&lt;String&gt;("user", "name", "first") deserializes the above into "John"
 */
@ExperimentalSerializationApi
inline fun <reified T : Any> deepSelectDeserializer(vararg keys: String, tDeserializer: KSerializer<T> = serializer()): KSerializer<T> {
    val descriptors = keys.foldRight(listOf(tDeserializer.descriptor)) { x, acc ->
        acc + acc.last().let {
            buildClassSerialDescriptor("$x\$${it.serialName}") { element(x, it) }
        }
    }.asReversed()
    var a: ((Int) -> KSerializer<T>)? = null
    val b = { depth: Int ->
        object : KSerializer<T> {
            override val descriptor = descriptors[depth]

            override fun deserialize(decoder: Decoder): T {
                return if (depth == keys.size) decoder.decodeSerializableValue(tDeserializer)
                else decoder.decodeStructureByKnownName(descriptor) { names ->
                    names.filter { (name, _) -> name == keys[depth] }
                        .map { (_, index) -> decodeSerializableElement(descriptor, index, a!!(depth + 1)) }
                        .single()
                }
            }

            override fun serialize(encoder: Encoder, value: T) = throw UnsupportedOperationException("Not supported")
        }
    }
    a = b // this is the hackiest of hacky hacks to get around not being able to define recursive inline functions
    return a(0)
}

/**
 * Transforms given json element by lifting specified keys in `element[objKey]` up into `element`
 * Existing conflicts are overwritten
 *
 * @param objKey: String - A key identifying an object in JsonElement
 * @param keys: vararg String - Keys identifying values to lift from objKey
 */
inline fun <reified T : Any> jsonFlatten(objKey: String, vararg keys: String, tDeserializer: KSerializer<T> = serializer()): JsonTransformingSerializer<T> {
    return object : JsonTransformingSerializer<T>(tDeserializer) {
        override fun transformDeserialize(element: JsonElement) = buildJsonObject {
            require(element is JsonObject)
            element.entries.forEach { (key, value) -> put(key, value) }
            val fromObj = element[objKey]
            require(fromObj is JsonObject)
            keys.forEach { put(it, fromObj[it]!!) }
        }
    }
}

@ExperimentalSerializationApi
inline fun <T> Decoder.decodeStructureByKnownName(descriptor: SerialDescriptor, decodeFn: CompositeDecoder.(Sequence<Pair<String, Int>>) -> T): T {
    return decodeStructure(descriptor) {
        decodeFn(
            generateSequence { decodeElementIndex(descriptor) }
                .takeWhile { it != CompositeDecoder.DECODE_DONE }
                .filter { it != CompositeDecoder.UNKNOWN_NAME }
                .map { descriptor.getElementName(it) to it }
        )
    }
}

@ExperimentalSerializationApi
class SChapterDeserializer : KSerializer<SChapter> {
    override val descriptor = buildClassSerialDescriptor(SChapter::class.qualifiedName!!) {
        element<String>("chap")
        element<String>("hid")
        element<String?>("title")
        element<String?>("vol", isOptional = true)
        element<String>("created_at")
        element<String>("iso639_1")
        element<List<String>>("images", isOptional = true)
        element<List<JsonObject>>("md_groups", isOptional = true)
    }

    /** Attempts to parse an ISO-8601 compliant Date Time string with offset to epoch.
     * @returns epochtime on success, 0 on failure
     **/
    private fun parseISO8601(s: String): Long {
        var fractionalPart_ms: Long = 0
        val sNoFraction = Regex("""\.\d+""").replace(s) { match ->
            fractionalPart_ms = truncate(
                match.value.substringAfter(".").toFloat() * 10.0f.pow(-(match.value.length - 1)) * // seconds
                    1000 // milliseconds
            ).toLong()
            ""
        }

        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ").parse(sNoFraction)?.let {
            fractionalPart_ms + it.time
        } ?: 0
    }

    private fun formatChapterTitle(title: String?, chap: String?, vol: String?): String {
        val numNonNull = listOfNotNull(title.takeIf { !it.isNullOrBlank() }, chap, vol).size
        if (numNonNull == 0) throw RuntimeException("formatChapterTitle requires at least one non-null argument")

        val formattedTitle = StringBuilder()
        if (vol != null) formattedTitle.append("${numNonNull.takeIf { it > 1 }?.let { "Vol." } ?: "Volume"} $vol")
        if (vol != null && chap != null) formattedTitle.append(", ")
        if (chap != null) formattedTitle.append("${numNonNull.takeIf { it > 1 }?.let { "Ch." } ?: "Chapter"} $chap")
        if (!title.isNullOrBlank()) formattedTitle.append("${numNonNull.takeIf { it > 1 }?.let { ": " } ?: ""} $title")
        return formattedTitle.toString()
    }

    @ExperimentalSerializationApi
    override fun deserialize(decoder: Decoder): SChapter {
        return SChapter.create().apply {
            var chap: String? = null
            var vol: String? = null
            var title: String? = null
            var hid = ""
            var iso639_1 = ""
            require(decoder is JsonDecoder)
            decoder.decodeStructureByKnownName(descriptor) { names ->
                for ((name, index) in names) {
                    when (name) {
                        "created_at" -> date_upload = parseISO8601(decodeStringElement(descriptor, index))
                        "title" -> title = decodeNullableSerializableElement(descriptor, index, serializer())
                        "vol" -> vol = decodeNullableSerializableElement(descriptor, index, serializer())
                        "chap" -> {
                            chap = decodeStringElement(descriptor, index)
                            chapter_number = chap!!.toFloat()
                        }
                        "hid" -> hid = decodeStringElement(descriptor, index)
                        "iso639_1" -> iso639_1 = decodeStringElement(descriptor, index)
                        "md_groups" -> scanlator = decodeSerializableElement(descriptor, index, ListSerializer(deepSelectDeserializer<String>("title"))).joinToString(", ")
                    }
                }
            }
            name = formatChapterTitle(title, chap, vol)
            url = "/$hid-chapter-$chap-$iso639_1" // incomplete, is finished in fetchChapterList
        }
    }

    override fun serialize(encoder: Encoder, value: SChapter) = throw UnsupportedOperationException("Unsupported")
}

@ExperimentalSerializationApi
class SMangaDeserializer : KSerializer<SManga> {
    private fun cleanDesc(s: String) = (
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY) else Html.fromHtml(s)
        ).toString()

    private fun parseStatus(status: Int) = when (status) {
        1 -> SManga.ONGOING
        2 -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override val descriptor = buildClassSerialDescriptor(SManga::class.qualifiedName!!) {
        element<String>("slug")
        element<String>("title")
        element<String>("coverURL")
        element<String>("id", isOptional = true)
        element<List<JsonObject>>("artists", isOptional = true)
        element<List<JsonObject>>("authors", isOptional = true)
        element<String>("desc", isOptional = true)
        element<String>("demographic", isOptional = true)
        element<List<JsonObject>>("genres", isOptional = true)
        element<Int>("status", isOptional = true)
        element<String>("country", isOptional = true)
    }

    @ExperimentalSerializationApi
    override fun deserialize(decoder: Decoder): SManga {
        return SManga.create().apply {
            var id: Int? = null
            var slug: String? = null
            val tryTo = (
                {
                    var hasThrown = false;
                    { fn: () -> Unit ->
                        if (!hasThrown) {
                            try {
                                fn()
                            } catch (_: java.lang.Exception) {
                                hasThrown = true
                            }
                        }
                    }
                }
                )()
            decoder.decodeStructureByKnownName(descriptor) { names ->
                for ((name, index) in names) {
                    val sluggedNameSerializer = ListSerializer(deepSelectDeserializer<String>("name"))
                    fun nameList() = decodeSerializableElement(descriptor, index, sluggedNameSerializer).joinToString(", ")
                    when (name) {
                        "slug" -> {
                            slug = decodeStringElement(descriptor, index)
                            url = "/comic/$slug"
                        }
                        "title" -> title = decodeStringElement(descriptor, index)
                        "coverURL" -> thumbnail_url = decodeStringElement(descriptor, index)
                        "id" -> id = decodeIntElement(descriptor, index)
                        "artists" -> artist = nameList()
                        "authors" -> author = nameList()
                        "desc" -> description = cleanDesc(decodeStringElement(descriptor, index))
                        // Isn't always a string in every api call
                        "demographic" -> tryTo { genre = listOfNotNull(genre, decodeStringElement(descriptor, index)).joinToString(", ") }
                        // Isn't always a list of objects in every api call
                        "genres" -> tryTo { genre = listOfNotNull(genre, nameList()).joinToString(", ") }
                        "status" -> status = parseStatus(decodeIntElement(descriptor, index))
                        "country" -> genre = listOfNotNull(
                            genre,
                            mapOf("kr" to "Manhwa", "jp" to "Manga", "cn" to "Manhua")[decodeStringElement(descriptor, index)]
                        ).joinToString(", ")
                    }
                }
            }
            if (id != null && slug != null) {
                mangaIdCache[slug!!] = id!!
            }
        }
    }

    override fun serialize(encoder: Encoder, value: SManga) = throw UnsupportedOperationException("Not supported")

    companion object {
        val mangaIdCache = mutableMapOf<String, Int>()
    }
}
