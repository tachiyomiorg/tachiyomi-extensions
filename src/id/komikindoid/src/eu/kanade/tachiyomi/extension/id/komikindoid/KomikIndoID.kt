package eu.kanade.tachiyomi.extension.id.komikindoid

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class KomikIndoID : ParsedHttpSource() {
    override val name = "KomikIndoID"
    override val baseUrl = "https://komikindo.id"
    override val lang = "id"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/daftar-komik/page/$page/?order=popular", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/daftar-komik/page/$page/?order=update", headers)
    }

    override fun popularMangaSelector() = "div.animepost"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a.next.page-numbers"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.limit img").attr("src")
        manga.title = element.select("div.tt h4").text()
        element.select("div.animposx > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
        }
        return manga
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val builtUrl = if (page == 1) "$baseUrl/daftar-komik/" else "$baseUrl/daftar-komik/page/$page/?order="
        val url = builtUrl.toHttpUrlOrNull()!!.newBuilder()
        url.addQueryParameter("title", query)
        url.addQueryParameter("page", page.toString())
        filters.forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                    url.addQueryParameter("author", filter.state)
                }
                is YearFilter -> {
                    url.addQueryParameter("yearx", filter.state)
                }
                is StatusFilter -> {
                    val status = when (filter.state) {
                        Filter.TriState.STATE_INCLUDE -> "completed"
                        Filter.TriState.STATE_EXCLUDE -> "ongoing"
                        else -> ""
                    }
                    url.addQueryParameter("status", status)
                }
                is TypeFilter -> {
                    url.addQueryParameter("type", filter.toUriPart())
                }
                is SortByFilter -> {
                    url.addQueryParameter("order", filter.toUriPart())
                }
                is GenreListFilter -> {
                    filter.state
                        .filter { it.state != Filter.TriState.STATE_IGNORE }
                        .forEach { url.addQueryParameter("genre[]", it.id) }
                }
            }
        }
        return GET(url.build().toString(), headers)
    }
    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.infoanime").first()
        val descElement = document.select("div.desc > .entry-content.entry-content-single").first()
        val sepName = infoElement.select(".infox > .spe > span:nth-child(2)").last()
        val manga = SManga.create()
        // need authorCleaner to take "pengarang:" string to remove it from author
        val authorCleaner = document.select(".infox .spe b:contains(Pengarang)").text()
        manga.author = document.select(".infox .spe span:contains(Pengarang)").text().substringAfter(authorCleaner)
        manga.artist = manga.author
        val genres = mutableListOf<String>()
        infoElement.select(".infox > .genre-info > a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        manga.genre = genres.joinToString(", ")
        manga.status = parseStatus(infoElement.select(".infox > .spe > span:nth-child(1)").text())
        manga.description = descElement.select("p").text()
        manga.thumbnail_url = document.select(".thumb > img:nth-child(1)").attr("src")
        return manga
    }

    private fun parseStatus(element: String): Int = when {
        element.toLowerCase().contains("berjalan") -> SManga.ONGOING
        element.toLowerCase().contains("tamat") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "#chapter_list li"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select(".lchx a").first()
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = element.select(".dt a").first()?.text()?.let { parseChapterDate(it) } ?: 0
        return chapter
    }

    fun parseChapterDate(date: String): Long {
        return if (date.contains("lalu")) {
            val value = date.split(' ')[0].toInt()
            when {
                "detik" in date -> Calendar.getInstance().apply {
                    add(Calendar.SECOND, value * -1)
                }.timeInMillis
                "menit" in date -> Calendar.getInstance().apply {
                    add(Calendar.MINUTE, value * -1)
                }.timeInMillis
                "jam" in date -> Calendar.getInstance().apply {
                    add(Calendar.HOUR_OF_DAY, value * -1)
                }.timeInMillis
                "hari" in date -> Calendar.getInstance().apply {
                    add(Calendar.DATE, value * -1)
                }.timeInMillis
                "minggu" in date -> Calendar.getInstance().apply {
                    add(Calendar.DATE, value * 7 * -1)
                }.timeInMillis
                "bulan" in date -> Calendar.getInstance().apply {
                    add(Calendar.MONTH, value * -1)
                }.timeInMillis
                "tahun" in date -> Calendar.getInstance().apply {
                    add(Calendar.YEAR, value * -1)
                }.timeInMillis
                else -> {
                    0L
                }
            }
        } else {
            try {
                dateFormat.parse(date)?.time ?: 0
            } catch (_: Exception) {
                0L
            }
        }
    }

    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        val basic = Regex("""Chapter\s([0-9]+)""")
        when {
            basic.containsMatchIn(chapter.name) -> {
                basic.find(chapter.name)?.let {
                    chapter.chapter_number = it.groups[1]?.value!!.toFloat()
                }
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        var i = 0
        document.select("div.imgch img").forEach { element ->
            val url = element.attr("src")
            i++
            if (url.isNotEmpty()) {
                pages.add(Page(i, "", url))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    private class AuthorFilter : Filter.Text("Author")

    private class YearFilter : Filter.Text("Year")

    private class TypeFilter : UriPartFilter(
        "Type",
        arrayOf(
            Pair("Default", ""),
            Pair("Manga", "Manga"),
            Pair("Manhwa", "Manhwa"),
            Pair("Manhua", "Manhua"),
            Pair("Comic", "Comic")
        )
    )

    private class SortByFilter : UriPartFilter(
        "Sort By",
        arrayOf(
            Pair("Default", ""),
            Pair("A-Z", "title"),
            Pair("Z-A", "titlereverse"),
            Pair("Latest Update", "update"),
            Pair("Latest Added", "latest"),
            Pair("Popular", "popular")
        )
    )

    private class StatusFilter : UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed")
        )
    )

    private class Genre(name: String, val id: String = name) : Filter.TriState(name)
    private class GenreListFilter(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        AuthorFilter(),
        YearFilter(),
        StatusFilter(),
        TypeFilter(),
        SortByFilter(),
        GenreListFilter(getGenreList())
    )

    private fun getGenreList() = listOf(
        Genre("Action", "action"),
        Genre("Adventure", "adventure"),
        Genre("Comedy", "comedy"),
        Genre("Crime", "crime"),
        Genre("Drama", "drama"),
        Genre("Fantasy", "fantasy"),
        Genre("Girls Love", "girls-love"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Magical Girls", "magical-girls"),
        Genre("Mecha", "mecha"),
        Genre("Medical", "medical"),
        Genre("Philosophical", "philosophical"),
        Genre("Psychological", "psychological"),
        Genre("Romance", "romance"),
        Genre("Sci-Fi", "sci-fi"),
        Genre("Shoujo Ai", "shoujo-ai"),
        Genre("Shounen Ai", "shounen-ai"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Sports", "sports"),
        Genre("Superhero", "superhero"),
        Genre("Thriller", "thriller"),
        Genre("Tragedy", "tragedy"),
        Genre("Wuxia", "wuxia"),
        Genre("Yuri", "yuri")
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
