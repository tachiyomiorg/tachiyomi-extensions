package eu.kanade.tachiyomi.extension.all.mangadex

import android.util.Log
import eu.kanade.tachiyomi.extension.all.mangadex.dto.AtHomeDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.AuthorListDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.ChapterDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.CoverDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.CoverListDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.GroupListDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.MangaDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.parser.Parser
import java.util.Date
import java.util.Locale

class MangaDexHelper() {

    val mdFilters = MangaDexFilters()

    val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        allowSpecialFloatingPointValues = true
        useArrayPolymorphism = true
        prettyPrint = true
    }

    /**
     * Gets the UUID from the url
     */
    fun getUUIDFromUrl(url: String) = url.substringAfterLast("/")

    /**
     * get chapters for manga (aka manga/$id/feed endpoint)
     */
    fun getChapterEndpoint(mangaId: String, offset: Int, langCode: String) =
        "${MDConstants.apiMangaUrl}/$mangaId/feed?limit=500&offset=$offset&translatedLanguage[]=$langCode&order[volume]=desc&order[chapter]=desc"

    /**
     * Check if the manga url is a valid uuid
     */
    fun containsUuid(url: String) = url.contains(MDConstants.uuidRegex)

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
        if (cacheControl == CacheControl.FORCE_NETWORK) {
            tokenTracker[tokenRequestUrl] = Date().time
        }
        val response =
            client.newCall(GET(tokenRequestUrl, headers, cacheControl)).execute()
        return json.decodeFromString<AtHomeDto>(response.body!!.string()).baseUrl
    }

    /**
     * create an SManga from json element only basic elements
     */
    fun createBasicManga(mangaDto: MangaDto): SManga {
        return SManga.create().apply {
            url = "/manga/${mangaDto.data.id}"
            title = cleanString(mangaDto.data.attributes.title["en"] ?: "")
        }
    }

    /**
     * Create an SManga from json element with all details
     */
    fun createManga(mangaDto: MangaDto, client: OkHttpClient, lang: String): SManga {
        try {
            val data = mangaDto.data
            val attr = data.attributes

            // things that will go with the genre tags but aren't actually genre

            val tempContentRating = attr.contentRating
            val contentRating =
                if (tempContentRating == null || tempContentRating.equals("safe", true)) {
                    null
                } else {
                    "Content rating: " + tempContentRating.capitalize(Locale.US)
                }

            val nonGenres = listOf(
                (attr.publicationDemographic ?: "").capitalize(Locale.US),
                contentRating,
                Locale(attr.originalLanguage ?: "").displayLanguage
            )

            // get authors ignore if they error, artists are labelled as authors currently
            val authorIds = mangaDto.relationships.filter { relationship ->
                relationship.type.equals("author", true)
            }.map { relationship -> relationship.id }
                .distinct()

            val artistIds = mangaDto.relationships.filter { relationship ->
                relationship.type.equals("artist", true)
            }.map { relationship -> relationship.id }
                .distinct()

            val authorMap = runCatching {
                val ids = listOf(authorIds, artistIds).flatten().distinct()
                    .joinToString("&ids[]=", "?ids[]=")
                val response = client.newCall(GET("${MDConstants.apiUrl}/author$ids")).execute()
                val authorListDto = json.decodeFromString<AuthorListDto>(response.body!!.string())
                authorListDto.results.map { result ->
                    result.data.id to cleanString(result.data.attributes.name)
                }.toMap()
            }.getOrNull() ?: emptyMap()

            val coverId = mangaDto.relationships.filter { relationship ->
                relationship.type.equals("cover_art", true)
            }.map { relationship -> relationship.id }.firstOrNull()!!

            // get tag list
            val tags = mdFilters.getTags()

            // map ids to tag names
            val genreList = (
                attr.tags
                    .map { it.id }
                    .map { dexId ->
                        tags.firstOrNull { it.id == dexId }
                    }
                    .map { it?.name } +
                    nonGenres
                )
                .filter { it.isNullOrBlank().not() }

            return SManga.create().apply {
                url = "/manga/${data.id}"
                title = cleanString(attr.title["en"] ?: "")
                description = cleanString(attr.description[lang] ?: attr.description["en"] ?: "")
                author = authorIds.mapNotNull { authorMap[it] }.joinToString(", ")
                artist = artistIds.mapNotNull { authorMap[it] }.joinToString(", ")
                status = getPublicationStatus(attr.status)
                thumbnail_url = getCoverUrl(data.id, coverId, client)
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
        chapterListDto: List<ChapterDto>,
        client: OkHttpClient
    ): Map<String, String> {
        val groupIds =
            chapterListDto.map { chapterDto -> chapterDto.relationships }
                .flatten()
                .filter { relationshipDto -> relationshipDto.type.equals("scanlation_group", true) }
                .map { relationshipDto -> relationshipDto.id }.distinct()

        // ignore errors if request fails, there is no batch group search yet..
        return runCatching {
            groupIds.chunked(100).map { chunkIds ->
                val ids = chunkIds.joinToString("&ids[]=", "?ids[]=")
                val groupResponse =
                    client.newCall(GET("${MDConstants.apiUrl}/group$ids")).execute()
                // map results to pair id and name
                json.decodeFromString<GroupListDto>(groupResponse.body!!.string())
                    .results.map { result ->
                        result.data.id to result.data.attributes.name
                    }
            }.flatten().toMap()
        }.getOrNull() ?: emptyMap()
    }

    /**
     * create the SChapter from json
     */
    fun createChapter(chapterDto: ChapterDto, groupMap: Map<String, String>): SChapter {
        try {
            val data = chapterDto.data
            val attr = data.attributes

            val scanlatorGroupIds =
                chapterDto.relationships
                    .filter { relationshipDto ->
                        relationshipDto.type.equals(
                            "scanlation_group",
                            true
                        )
                    }
                    .map { relationshipDto -> groupMap[relationshipDto.id] }
                    .joinToString(" & ")

            val chapterName = mutableListOf<String>()
            // Build chapter name

            attr.volume?.let {
                if (it.isNotEmpty()) {
                    chapterName.add("Vol.$it")
                }
            }

            attr.chapter?.let {
                if (it.isNotEmpty()) {
                    chapterName.add("Ch.$it")
                }
            }

            attr.title?.let {
                if (it.isNotEmpty()) {
                    if (chapterName.isNotEmpty()) {
                        chapterName.add("-")
                    }
                    chapterName.add(it)
                }
            }

            // if volume, chapter and title is empty its a oneshot
            if (chapterName.isEmpty()) {
                chapterName.add("Oneshot")
            }
            // In future calculate [END] if non mvp api doesnt provide it

            return SChapter.create().apply {
                url = "/chapter/${data.id}"
                name = cleanString(chapterName.joinToString(" "))
                date_upload = parseDate(attr.publishAt)
                scanlator = scanlatorGroupIds
            }
        } catch (e: Exception) {
            Log.e("MangaDex", "error parsing chapter", e)
            throw(e)
        }
    }

    private fun getCoverUrl(dexId: String, coverId: String, client: OkHttpClient): String {
        val response =
            client.newCall(GET("${MDConstants.apiCoverUrl}/$coverId"))
                .execute()
        val coverDto = json.decodeFromString<CoverDto>(response.body!!.string())
        val fileName = coverDto.data.attributes.fileName
        return "${MDConstants.cdnUrl}/covers/$dexId/$fileName"
    }

    fun getBatchCoversUrl(ids: Map<String, String>, client: OkHttpClient): Map<String, String> {

        val url = MDConstants.apiCoverUrl.toHttpUrl().newBuilder().apply {
            ids.values.forEach { coverArtId ->
                addQueryParameter("ids[]", coverArtId)
            }
            addQueryParameter("limit", ids.size.toString())
        }.build().toString()

        val response = client.newCall(GET(url)).execute()
        val coverListDto = json.decodeFromString<CoverListDto>(response.body!!.string())

        return coverListDto.results.map { coverDto ->
            val fileName = coverDto.data.attributes.fileName
            val mangaId = coverDto.relationships
                .first { relationshipDto -> relationshipDto.type.equals("manga", true) }
                .id
            mangaId to "${MDConstants.cdnUrl}/covers/$mangaId/$fileName"
        }.toMap()
    }
}
