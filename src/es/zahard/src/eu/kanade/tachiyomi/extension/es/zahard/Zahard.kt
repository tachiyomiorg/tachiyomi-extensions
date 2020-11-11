package eu.kanade.tachiyomi.extension.es.zahard

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class Zahard : ParsedHttpSource() {

    override val name = "Zahard"
    override val baseUrl = "https://zahard.top"
    override val lang = "es"
    override val supportsLatest = true

    // common

    private fun mangaFromElement(element: Element): SManga =
        SManga.create().apply {
            url = element.select("a").first().attr("href")
            title = element.select("h6").text().trim()
            thumbnail_url = element.select("img").attr("src")
        }

    // poplular manga

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/biblioteca?page=$page", headers)

    override fun popularMangaSelector() = "div.col-6.col-md-3.p-1"

    override fun popularMangaNextPageSelector() = "a[rel=next]"

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)

    // latest manga
    // refer to: https://github.com/inorichi/tachiyomi-extensions/issues/4847

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return client.newCall(latestUpdatesRequest(page))
            .asObservableSuccess()
            .map { response ->
                latestUpdatesParse(response, page)
            }
    }

    val mangasLoaded = mutableListOf<SManga>()

    override fun latestUpdatesParse(response: Response): MangasPage = throw Exception("Not Used")

    fun latestUpdatesParse(response: Response, page: Int): MangasPage {
        val document = response.asJsoup()

        val potentialMangas = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }.distinctBy { it.title }

        // remve duplicates globaly
        if (page == 1)
            mangasLoaded.clear()
        var mangas = mutableListOf<SManga>()
        potentialMangas.forEach { manga ->
            var isDuplicate = false
            for (i in 0 until mangasLoaded.size) {
                if (manga.title == mangasLoaded.get(i).title) {
                    isDuplicate = true
                    break
                }
            }

            if (!isDuplicate) {
                mangasLoaded.add(manga)
                mangas.add(manga)
            }
        }

        // extract manga urls from chapters
        mangas = mangas.map { manga ->
            manga.apply {
                url = client.newCall(GET(url, headers)).execute().asJsoup().select("body > div.container.mb-3.mibg.rounded.px-4.py-2 > div:nth-child(2) > div > a")[0].attr("href")
            }
        }.toMutableList()

        // if is empty must pass something or we will get cut off
        // ref: tachiyomi/app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/browse/Pager.kt:27
        if (mangas.isEmpty()) {
            mangas.add(potentialMangas.get(0))
        }

        val hasNextPage = latestUpdatesNextPageSelector().let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/fastpass?page=$page", headers)

    override fun latestUpdatesSelector() = "div.col-6.col-md-3.p-1"

    override fun latestUpdatesNextPageSelector() = "a[rel=next]"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create().apply {
            url = element.select("a").first().attr("href")
            title = element.select("h6").text().trim().substringBefore(" episodio")
            thumbnail_url = element.select("img").attr("src")
        }

        return manga
    }

    // search manga

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            throw Exception("Source does not support search")
        } else {
            val uri = Uri.parse("$baseUrl/").buildUpon()
            // Append uri filters
            filters.forEach {
                if (it is UriFilter)
                    it.addToUri(uri)
            }
            uri.appendQueryParameter("page", "$page")
            return GET(uri.toString(), headers)
        }
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element) = mangaFromElement(element)

    // chapter list

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListSelector() = "a.list-group-item"

    override fun chapterFromElement(element: Element): SChapter =
        SChapter.create().apply {
            url = element.attr("href")
            name = element.ownText().trim() + " [" + element.select("span").text().trim() + "]"

            // general pattern: "Chapter <number in float> - <manga name>"
            chapter_number = element.ownText().split(" ")[1].toFloat()
        }

    // manga details

    override fun mangaDetailsRequest(manga: SManga) = GET(manga.url, headers)

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.select("h2").text().trim()
        manga.description = document.select("p[style=margin: 5px]").text().trim()
        manga.thumbnail_url = document.select("div.text-center img").first().attr("src")
        val glist = document.select("div.container.mb-3.mibg.rounded.px-4.py-2 a[href*=genero]").map { it.text().trim() }
        manga.genre = glist.joinToString(", ")
        return manga
    }

    // page list

    override fun pageListRequest(chapter: SChapter) = GET(chapter.url, headers)

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("img.img-fluid")?.forEach {
            pages.add(Page(pages.size, "", it.attr("src")))
        }

        return pages
    }

    // image url

    override fun imageUrlParse(document: Document) = throw Exception("Not Used")

    // filters

    /**
     * Represents a filter that is able to modify a URI.
     */
    private interface UriFilter {
        fun addToUri(uri: Uri.Builder)
    }

    private open class UriSelectFilter(
        displayName: String,
        val uriParam: String,
        val vals: Array<Pair<String, String>>,
        val firstIsUnspecified: Boolean = true,
        defaultValue: Int = 0
    ) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if (state != 0 || !firstIsUnspecified)
                uri.appendPath(uriParam)
                    .appendPath(vals[state].first)
        }
    }

    private class TypeFilter : UriSelectFilter(
        "Type",
        "biblioteca",
        arrayOf(
            Pair("all", "All"),
            Pair("manga", "Manga"),
            Pair("manhwa", "Manhwa"),
            Pair("manhua", "Manhua")
        )
    )

    private class GenreFilter : UriSelectFilter(
        "Genre",
        "genero",
        arrayOf(
            Pair("all", "ALL"),
            Pair("accion", "Acción"),
            Pair("aventura", "Aventura"),
            Pair("comedia", "Comedia"),
            Pair("drama", "Drama"),
            Pair("recuentos-de-la-vida", "Recuentos de la vida"),
            Pair("ecchi", "Ecchi"),
            Pair("fantasia", "Fantasia"),
            Pair("magia", "Magia"),
            Pair("sobrenatural", "Sobrenatural"),
            Pair("horror", "Horror"),
            Pair("misterio", "Misterio"),
            Pair("psicologico", "Psicológico"),
            Pair("romance", "Romance"),
            Pair("ciencia-ficcion", "Ciencia Ficción"),
            Pair("thriller", "Thriller"),
            Pair("deporte", "Deporte"),
            Pair("girls-love", "Girls Love"),
            Pair("boys-love", "Boys Love"),
            Pair("harem", "Harem"),
            Pair("mecha", "Mecha"),
            Pair("supervivencia", "Supervivencia"),
            Pair("reencarnacion", "Reencarnación"),
            Pair("gore", "Gore"),
            Pair("apocaliptico", "Apocalíptico"),
            Pair("tragedia", "Tragedia"),
            Pair("vida-escolar", "Vida Escolar"),
            Pair("historia", "Historia"),
            Pair("militar", "Militar"),
            Pair("policiaco", "Policiaco"),
            Pair("crimen", "Crimen"),
            Pair("superpoderes", "Superpoderes"),
            Pair("vampiros", "Vampiros"),
            Pair("artes-marciales", "Artes Marciales"),
            Pair("samurai", "Samurái"),
            Pair("genero-bender", "Género Bender"),
            Pair("realidad-virtual", "Realidad Virtual"),
            Pair("ciberpunk", "Ciberpunk"),
            Pair("musica", "Musica"),
            Pair("parodia", "Parodia"),
            Pair("animacion", "Animación"),
            Pair("demonios", "Demonios"),
            Pair("familia", "Familia"),
            Pair("extranjero", "Extranjero"),
            Pair("ni%C3%B1os", "Niños"),
            Pair("realidad", "Realidad"),
            Pair("telenovela", "Telenovela"),
            Pair("guerra", "Guerra"),
            Pair("oeste", "Oeste"),
            Pair("hentai", "hentai"),
            Pair("Comics", "Comics")
        )
    )

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Filters are ignored if using text search."),
        Filter.Header("Only one filter can be used at a time."),
        TypeFilter(),
        GenreFilter()
    )
}
