package eu.kanade.tachiyomi.extension.all.genkanio

import android.util.Log
import com.github.salomonbrys.kotson.keys
import com.github.salomonbrys.kotson.put
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.util.Calendar

open class GenkanIO : ParsedHttpSource() {
    override val lang = "all"
    final override val name = "Genkan.io"
    final override val baseUrl = "https://genkan.io"
    final override val supportsLatest = false

    data class LiveWireRPC(val csrf: String, val state: JsonObject)
    private var livewire: LiveWireRPC? = null

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

    /**
     * initializes `livewire` local variable using data from https://genkan.io/manga
     */
    private fun initLiveWire(response: Response) {
        val soup = response.asJsoup()
        val csrf = soup.selectFirst("meta[name=csrf-token]")?.attr("content")

        val initialProps = soup.selectFirst("div[wire:initial-data]")?.attr("wire:initial-data")?.let {
            JsonParser.parseString(htmlDecode(it))
        }

        if (csrf != null && initialProps?.asJsonObject != null) {
            livewire = LiveWireRPC(csrf, initialProps.asJsonObject)
        } else {
            Log.e("GenkanIo", soup.selectFirst("div[wire:initial-data]")?.toString() ?: "null")
        }
    }

    /**
     * Prepares  a request which'll send a message to livewire server
     *
     * @param url: String - Message endpoint
     * @param updates: JsonElement - JsonElement which describes the actions taken by server
     *
     * @return Request
     */
    private fun livewireRequest(url: String, updates: JsonElement): Request {
        // assert(livewire != null)
        val payload = JsonObject()
        payload.put("fingerprint" to livewire!!.state.get("fingerprint"))
        payload.put("serverMemo" to livewire!!.state.get("serverMemo"))
        payload.put("updates" to updates)

        // not sure why this isn't getting added automatically
        val cookie = client.cookieJar.loadForRequest(url.toHttpUrlOrNull()!!).joinToString("; ") { "${it.name}=${it.value}" }
        return POST(
            url,
            Headers.headersOf("x-csrf-token", livewire!!.csrf, "x-livewire", "true", "cookie", cookie, "cache-control", "no-cache, private"),
            payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        )
    }

    /**
     * Transforms  json response from livewire server into a response which returns html
     * Also updates `livewire` variable with state returned by livewire server
     *
     * @param response: Response - The response of sending a message to genkan's livewire server
     *
     * @return HTML Response - The html embedded within the provided response
     */
    private fun livewireResponse(response: Response): Response {
        val body = response.body?.string()
        val responseJson = JsonParser.parseString(body).asJsonObject

        // response contains state that we need to preserve
        mergeLeft(livewire!!.state.get("serverMemo").asJsonObject, responseJson.get("serverMemo").asJsonObject)

        // this seems to be an error  state, so reset everything
        if (responseJson.get("effects")?.asJsonObject?.get("html")?.isJsonNull == true) {
            livewire = null
        }

        // Build html response
        return response.newBuilder()
            .body(htmlDecode("${responseJson.get("effects")?.asJsonObject?.get("html")}").toResponseBody("Content-Type: text/html; charset=UTF-8".toMediaTypeOrNull()))
            .build()
    }

    /**
     * Recursively merges j2 onto j1 in place
     * If j1 and j2 both contain keys whose values aren't both jsonObjects, j2's value overwrites j1's
     *
     */
    private fun mergeLeft(j1: JsonObject, j2: JsonObject) {
        j2.keys().forEach { k ->
            if (j1.get(k)?.isJsonObject != true)
                j1.put(k to j2.get(k))
            else if (j1.get(k).isJsonObject && j2.get(k).isJsonObject)
                mergeLeft(j1.get(k).asJsonObject, j2.get(k).asJsonObject)
        }
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        fun searchRequest() = client.newCall(searchMangaRequest(page, query, filters)).asObservableSuccess().map(::livewireResponse)
        return if (livewire == null) {
            client.newCall(GET("$baseUrl/manga", headers))
                .asObservableSuccess()
                .doOnNext(::initLiveWire)
                .concatWith(Observable.defer(::searchRequest))
                .reduce { _, x -> x }
        } else {
            searchRequest()
        }.map(::searchMangaParse)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        //        assert(livewire != null)
        val updates = JsonArray()
        val data = livewire!!.state.get("serverMemo")?.asJsonObject?.get("data")?.asJsonObject!!
        if (data["readyToLoad"]?.asBoolean == false) {
            updates.add(JsonParser.parseString("""{"type":"callMethod","payload":{"method":"loadManga","params":[]}}"""))
        }
        val isNewQuery = query != data["search"]?.asString
        if (isNewQuery) {
            updates.add(JsonParser.parseString("""{"type": "syncInput", "payload": {"name": "search", "value": "$query"}}"""))
        }

        val currPage = if (isNewQuery) 1 else data["page"]?.asInt

        for (i in (currPage!! + 1)..page)
            updates.add(JsonParser.parseString("""{"type":"callMethod","payload":{"method":"nextPage","params":[]}}"""))

        return livewireRequest("$baseUrl/livewire/message/manga.list-all-manga", updates)
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
            fun getAllPagesFrom(page: Int): Observable<List<SChapter>> =
                client.newCall(chapterListRequest(manga, page))
                    .asObservableSuccess()
                    .concatMap { response ->
                        val cp = chapterPageParse(response)
                        if (cp.hasnext)
                            Observable.just(cp.chapters).concatWith(getAllPagesFrom(page + 1))
                        else
                            Observable.just(cp.chapters)
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
