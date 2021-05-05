package eu.kanade.tachiyomi.extension.all.mangadex

import android.util.Log
import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.nullString
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.parser.Parser
import java.util.Date

class MangaDexHelper() {

    val mdFilters = MangaDexFilters()

    /**
     * Gets the UUID from the url
     */
    fun getUUIDFromUrl(url: String) = url.substringAfterLast("/")

    /**
     * get the manga feed url
     */
    fun getChapterEndpoint(mangaId: String, offset: Int, langCode: String) =
        "${MDConstants.apiMangaUrl}/$mangaId/feed?limit=500&offset=$offset&locales[]=$langCode"

    /**
     * Check if the manga id is a valid uuid
     */
    fun containsUuid(id: String) = id.contains(MDConstants.uuidRegex)

    /**
     * Get the manga offset pages are 1 based, so subtract 1
     */
    fun getMangaListOffset(page: Int): String = (MDConstants.mangaLimit * (page - 1)).toString()

    /**
     * Remove bbcode tags as well as parses any html characters in description or
     * chapter name to actual characters for example &hearts; will show ♥
     */
    fun cleanString(string: String): String {
        val bbRegex =
            """\[(\w+)[^]]*](.*?)\[/\1]""".toRegex()
        var intermediate = string
            .replace("[list]", "")
            .replace("[/list]", "")
            .replace("[*]", "")
        // Recursively remove nested bbcode
        while (bbRegex.containsMatchIn(intermediate)) {
            intermediate = intermediate.replace(bbRegex, "$2")
        }
        return Parser.unescapeEntities(intermediate, false)
    }

