package eu.kanade.tachiyomi.extension.es.tmohentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class TMOHentai : ParsedHttpSource() {

    override val name = "TMOHentai"

    override val baseUrl = "https://tmohentai.com"

    override val lang = "es"

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/section/all?view=list&page=$page&order=popularity&order-dir=desc&search[searchText]=&search[searchBy]=name&type=all", headers)

    override fun popularMangaSelector() = "table > tbody > tr[data-toggle=popover]"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.select("tr").let {
            title = it.attr("data-title")
            thumbnail_url = it.attr("data-content").substringAfter("src=\"").substringBeforeLast("\"")
            setUrlWithoutDomain(it.select("td.text-left > a").attr("href"))
        }
    }

    override fun popularMangaNextPageSelector() = "a[rel=next]"

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/section/all?view=list&page=$page&order=publication_date&order-dir=desc&search[searchText]=&search[searchBy]=name&type=all", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val parsedInformation = document.select("div.row > div.panel.panel-primary").text()
        val authorAndArtist = parsedInformation.substringAfter("Groups").substringBefore("Magazines").trim()

        thumbnail_url = document.select("img.content-thumbnail-cover").attr("src")
        author = authorAndArtist
        artist = authorAndArtist
        description = "Sin descripción"
        status = SManga.UNKNOWN
        genre = parsedInformation.substringAfter("Genders").substringBefore("Tags").trim().split(" ").joinToString {
            it
        }
    }

    override fun chapterListSelector() = "div#app > div.container"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val parsedInformation = element.select("div.row > div.panel.panel-primary").text()

        name = element.select("h3.truncate").text()
        scanlator = parsedInformation.substringAfter("By").substringBefore("Language").trim()
        setUrlWithoutDomain(element.select("a.pull-right.btn.btn-primary").attr("href"))
        // date_upload = no date in the web
    }

    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url.substringBefore("/paginated") + "/cascade", headers) // "/cascade" to get all images

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        document.select("div#content-images > div.row > div.col-xs-12.text-center > img.content-image")?.forEach {
            add(Page(size, "", baseUrl + it.attr("data-original")))
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/section/all?view=list")!!.newBuilder()

        url.addQueryParameter("search[searchText]", query)
        url.addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is Types -> {
                    url.addQueryParameter("type", filter.toUriPart())
                }
                is GenreList -> {
                    filter.state
                            .filter { genre -> genre.state }
                            .forEach { genre -> url.addQueryParameter("genders[]", genre.id) }
                }
                is FilterBy -> {
                    url.addQueryParameter("search[searchBy]", filter.toUriPart())
                }
                is SortBy -> {
                    if (filter.state != null) {
                        url.addQueryParameter("order", SORTABLES[filter.state!!.index].second)
                        url.addQueryParameter(
                            "order-dir",
                            if (filter.state!!.ascending) { "asc" } else { "desc" }
                        )
                    }
                }
            }
        }

        return GET(url.build().toString(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    private class Genre(name: String, val id: String) : Filter.CheckBox(name)

    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Géneros", genres)

    override fun getFilterList() = FilterList(
        Types(),
        Filter.Separator(),
        FilterBy(),
        SortBy(),
        Filter.Separator(),
        GenreList(getGenreList())
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
            Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class Types : UriPartFilter("Filtrar por tipo", arrayOf(
            Pair("Ver todos", "all"),
            Pair("Manga", "hentai"),
            Pair("Light Hentai", "light-hentai"),
            Pair("Doujinshi", "doujinshi"),
            Pair("One-shot", "one-shot"),
            Pair("Other", "otro")
    ))

    private class FilterBy : UriPartFilter("Campo de orden", arrayOf(
        Pair("Nombre", "name"),
        Pair("Artista", "artist"),
        Pair("Revista", "magazine"),
        Pair("Tag", "tag")
    ))

    class SortBy : Filter.Sort(
        "Ordenar por",
        SORTABLES.map { it.first }.toTypedArray(),
        Selection(2, false)
    )

    // Array.from(document.querySelectorAll('#advancedSearch .list-group .list-group-item'))
    // .map(a => `Genre("${a.querySelector('span').innerText.replace(' ', '')}", "${a.querySelector('input').value}")`).join(',\n')
    // https://tmohentai.com/section/hentai
    private fun getGenreList() = listOf(
            Genre("Romance", "1"),
            Genre("Fantasy", "2"),
            Genre("Comedy", "3"),
            Genre("Parody", "4"),
            Genre("Student", "5"),
            Genre("Adventure", "6"),
            Genre("Milf", "7"),
            Genre("Orgy", "8"),
            Genre("Big Breasts", "9"),
            Genre("Bondage", "10"),
            Genre("Tentacles", "11"),
            Genre("Incest", "12"),
            Genre("Ahegao", "13"),
            Genre("Bestiality", "14"),
            Genre("Futanari", "15"),
            Genre("Rape", "16"),
            Genre("Monsters", "17"),
            Genre("Pregnant", "18"),
            Genre("Small Breast", "19"),
            Genre("Bukkake", "20"),
            Genre("Femdom", "21"),
            Genre("Fetish", "22"),
            Genre("Forced", "23"),
            Genre("3D", "24"),
            Genre("Furry", "25"),
            Genre("Adultery", "26"),
            Genre("Anal", "27"),
            Genre("FootJob", "28"),
            Genre("BlowJob", "29"),
            Genre("Toys", "30"),
            Genre("Vanilla", "31"),
            Genre("Colour", "32"),
            Genre("Uncensored", "33"),
            Genre("Netorare", "34"),
            Genre("Virgin", "35"),
            Genre("Cheating", "36"),
            Genre("Harem", "37"),
            Genre("Horror", "38"),
            Genre("Lolicon", "39"),
            Genre("Mature", "40"),
            Genre("Nympho", "41"),
            Genre("Public Sex", "42"),
            Genre("Sport", "43"),
            Genre("Domination", "44"),
            Genre("Tsundere", "45"),
            Genre("Yandere", "46")
    )

    companion object {
        private val SORTABLES = listOf(
            Pair("Alfabético", "alphabetic"),
            Pair("Creación", "publication_date"),
            Pair("Popularidad", "popularity")
        )
    }
}
