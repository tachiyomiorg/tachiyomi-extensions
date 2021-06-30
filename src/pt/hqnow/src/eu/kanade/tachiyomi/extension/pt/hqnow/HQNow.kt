package eu.kanade.tachiyomi.extension.pt.hqnow

import eu.kanade.tachiyomi.lib.ratelimit.SpecificHostRateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.Normalizer
import java.util.Locale

class HQNow : HttpSource() {

    override val name = "HQ Now!"

    override val baseUrl = "http://www.hq-now.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(SpecificHostRateLimitInterceptor(GRAPHQL_URL.toHttpUrl(), 1))
        .addInterceptor(SpecificHostRateLimitInterceptor(STATIC_URL.toHttpUrl(), 2))
        .build()

    private val json: Json by injectLazy()

    private fun genericComicBookFromObject(comicBook: HqNowComicBookDto): SManga =
        SManga.create().apply {
            title = comicBook.name
            url = "/hq/${comicBook.id}/${comicBook.name.toSlug()}"
            thumbnail_url = comicBook.cover
        }

    override fun popularMangaRequest(page: Int): Request {
        val query = buildQuery {
            """
                query getHqsByFilters(
                    %orderByViews: Boolean,
                    %limit: Int,
                    %publisherId: Int,
                    %loadCovers: Boolean
                ) {
                    getHqsByFilters(
                        orderByViews: %orderByViews,
                        limit: %limit,
                        publisherId: %publisherId,
                        loadCovers: %loadCovers
                    ) {
                        id
                        name
                        editoraId
                        status
                        publisherName
                        hqCover
                        synopsis
                        updatedAt
                    }
                }
            """.trimIndent()
        }

        val payload = buildJsonObject {
            put("operationName", "getHqsByFilters")
            put("query", query)
            putJsonObject("variables") {
                put("orderByViews", true)
                put("loadCovers", true)
                put("limit", 300)
            }
        }

        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)

        val newHeaders = headersBuilder()
            .add("Content-Length", body.contentLength().toString())
            .add("Content-Type", body.contentType().toString())
            .build()

        return POST(GRAPHQL_URL, newHeaders, body)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = json.parseToJsonElement(response.body!!.string()).jsonObject

        val comicList = result["data"]!!.jsonObject["getHqsByFilters"]!!
            .let { json.decodeFromJsonElement<List<HqNowComicBookDto>>(it) }
            .map(::genericComicBookFromObject)

        return MangasPage(comicList, hasNextPage = false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val query = buildQuery {
            """
                query getRecentlyUpdatedHqs {
                    getRecentlyUpdatedHqs {
                        name
                        hqCover
                        synopsis
                        id
                        updatedAt
                        updatedChapters
                    }
                }
            """.trimIndent()
        }

        val payload = buildJsonObject {
            put("operationName", "getRecentlyUpdatedHqs")
            put("query", query)
        }

        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)

        val newHeaders = headersBuilder()
            .add("Content-Length", body.contentLength().toString())
            .add("Content-Type", body.contentType().toString())
            .build()

        return POST(GRAPHQL_URL, newHeaders, body)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = json.parseToJsonElement(response.body!!.string()).jsonObject

        val comicList = result["data"]!!.jsonObject["getRecentlyUpdatedHqs"]!!
            .let { json.decodeFromJsonElement<List<HqNowComicBookDto>>(it) }
            .map(::genericComicBookFromObject)

        return MangasPage(comicList, hasNextPage = false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val queryStr = buildQuery {
            """
                query getHqsByName(%name: String!) {
                    getHqsByName(name: %name) {
                        id
                        name
                        editoraId
                        status
                        publisherName
                        impressionsCount
                    }
                }
            """.trimIndent()
        }

        val payload = buildJsonObject {
            put("operationName", "getHqsByName")
            put("query", queryStr)
            putJsonObject("variables") {
                put("name", query)
            }
        }

        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)

        val newHeaders = headersBuilder()
            .add("Content-Length", body.contentLength().toString())
            .add("Content-Type", body.contentType().toString())
            .build()

        return POST(GRAPHQL_URL, newHeaders, body)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = json.parseToJsonElement(response.body!!.string()).jsonObject

        val comicList = result["data"]!!.jsonObject["getHqsByName"]!!
            .let { json.decodeFromJsonElement<List<HqNowComicBookDto>>(it) }
            .map(::genericComicBookFromObject)

