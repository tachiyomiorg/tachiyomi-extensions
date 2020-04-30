package eu.kanade.tachiyomi.extension.all.mmrcms

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.bool
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.source.SourceFactory

class MyMangaReaderCMSSources : SourceFactory {
    /**
     * Create a new copy of the sources
     * @return The created sources
     */
    override fun createSources() = parseSources(SOURCES)

    /**
     * Parse a List of JSON sources into a list of `MyMangaReaderCMSSource`s
     *
     * Example JSON :
     * ```
     *     {
     *         "language": "en",
     *         "name": "Example manga reader",
     *         "base_url": "https://example.com",
     *         "supports_latest": true,
     *         "item_url": "https://example.com/manga/",
     *         "categories": [
     *             {"id": "stuff", "name": "Stuff"},
     *             {"id": "test", "name": "Test"}
     *         ],
     *         "tags": [
     *             {"id": "action", "name": "Action"},
     *             {"id": "adventure", "name": "Adventure"}
     *         ]
     *     }
     *
     *
     * Sources that do not supports tags may use `null` instead of a list of json objects
     *
     * @param sourceString The List of JSON strings 1 entry = one source
     * @return The list of parsed sources
     */
    private fun parseSources(sourceString: List<String>): List<MyMangaReaderCMSSource> {
        val parser = JsonParser()
        return sourceString.map {
            val jsonObject = parser.parse(it) as JsonObject

            val language = jsonObject["language"].string
            val name = jsonObject["name"].string
            val baseUrl = jsonObject["base_url"].string
            val supportsLatest = jsonObject["supports_latest"].bool
            val itemUrl = jsonObject["item_url"].string
            val categories = mapToPairs(jsonObject["categories"].array)
            var tags = emptyList<Pair<String, String>>()
            if (jsonObject["tags"].isJsonArray) {
                tags = jsonObject["tags"].asJsonArray.let { mapToPairs(it) }
            }

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
    private fun mapToPairs(array: JsonArray): List<Pair<String, String>> = array.map {
        it as JsonObject

        it["id"].string to it["name"].string
    }
}
