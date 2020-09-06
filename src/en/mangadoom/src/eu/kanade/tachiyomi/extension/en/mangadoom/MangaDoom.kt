package eu.kanade.tachiyomi.extension.en.mangadoom

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import java.io.IOException
import java.nio.charset.Charset
import java.util.Calendar
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

class MangaDoom : HttpSource() {

    // TODO Implement genre filters
    // TODO replace icons

    override val baseUrl = "https://www.mngdoom.com"
    override val lang = "en"
    override val name = "MangaDoom"
    override val supportsLatest = true

    private val popularMangaPath = "/popular-manga/"

    private val popularMangaSelector = "div.row.manga-list-style"

    // popular
    override fun popularMangaRequest(page: Int) = GET(baseUrl + popularMangaPath + page)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        return MangasPage(document.select(popularMangaSelector).map {
            mangaFromMangaListElement(it)
        }, paginationHasNext(document))
    }

    // latest
    private val latestMangaPath = "/latest-chapters"

    override fun latestUpdatesRequest(page: Int): Request {
        var url = baseUrl + latestMangaPath

        if (page != 1) {
            url += "/${page - 1}"
        }

        return GET(url)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangaUpdates = document.select("div.manga_updates > dl > div.manga-cover > a")

        return MangasPage(mangaUpdates.map { mangaFromMangaTitleElement(it) }, paginationHasNext(document))
    }

    private fun paginationHasNext(document: Document) = !document.select("ul.pagination > li:contains(»)").isEmpty()

    // individual manga
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        val innerContentElement = document.select("div.content-inner.inner-page").first()
        val descriptionListElement = innerContentElement.select("div.col-md-8 > dl").first()

        return SManga.create().apply {
            this.url = response.request().url().toString()

            this.title = innerContentElement.select("h5.widget-heading:matchText").first().text()
            this.thumbnail_url = innerContentElement.select("div.col-md-4 > img").first()?.attr("src")

            this.genre = descriptionListElement.select("dt:containsOwn(Categories:) + dd > a")
                .joinToString { e -> e.attr("title") }

            this.description = innerContentElement.select("div.note").first()?.let {
                descriptionProcessor(it)
            }

            this.author = descriptionListElement.select("dt:containsOwn(Author:) + dd > a")
                .first()?.ownText().takeIf { it != "-" }

            this.artist = descriptionListElement.select("dt:containsOwn(Artist:) + dd > a")
                .first()?.ownText().takeIf { it != "-" }

            this.status = when (descriptionListElement.select("dt:containsOwn(Status:) + dd")
                .first().ownText()) {
                "Ongoing" -> SManga.ONGOING
                "Completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    private fun descriptionProcessor(descriptionRootNode: Node): String? {

        val descriptionStringBuilder = StringBuilder()

        fun descriptionElementProcessor(descriptionNode: Node): String? {
            if (descriptionNode is Element) {
                if (descriptionNode.tagName() == "br") {
                    return "\n"
                }
            } else if (descriptionNode is TextNode) {
                return descriptionNode.text()
            }

            return null
        }

        fun descriptionHierarchyProcessor(currentNode: Node) {
            descriptionElementProcessor(currentNode)?.let {
                descriptionStringBuilder.append(it)
            }

            val childNodesIterator = currentNode.childNodes().iterator()

            while (childNodesIterator.hasNext()) {
                descriptionHierarchyProcessor(childNodesIterator.next())
            }

            if (currentNode is Element && currentNode.tagName() == "p") {
                descriptionStringBuilder.append("\n\n")
            }
        }

        descriptionHierarchyProcessor(descriptionRootNode)

        return if (descriptionStringBuilder.isNotEmpty()) {
            descriptionStringBuilder.toString().trimEnd()
        } else {
            null
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = response.asJsoup().select("ul.chapter-list > li").reversed()

        return chapters.map { SChapter.create().apply {
            this.name = it.select("span.val").first().ownText()
            this.url = it.select("a").first().attr("href")
            this.chapter_number = chapters.indexOf(it).toFloat()

            val calculatedDate = it.select("span.date").first().ownText()?.let {
                parseDate(it)
            }

            if (calculatedDate != null) {
                this.date_upload = calculatedDate
            }
        } }
    }

    private fun Calendar.setWithDefaults(
        year: Int = this.get(Calendar.YEAR),
        month: Int = this.get(Calendar.MONTH),
        date: Int = this.get(Calendar.DATE),
        hourOfDay: Int = this.get(Calendar.HOUR_OF_DAY),
        minute: Int = this.get(Calendar.MINUTE),
        second: Int = this.get(Calendar.SECOND)
    ) {
        this.set(Calendar.MILLISECOND, 0)
        this.set(year, month, date, hourOfDay, minute, second)
    }

    private val regexFirstNumberPattern = Regex("^\\d*")
    private val regexLastWordPattern = Regex("\\w*\$")

    private fun parseDate(inputString: String): Long? {

        val timeDifference = regexFirstNumberPattern.find(inputString)?.let {
            it.value.toInt() * (-1)
        }

        val lastWord = regexLastWordPattern.find(inputString)?.value

        if (lastWord != null && timeDifference != null) {
            val calculatedTime = Calendar.getInstance()

            when (lastWord) {
                "Years", "Year" -> {
                    calculatedTime.setWithDefaults(month = 0, date = 1, hourOfDay = 0, minute = 0, second = 0)
                    calculatedTime.add(Calendar.YEAR, timeDifference)
                }

                "Months", "Month" -> {
                    calculatedTime.setWithDefaults(date = 1, hourOfDay = 0, minute = 0, second = 0)
                    calculatedTime.add(Calendar.MONTH, timeDifference)
                }

                "Weeks", "Week" -> {
                    calculatedTime.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    calculatedTime.setWithDefaults(hourOfDay = 0, minute = 0, second = 0)
                    calculatedTime.add(Calendar.WEEK_OF_YEAR, timeDifference)
                }
                "Days", "Day" -> {
                    calculatedTime.setWithDefaults(hourOfDay = 0, minute = 0, second = 0)
                    calculatedTime.add(Calendar.DATE, timeDifference)
                }
                "Hours", "Hour" -> {
                    calculatedTime.setWithDefaults(minute = 0, second = 0)
                    calculatedTime.add(Calendar.HOUR_OF_DAY, timeDifference)
                }
                "Minutes", "Minute" -> {
                    calculatedTime.setWithDefaults(second = 0)
                    calculatedTime.add(Calendar.MINUTE, timeDifference)
                }
                "Seconds", "Second" -> {
                    calculatedTime.set(Calendar.MILLISECOND, 0)
                    calculatedTime.add(Calendar.SECOND, timeDifference)
                }
            }

            return calculatedTime.time.time
        } else {
            return null
        }
    }

    private val allPagesURLPart = "/all-pages"

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(chapter.url + allPagesURLPart)
    }

    private val imgSelector = "div.content-inner.inner-page > div > img.img-responsive"

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        var pageIndex = 0

        return document.select(imgSelector).map { Page(pageIndex++, it.attr("src"), it.attr("src")) }
    }

    override fun fetchImageUrl(page: Page) = throw UnsupportedOperationException("Not used.")

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Not used.")

    // search

    private val underlyingSearchMangaPath = "/service/advanced_search"

    private val searchHeaders: Headers = headers.newBuilder()
        .set("X-Requested-With", "XMLHttpRequest")
        .build()

    private val defaultSearchParameter = linkedMapOf(
        Pair("type", "all"),
        Pair("manga-name", ""),
        Pair("author-name", ""),
        Pair("artist-name", ""),
        Pair("status", "both")
    )

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val currentSearchParameter = LinkedHashMap(defaultSearchParameter)

        var potentialGenreGroup: GenreGroup? = null

        filters.forEach {
            if (it is FormBodyFilter) it.addToFormParameters(currentSearchParameter)
            if (it is GenreGroup) potentialGenreGroup = it
        }

        Log.d("devExt", "Filter size ${filters.size}")

        if (query.isNotEmpty()) {
            Log.d("devExt", "Query is not empty")
            currentSearchParameter["manga-name"] = query
        }

        val requestBodyBuilder = FormBody.Builder(Charset.forName("utf8"))

        currentSearchParameter.entries.forEach {
            requestBodyBuilder.add(it.key, it.value)
            if (it.key == "artist-name") {
                potentialGenreGroup?.run {
                    addToRequestPayload(requestBodyBuilder)
                }
            }
        }

        return POST(baseUrl + underlyingSearchMangaPath, searchHeaders, requestBodyBuilder.build())
    }

    private val searchResultSelector = "div.row"

    override fun searchMangaParse(response: Response): MangasPage {
        Log.d("devExt", "Requestbody:\n${bodyToString(response.request())}")

        val document = response.asJsoup()

        return MangasPage(document.select(searchResultSelector).map {
            mangaFromMangaListElement(it)
        }, false)
    }

    // for debugging purposes
    // TODO remove
    private fun bodyToString(request: Request): String? {
        return try {
            val copy = request.newBuilder().build()
            val buffer = Buffer()
            copy.body()!!.writeTo(buffer)
            buffer.readUtf8()
        } catch (e: IOException) {
            "did not work"
        }
    }

    // filters
    override fun getFilterList() = FilterList(
        TypeFilter(),
        AuthorText(),
        ArtistText(),
        StatusFilter(),
        getGenreGroupFilterOrPlaceholder()
    )

    private class TypeFilter : FormBodySelectFilter("Type", "type", arrayOf(
        Pair("japanese", "Japanese Manga"),
        Pair("korean", "Korean Manhwa"),
        Pair("chinese", "Chinese Manhua"),
        Pair("all", "All")
    ), 3)

    private class AuthorText : Filter.Text("Author"), FormBodyFilter {
        override fun addToFormParameters(formParameters: MutableMap<String, String>) {
            formParameters["author-name"] = state
        }
    }

    private class ArtistText : Filter.Text("Artist"), FormBodyFilter {
        override fun addToFormParameters(formParameters: MutableMap<String, String>) {
            formParameters["artist-name"] = state
        }
    }

    private class StatusFilter : FormBodySelectFilter("Status", "status", arrayOf(
        Pair("ongoing", "Ongoing"),
        Pair("completed", "Completed"),
        Pair("both", "Both")
    ), 2)

    private fun getGenreGroupFilterOrPlaceholder(): Filter<*> {
        return when (val potentialGenreGroup = callForGenreGroup()) {
            null -> GenreNotAvailable()
            else -> potentialGenreGroup
        }
    }

    private class GenreNotAvailable : Filter.Text("genreNotAvailable", "Reset for genre filter")

    private class GenreFilter(val payloadParam: String, displayName: String) : Filter.CheckBox(displayName)

    private class GenreGroup(generatedGenreList: List<GenreFilter>) : Filter.Group<GenreFilter>("Genres", generatedGenreList) {
        fun addToRequestPayload(formBodyBuilder: FormBody.Builder) {
            state.filter { it.state }
                .forEach { formBodyBuilder.add("include[]", it.payloadParam) }
        }
    }

    private var genreFiltersContent: List<Pair<String, String>>? = null
    private var genreFilterContentFrom: Long? = null

    private fun contentUpToDate(compareTimestamp: Long?): Boolean =
        (genreFiltersContent == null || compareTimestamp == null || (System.currentTimeMillis() - compareTimestamp < 15 * 60 * 1000))

    private fun callForGenreGroup(): GenreGroup? {
        fun genreContentListToGenreGroup(genreFiltersContent: List<Pair<String, String>>) =
            GenreGroup(genreFiltersContent.map { singleGenreContent ->
                GenreFilter(singleGenreContent.first, singleGenreContent.second) })

        val genreGroupFromCacheContent = genreFiltersContent?.let { genreList ->
            genreContentListToGenreGroup(genreList)
        }

        return if (genreGroupFromCacheContent != null && contentUpToDate(genreFilterContentFrom)) {
            genreGroupFromCacheContent
        } else {
            generateFilterContent()?.let {
                genreContentListToGenreGroup(it)
            }
        }
    }

    private val advancedSearchPagePath = "/advanced-search"

    private fun generateFilterContent(): List<Pair<String, String>>? {
        fun responseToGenreFilter(genreResponse: Response): List<Pair<String, String>> {
            val document = genreResponse.asJsoup()

            return document.select("ul.manga-cat > li").map {
                Pair(it.select("span.fa").first().attr("data-id"), it.ownText())
            }
        }

        val genreResponse = client
            .newCall(GET(url = baseUrl + advancedSearchPagePath, cache = CacheControl.FORCE_CACHE))
            .execute()

        return if (genreResponse.code() == 200 && contentUpToDate(genreResponse.receivedResponseAtMillis())) {
            responseToGenreFilter(genreResponse)
        } else {
            client.newCall(GET(url = baseUrl + advancedSearchPagePath, cache = CacheControl.FORCE_NETWORK)).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    throw e
                }

                override fun onResponse(call: Call, response: Response) {
                    genreFilterContentFrom = response.receivedResponseAtMillis()
                    genreFiltersContent = responseToGenreFilter(response)
                }
            })
            null
        }
    }

    /**
     * Class that creates a select filter. Each entry in the dropdown has a name and a display name.
     */
    // vals: <name, display>
    private open class FormBodySelectFilter(
        displayName: String,
        val payloadParam: String,
        val vals: Array<Pair<String, String>>,
        defaultValue: Int = 0
    ) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue), FormBodyFilter {
        override fun addToFormParameters(formParameters: MutableMap<String, String>) {
            formParameters[payloadParam] = vals[state].first
        }
    }

    /**
     * Represents a filter that is able to modify a Payload.
     */
    private interface FormBodyFilter {
        fun addToFormParameters(formParameters: MutableMap<String, String>)
    }

    // common
    private fun mangaFromMangaListElement(mangaListElement: Element): SManga {
        val titleElement = mangaListElement.select("div.col-md-4 > a").first()
        return mangaFromMangaTitleElement(titleElement)
    }

    private fun mangaFromMangaTitleElement(mangaTitleElement: Element): SManga = SManga.create().apply {
        this.title = mangaTitleElement.attr("title")
        this.setUrlWithoutDomain(mangaTitleElement.attr("href"))
        this.thumbnail_url = mangaTitleElement.select("img").first().attr("src")
    }
}
