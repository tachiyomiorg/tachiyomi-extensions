package eu.kanade.tachiyomi.extension.all.genkanio

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.util.Calendar

open class GenkanIO : ParsedHttpSource() {
    override val lang = "all"
    final override val name = "Genkan.io"
    final override val baseUrl = "https://genkan.io"
    final override val supportsLatest = false

    private val json: Json by injectLazy()

    /** An interceptor which encapsulates the logic needed to interoperate with Genkan.io's
     *  livewire server, which uses a form a Remote Procedure call
     */
    private val livewireInterceptor = object : Interceptor {
        private lateinit var fingerprint: JsonElement
        lateinit var serverMemo: JsonObject
        private lateinit var csrf: String
        var initialized = false
        val serverUrl = "$baseUrl/livewire/message/manga.list-all-manga"

        /**
         * Given a string encoded with html entities and escape sequences, makes an attempt to decode
         * and returns decoded string
         *
         * Warning: This is not all all exhaustive, and probably misses edge cases
         *
         * @Returns decoded string
         */
        private fun htmlDecode(html: String): String {
            return html.replace(Regex("&([A-Za-z]+);")) { match ->
                mapOf(
                    "raquo" to "»",
                    "laquo" to "«",
                    "amp" to "&",
                    "lt" to "<",
                    "gt" to ">",
                    "quot" to "\""
                )[match.groups[1]!!.value] ?: match.groups[0]!!.value
            }.replace(Regex("\\\\(.)")) { match ->
                mapOf(
                    "t" to "\t",
                    "n" to "\n",
                    "r" to "\r",
                    "b" to "\b"
                )[match.groups[1]!!.value] ?: match.groups[1]!!.value
            }
        }

        /**
         * Recursively merges j2 onto j1 in place
         * If j1 and j2 both contain keys whose values aren't both jsonObjects, j2's value overwrites j1's
         *
         */
        private fun mergeLeft(j1: JsonObject, j2: JsonObject): JsonObject = buildJsonObject {
            j1.keys.forEach { put(it, j1[it]!!) }
            j2.keys.forEach { k ->
                when {
                    j1[k] !is JsonObject -> put(k, j2[k]!!)
                    j1[k] is JsonObject && j2[k] is JsonObject -> put(k, mergeLeft(j1[k]!!.jsonObject, j2[k]!!.jsonObject))
                }
            }
        }

        /**
         * Initializes lateinit member vars
         */
        private fun initLivewire(chain: Interceptor.Chain) {
            val response = chain.proceed(GET("$baseUrl/manga", headers))
            val soup = response.asJsoup()
            response.body?.close()
            val csrfToken = soup.selectFirst("meta[name=csrf-token]")?.attr("content")

            val initialProps = soup.selectFirst("div[wire:initial-data]")?.attr("wire:initial-data")?.let {
                json.parseToJsonElement(htmlDecode(it))
            }

            if (csrfToken != null && initialProps is JsonObject) {
                csrf = csrfToken
                serverMemo = initialProps["serverMemo"]!!.jsonObject
                fingerprint = initialProps["fingerprint"]!!
                initialized = true
            } else {
                Log.e("GenkanIo", soup.selectFirst("div[wire:initial-data]")?.toString() ?: "null")
            }
        }

        /**
         * Builds a request for livewire, augmenting the request with required body fields and headers
         *
         * @param req: Request - A request with a json encoded body, which represent the updates sent to server
         *
         */
        private fun livewireRequest(req: Request): Request {
            val payload = buildJsonObject {
                put("fingerprint", fingerprint)
                put("serverMemo", serverMemo)
                put("updates", json.parseToJsonElement(Buffer().apply { req.body!!.writeTo(this) }.readUtf8()))
            }.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            return req.newBuilder()
                .method(req.method, payload)
                .addHeader("x-csrf-token", csrf)
                .addHeader("x-livewire", "true")
                .build()
        }

        /**
         * Transforms  json response from livewire server into a response which returns html
         *
         * @param response: Response - The response of sending a message to genkan's livewire server
         *
         * @return HTML Response - The html embedded within the provided response
         */
        private fun livewireResponse(response: Response): Response {
            if (!response.isSuccessful) return response
            val body = response.body!!.string()
            val responseJson = json.parseToJsonElement(body).jsonObject

            // response contains state that we need to preserve
            serverMemo = mergeLeft(serverMemo, responseJson["serverMemo"]!!.jsonObject)

            // this seems to be an error  state, so reset everything
            if (responseJson["effects"]?.jsonObject?.get("html") is JsonNull) {
                initialized = false
            }

            // Build html response
            return response.newBuilder()
                .body(htmlDecode("${responseJson["effects"]?.jsonObject?.get("html")}").toResponseBody("Content-Type: text/html; charset=UTF-8".toMediaTypeOrNull()))
                .build()
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            if (chain.request().url.toString() != serverUrl)
                return chain.proceed(chain.request())

            if (!initialized) initLivewire(chain)
            return livewireResponse(chain.proceed(livewireRequest(chain.request())))
        }
    }

    override val client = super.client.newBuilder().addInterceptor(livewireInterceptor).build()

    // popular manga

    override fun fetchPopularManga(page: Int) = fetchSearchManga(page, "", FilterList(emptyList()))
    override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException("Not used")
    override fun popularMangaFromElement(element: Element) = throw UnsupportedOperationException("Not used")
    override fun popularMangaSelector() = throw UnsupportedOperationException("Not used")
    override fun popularMangaNextPageSelector() = throw UnsupportedOperationException("Not used")