    /**Maps dex status to tachi status
     * abandoned and completed statuses's need addition checks with chapter info if we are to be accurate
     */
    fun getPublicationStatus(dexStatus: String?): Int {
        return when (dexStatus) {
            null -> SManga.UNKNOWN
            "ongoing" -> SManga.ONGOING
            "hiatus" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    fun parseDate(dateAsString: String): Long =
        MDConstants.dateFormatter.parse(dateAsString)?.time ?: 0

    // chapter url where we get the token, last request time
    private val tokenTracker = hashMapOf<String, Long>()

    // Check the token map to see if the md@home host is still valid
    fun getValidImageUrlForPage(page: Page, headers: Headers, client: OkHttpClient): Request {
        val data = page.url.split(",")
        val mdAtHomeServerUrl =
            when (Date().time - data[2].toLong() > MDConstants.mdAtHomeTokenLifespan) {
                false -> data[0]
                true -> {
                    val tokenRequestUrl = data[1]
                    val cacheControl =
                        if (Date().time - (
                            tokenTracker[tokenRequestUrl]
                                ?: 0
                            ) > MDConstants.mdAtHomeTokenLifespan
                        ) {
                            tokenTracker[tokenRequestUrl] = Date().time
                            CacheControl.FORCE_NETWORK
                        } else {
                            CacheControl.FORCE_CACHE
                        }
                    getMdAtHomeUrl(tokenRequestUrl, client, headers, cacheControl)
                }
            }
        return GET(mdAtHomeServerUrl + page.imageUrl, headers)
    }

    /**
     * get the md@home url
     */
    fun getMdAtHomeUrl(
        tokenRequestUrl: String,
        client: OkHttpClient,
        headers: Headers,
        cacheControl: CacheControl
    ): String {
        val response =
            client.newCall(GET(tokenRequestUrl, headers, cacheControl)).execute()
        return JsonParser.parseString(response.body!!.string()).obj["baseUrl"].string
    }

    /**
     * create an SManga from json element only basic elements
     */
    fun createManga(mangaJson: JsonElement): SManga {
        val data = mangaJson["data"].obj
        val dexId = data["id"].string
        val attr = data["attributes"].obj

        return SManga.create().apply {
            url = "/manga/$dexId"
            title = cleanString(attr["title"]["en"].string)
            thumbnail_url = ""
        }
    }

    /**
     * Create an SManga from json element with all details
     */
    fun createManga(mangaJson: JsonElement, client: OkHttpClient): SManga {
        try {
            val data = mangaJson["data"].obj
            val dexId = data["id"].string
            val attr = data["attributes"].obj

            // things that will go with the genre tags but aren't actually genre
            val nonGenres = listOf(
                attr["contentRating"].nullString,
                attr["originalLanguage"]?.nullString,
                attr["publicationDemographic"]?.nullString
            )

            // get authors ignore if they error, artists are labelled as authors currently
            val authorIds = mangaJson["relationships"].array.filter { relationship ->
                relationship["type"].string.equals("author", true)
            }.map { relationship -> relationship["id"].string }
                .distinct()

            val authors = runCatching {
                val ids = authorIds.joinToString("&ids[]=", "?ids[]=")
                val response = client.newCall(GET("${MDConstants.apiUrl}/author$ids")).execute()
                val json = JsonParser.parseString(response.body!!.string())
                json.obj["results"].array.map { result ->
                    cleanString(result["data"]["attributes"]["name"].string)
                }
            }.getOrNull() ?: emptyList()

            // get tag list
            val tags = mdFilters.getTags()

            // map ids to tag names
            val genreList = (
                attr["tags"].array
                    .map { it["id"].string }
                    .map { dexTag ->
                        tags.firstOrNull { it.name.equals(dexTag, true) }
                    }.map { it?.name } +
                    nonGenres
                )
                .filterNotNull()

            return SManga.create().apply {
                url = "/manga/$dexId"
                title = cleanString(attr["title"]["en"].string)
                description = cleanString(attr["description"]["en"].string)
                author = authors.joinToString(", ")
                status = getPublicationStatus(attr["publicationDemographic"].nullString)
                thumbnail_url = ""
                genre = genreList.joinToString(", ")
            }
        } catch (e: Exception) {
            Log.e("MangaDex", "error parsing manga", e)
            throw(e)
        }
    }

    /**
     * This makes an api call per a unique group id found in the chapters hopefully Dex will eventually support
     * batch ids
     */
    fun createGroupMap(
        chapterListResults: List<JsonElement>,
        client: OkHttpClient
    ): Map<String, String> {
        val groupIds =
            chapterListResults.map { it["relationships"].array }
                .flatten()
                .filter { it["type"].string == "scanlation_group" }
                .map { it["id"].string }.distinct()

        // ignore errors if request fails, there is no batch group search yet..
        return runCatching {
            groupIds.chunked(100).map { chunkIds ->
                val ids = chunkIds.joinToString("&ids[]=", "?ids[]=")
                val groupResponse =
                    client.newCall(GET("${MDConstants.apiUrl}/group$ids")).execute()
                // map results to pair id and name
                JsonParser.parseString(groupResponse.body!!.string())
                    .obj["results"].array.map { result ->
                    val id = result["data"]["id"].string
                    val name = result["data"]["attributes"]["name"].string
                    Pair(id, cleanString(name))
                }
            }.flatten().toMap()
        }.getOrNull() ?: emptyMap()
    }

    /**
     * create the SChapter from json
     */
    fun createChapter(chapterJsonResponse: JsonElement, groupMap: Map<String, String>): SChapter {
        try {
            val data = chapterJsonResponse["data"].obj
            val scanlatorGroupIds =
                chapterJsonResponse["relationships"].array.filter { it["type"].string == "scanlation_group" }
                    .map { groupMap[it["id"].string] }
                    .joinToString(" & ")
            val attr = data["attributes"]

            val chapterName = mutableListOf<String>()
            // Build chapter name

            attr["volume"].nullString?.let {
                if (it.isNotEmpty()) {
                    chapterName.add("Vol.$it")
                }
            }

            attr["chapter"].nullString?.let {
                if (it.isNotEmpty()) {
                    chapterName.add("Ch.$it")
                }
            }

            attr["title"].nullString?.let {
                if (chapterName.isNotEmpty() && it.isNotEmpty()) {
                    chapterName.add("-")
                    chapterName.add(it)
                }
            }
            // if volume, chapter and title is empty its a oneshot
            if (chapterName.isEmpty()) {
                chapterName.add("Oneshot")
            }
            // In future calculate [END] if non mvp api doesnt provide it

            return SChapter.create().apply {
                url = "/chapter/${data["id"].string}"
                name = cleanString(chapterName.joinToString(" "))
                date_upload = parseDate(attr["publishAt"].string)
                scanlator = scanlatorGroupIds
            }
        } catch (e: Exception) {
            Log.e("MangaDex", "error parsing chapter", e)
            throw(e)
        }
    }
}