        return MangasPage(comicList, hasNextPage = false)
    }

    // Workaround to allow "Open in browser" use the real URL.
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsApiRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    private fun mangaDetailsApiRequest(manga: SManga): Request {
        val comicBookId = manga.url.substringAfter("/hq/").substringBefore("/")

        val query = buildQuery {
            """
                query getHqsById(%id: Int!) {
                    getHqsById(id: %id) {
                        id
                        name
                        synopsis
                        editoraId
                        status
                        publisherName
                        hqCover
                        impressionsCount
                        capitulos {
                            name
                            id
                            number
                        }
                    }
                }
            """.trimIndent()
        }

        val payload = buildJsonObject {
            put("operationName", "getHqsById")
            put("query", query)
            putJsonObject("variables") {
                put("id", comicBookId.toInt())
            }
        }

        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)

        val newHeaders = headersBuilder()
            .add("Content-Length", body.contentLength().toString())
            .add("Content-Type", body.contentType().toString())
            .build()

        return POST(GRAPHQL_URL, newHeaders, body)
    }

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val result = json.parseToJsonElement(response.body!!.string()).jsonObject
        val comicBook = result["data"]!!.jsonObject["getHqsById"]!!.jsonArray[0].jsonObject
            .let { json.decodeFromJsonElement<HqNowComicBookDto>(it) }

        title = comicBook.name
        thumbnail_url = comicBook.cover
        description = comicBook.synopsis.orEmpty()
        author = comicBook.publisherName.orEmpty()
        status = comicBook.status.orEmpty().toStatus()
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsApiRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = json.parseToJsonElement(response.body!!.string()).jsonObject
        val comicBook = result["data"]!!.jsonObject["getHqsById"]!!.jsonArray[0].jsonObject
            .let { json.decodeFromJsonElement<HqNowComicBookDto>(it) }

        return comicBook.chapters
            .map { chapter -> chapterFromObject(chapter, comicBook) }
            .reversed()
    }

    private fun chapterFromObject(chapter: HqNowChapterDto, comicBook: HqNowComicBookDto): SChapter =
        SChapter.create().apply {
            name = "#" + chapter.number +
                (if (chapter.name.isNotEmpty()) " - " + chapter.name else "")
            url = "/hq-reader/${comicBook.id}/${comicBook.name.toSlug()}" +
                "/chapter/${chapter.id}/page/1"
        }

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfter("/chapter/").substringBefore("/")

        val query = buildQuery {
            """
                query getChapterById(%chapterId: Int!) {
                    getChapterById(chapterId: %chapterId) {
                        name
                        number
                        oneshot
                        pictures {
                            pictureUrl
                        }
                    }
                }
            """.trimIndent()
        }

        val payload = buildJsonObject {
            put("operationName", "getChapterById")
            put("query", query)
            putJsonObject("variables") {
                put("chapterId", chapterId.toInt())
            }
        }

        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)

        val newHeaders = headersBuilder()
            .add("Content-Length", body.contentLength().toString())
            .add("Content-Type", body.contentType().toString())
            .build()

        return POST(GRAPHQL_URL, newHeaders, body)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = json.parseToJsonElement(response.body!!.string()).jsonObject

        val chapterDto = result["data"]!!.jsonObject["getChapterById"]!!
            .let { json.decodeFromJsonElement<HqNowChapterDto>(it) }

        return chapterDto.pictures.mapIndexed { i, page ->
            Page(i, baseUrl, page.pictureUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private fun buildQuery(queryAction: () -> String) = queryAction().replace("%", "$")

    private fun String.toSlug(): String {
        return Normalizer
            .normalize(this, Normalizer.Form.NFD)
            .replace("[^\\p{ASCII}]".toRegex(), "")
            .replace("[^a-zA-Z0-9\\s]+".toRegex(), "").trim()
            .replace("\\s+".toRegex(), "-")
            .toLowerCase(Locale("pt", "BR"))
    }

    private fun String.toStatus(): Int = when (this) {
        "Concluído" -> SManga.COMPLETED
        "Em Andamento" -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    companion object {
        private const val STATIC_URL = "http://static.hq-now.com/"
        private const val GRAPHQL_URL = "http://admin.hq-now.com/graphql"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
    }
}