    // latest

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("Not used")
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException("Not used")
    override fun latestUpdatesSelector() = throw UnsupportedOperationException("Not used")
    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("Not used")

    // search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val data = if (livewireInterceptor.initialized) livewireInterceptor.serverMemo["data"]!!.jsonObject else buildJsonObject {
            put("readyToLoad", JsonPrimitive(false))
            put("page", JsonPrimitive(1))
            put("search", JsonPrimitive(""))
        }

        val updates = buildJsonArray {
            if (data["readyToLoad"]?.jsonPrimitive?.boolean == false) {
                add(json.parseToJsonElement("""{"type":"callMethod","payload":{"method":"loadManga","params":[]}}"""))
            }
            val isNewQuery = query != data["search"]?.jsonPrimitive?.content
            if (isNewQuery) {
                add(json.parseToJsonElement("""{"type": "syncInput", "payload": {"name": "search", "value": "$query"}}"""))
            }

            val currPage = if (isNewQuery) 1 else data["page"]!!.jsonPrimitive.int

            for (i in (currPage + 1)..page)
                add(json.parseToJsonElement("""{"type":"callMethod","payload":{"method":"nextPage","params":[]}}"""))
        }

        return POST(
            livewireInterceptor.serverUrl,
            headers,
            updates.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        )
    }

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").let {
            manga.url = it.attr("href").substringAfter(baseUrl)
            manga.title = it.text()
        }
        manga.thumbnail_url = element.select("img").attr("src")
        return manga
    }

    override fun searchMangaSelector() = "ul[role=list]:has(a)> li"
    override fun searchMangaNextPageSelector() = "button[rel=next]"

    // chapter list (is paginated),
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException("Not used")
    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException("Not used")
    data class ChapterPage(val chapters: List<SChapter>, val hasnext: Boolean)

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return if (manga.status != SManga.LICENSED) {
            // Returns an observable which emits the list of chapters found on a page,
            // for every page starting from specified page
            fun getAllPagesFrom(page: Int, pred: Observable<List<SChapter>> = Observable.just(emptyList())): Observable<List<SChapter>> =
                client.newCall(chapterListRequest(manga, page))
                    .asObservableSuccess()
                    .concatMap { response ->
                        val cp = chapterPageParse(response)
                        if (cp.hasnext)
                            getAllPagesFrom(page + 1, pred = pred.concatWith(Observable.just(cp.chapters))) // tail call to avoid blowing the stack
                        else
                            pred.concatWith(Observable.just(cp.chapters))
                    }
            getAllPagesFrom(1).reduce(List<SChapter>::plus)
        } else {
            Observable.error(Exception("Licensed - No chapters to show"))
        }
    }

    private fun chapterPageParse(response: Response): ChapterPage {
        val document = response.asJsoup()

        val manga = document.select(chapterListSelector()).map { element ->
            chapterFromElement(element)
        }

        val hasNextPage = chapterListNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return ChapterPage(manga, hasNextPage)
    }

    private fun chapterListRequest(manga: SManga, page: Int): Request {
        val url = "$baseUrl${manga.url}".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("page", "$page")
        return GET("$url", headers)
    }

    override fun chapterFromElement(element: Element): SChapter = element.children().let { tableRow ->
        val isTitleBlank: (String) -> Boolean = { s: String -> s == "-" || s.isBlank() }
        val (numElem, nameElem, languageElem, groupElem, viewsElem) = tableRow
        val (releasedElem, urlElem) = Pair(tableRow[5], tableRow[6])
        SChapter.create().apply {
            name = if (isTitleBlank(nameElem.text())) "Chapter ${numElem.text()}" else "Ch. ${numElem.text()}: ${nameElem.text()}"
            url = urlElem.select("a").attr("href").substringAfter(baseUrl)
            date_upload = parseRelativeDate(releasedElem.text())
            scanlator = "${groupElem.text()} - ${languageElem.text()}"
            chapter_number = numElem.text().toFloat()
        }
    }

    override fun chapterListSelector() = "tbody > tr"
    private fun chapterListNextPageSelector() = "a[rel=next]"

    // manga

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        thumbnail_url = document.selectFirst("section > div > img").attr("src")
        status = SManga.UNKNOWN // unreported
        artist = null // unreported
        author = null // unreported
        description = document.selectFirst("h2").nextElementSibling().text()
            .plus("\n\n\n")
            // Add additional details from info table
            .plus(
                document.select("ul.mt-1").joinToString("\n") {
                    "${it.previousElementSibling().text()}: ${it.text()}"
                }
            )
    }

    private fun parseRelativeDate(date: String): Long {
        val trimmedDate = date.substringBefore(" ago").removeSuffix("s").split(" ")

        val calendar = Calendar.getInstance()
        when (trimmedDate[1]) {
            "year" -> calendar.apply { add(Calendar.YEAR, -trimmedDate[0].toInt()) }
            "month" -> calendar.apply { add(Calendar.MONTH, -trimmedDate[0].toInt()) }
            "week" -> calendar.apply { add(Calendar.WEEK_OF_MONTH, -trimmedDate[0].toInt()) }
            "day" -> calendar.apply { add(Calendar.DAY_OF_MONTH, -trimmedDate[0].toInt()) }
            "hour" -> calendar.apply { add(Calendar.HOUR_OF_DAY, -trimmedDate[0].toInt()) }
            "minute" -> calendar.apply { add(Calendar.MINUTE, -trimmedDate[0].toInt()) }
            "second" -> calendar.apply { add(Calendar.SECOND, 0) }
        }

        return calendar.timeInMillis
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> = document.select("main > div > img").mapIndexed { index, img ->
        Page(index, "", img.attr("src"))
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")
}
