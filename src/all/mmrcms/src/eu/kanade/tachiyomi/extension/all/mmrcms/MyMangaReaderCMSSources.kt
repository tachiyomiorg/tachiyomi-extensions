package eu.kanade.tachiyomi.extension.all.mmrcms

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.bool
import com.github.salomonbrys.kotson.nullArray
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.source.SourceFactory

class MyMangaReaderCMSSources: SourceFactory {
    /**
     * Create a new copy of the sources
     * @return The created sources
     */
    override fun createSources() = parseSources(SOURCES)

    /**
     * Parse a JSON array of sources into a list of `MyMangaReaderCMSSource`s
     *
     * Example JSON array:
     * ```
     * [
     *     {
     *         "language": "en",
     *         "name": "Example manga reader",
     *         "base_url": "http://example.com",
     *         "supports_latest": true,
     *         "item_url": "http://example.com/manga/",
     *         "categories": [
     *             {"id": "stuff", "name": "Stuff"},
     *             {"id": "test", "name": "Test"}
     *         ],
     *         "tags": [
     *             {"id": "action", "name": "Action"},
     *             {"id": "adventure", "name": "Adventure"}
     *         ]
     *     }
     * ]
     * ```
     *
     * Sources that do not supports tags may use `null` instead of a list of json objects
     *
     * @param sourceString The JSON array of sources to parse
     * @return The list of parsed sources
     */
    private fun parseSources(sourceString: String): List<MyMangaReaderCMSSource> {
        val parser = JsonParser()
        val array = parser.parse(sourceString).array

        return array.map {
            it as JsonObject

            val language = it["language"].string
            val name = it["name"].string
            val baseUrl = it["base_url"].string
            val supportsLatest = it["supports_latest"].bool
            val itemUrl = it["item_url"].string
            val categories = mapToPairs(it["categories"].array)
            val tags = it["tags"].nullArray?.let { mapToPairs(it) }

            MyMangaReaderCMSSource(
                    language,
                    name,
                    baseUrl,
                    supportsLatest,
                    itemUrl,
                    categories,
                    tags
            )
        }
    }

    /**
     * Map an array of JSON objects to pairs. Each JSON object must have
     * the following properties:
     *
     * id: first item in pair
     * name: second item in pair
     *
     * @param array The array to process
     * @return The new list of pairs
     */
    private fun mapToPairs(array: JsonArray): List<Pair<String, String>>
            = array.map {
        it as JsonObject

        it["id"].string to it["name"].string
    }
}


