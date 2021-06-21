package eu.kanade.tachiyomi.extension.all.mangadex

import android.util.Log
import eu.kanade.tachiyomi.extension.all.mangadex.dto.AtHomeDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.ChapterDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.MangaAttributesDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.MangaDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.Headers
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
        "${MDConstants.apiMangaUrl}/$mangaId/feed?includes[]=${MDConstants.scanlator}&limit=500&offset=$offset&translatedLanguage[]=$langCode&order[volume]=desc&order[chapter]=desc"

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

    /**
     * Maps dex status to tachi status
     */
    fun getPublicationStatus(attr: MangaAttributesDto, chapters: List<String>): Int {
        return when (attr.status) {
            null -> SManga.UNKNOWN
            "ongoing" -> SManga.ONGOING
            "completed", "cancelled" -> doubleCheckChapters(attr, chapters)
            "hiatus" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    /**
     * if the manga is 'completed' or 'cancelled' then it'll have a lastChapter in the manga obj.
     * if the simple list of chapters contains that lastChapter, then we can consider it completed.
     */
    private fun doubleCheckChapters(attr: MangaAttributesDto, chapters: List<String>): Int {
        return if (chapters.contains(attr.lastChapter))
            SManga.COMPLETED
        else
            SManga.UNKNOWN
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
        cacheControl: CacheControl,
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
    fun createBasicManga(mangaDto: MangaDto, coverFileName: String?): SManga {
        return SManga.create().apply {
            url = "/manga/${mangaDto.data.id}"
            title = cleanString(mangaDto.data.attributes.title["en"] ?: "")
            coverFileName?.let {
                thumbnail_url = "${MDConstants.cdnUrl}/covers/${mangaDto.data.id}/$coverFileName"
            }
        }
    }

    /**
     * Create an SManga from json element with all details
     */
    fun createManga(mangaDto: MangaDto, chapters: List<String>, lang: String): SManga {
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

            val authors = mangaDto.relationships.filter { relationshipDto ->
                relationshipDto.type.equals(MDConstants.author, true)
            }.mapNotNull { it.attributes!!.name }.distinct()

            val artists = mangaDto.relationships.filter { relationshipDto ->
                relationshipDto.type.equals(MDConstants.artist, true)
            }.mapNotNull { it.attributes!!.name }.distinct()

            val coverFileName = mangaDto.relationships.firstOrNull { relationshipDto ->
                relationshipDto.type.equals(MDConstants.coverArt, true)
            }?.attributes?.fileName

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

            return createBasicManga(mangaDto, coverFileName).apply {
                description = cleanString(attr.description[lang] ?: attr.description["en"] ?: "")
                author = authors.joinToString(", ")
                artist = artists.joinToString(", ")
                status = getPublicationStatus(attr, chapters)
                genre = genreList.joinToString(", ")
            }
        } catch (e: Exception) {
            Log.e("MangaDex", "error parsing manga", e)
            throw(e)
        }
    }

    /**
     * create the SChapter from json
     */
    fun createChapter(chapterDto: ChapterDto): SChapter {
        try {
            val data = chapterDto.data
            val attr = data.attributes

            val groups = chapterDto.relationships.filter { relationshipDto ->
                relationshipDto.type.equals(
                    MDConstants.scanlator,
                    true
                )
            }.mapNotNull { it.attributes!!.name }
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
                scanlator = groups
            }
        } catch (e: Exception) {
            Log.e("MangaDex", "error parsing chapter", e)
            throw(e)
        }
    }
}
