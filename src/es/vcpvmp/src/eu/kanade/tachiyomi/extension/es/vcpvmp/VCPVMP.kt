package eu.kanade.tachiyomi.extension.es.vcpvmp

import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.nodes.*
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource

open class VCPVMP(override val name: String, override val baseUrl: String) : ParsedHttpSource() {

    override val lang = "es"

    override val supportsLatest: Boolean = false

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesSelector() = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("Not used")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/page/$page", headers)

    override fun popularMangaSelector() = "div#posts div.gallery"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.select("a.cover").first().let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.select("div.caption").text()
            thumbnail_url = it.select("img").attr(getCover(it.toString().contains("noscript"))) // baseUrl.contains("vercomicsporno")
        }
    }

    private fun getCover(arg: Boolean): String {
        when (arg) {
            true -> return "data-lazy-src"
            false -> return "src"
        }
    }

    override fun popularMangaNextPageSelector() = "ul.pagination > li.active + li"

    override fun mangaDetailsParse(document: Document) =  SManga.create().apply {
        document.select("div#catag").let {
            genre = document.select("div#tagsin > a[rel=tag]").joinToString(", ") {
                it.text()
            }
            artist = ""
            description = ""
            status = SManga.UNKNOWN
        }
    }

    override fun chapterListSelector() = "body"

    override fun chapterFromElement(element: Element)  = SChapter.create().apply {
        name = "One shot"
        setUrlWithoutDomain(element.baseUri())
    }


    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url)

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        document.select("div#posts img[data-lazy-src]").forEach {
            add(Page(size, document.baseUri(), it.attr("data-lazy-src")))
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = HttpUrl.parse(baseUrl)!!.newBuilder()

        url.addPathSegments("page")
        url.addPathSegments(page.toString())
        url.addQueryParameter("s", query)

        filters.forEach { filter ->
            when (filter) {
                is Genre -> {
                    when (filter.toUriPart().isNotEmpty()) {
                        true -> {
                            url = HttpUrl.parse(baseUrl)!!.newBuilder()

                            url.addPathSegments("etiqueta")
                            url.addPathSegments(filter.toUriPart())

                            url.addPathSegments("page")
                            url.addPathSegments(page.toString())
                        }
                    }
                }
                is ComicList -> {
                    filter.state
                        .filter { comic -> comic.state }
                        .forEach {
                            comic -> url.addQueryParameter("cat", comic.id)
                        }
                }
            }
        }

        return GET(url.build().toString(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    private class Comic(name: String, val id: String) : Filter.CheckBox(name)

    private class ComicList(genres: List<Comic>) : Filter.Group<Comic>("Filtrar por categoría", genres)

    override fun getFilterList() = FilterList(
        Genre(),
        Filter.Separator(),
        ComicList(getComicList())
    )

    // Array.from(document.querySelectorAll('div.tagcloud a.tag-cloud-link')).map(a => `Pair("${a.innerText}", "${a.href.replace('https://vermangasporno.com/genero/', '')}")`).join(',\n')
    // from https://vercomicsporno.com/
    private class Genre : UriPartFilter("Etiquetas", arrayOf(
        Pair("Ver todos", ""),
        Pair("Anales", "anales"),
        Pair("Culonas", "culonas"),
        Pair("Doujins", "doujins"),
        Pair("Full Color", "full-color"),
        Pair("Furry", "furry"),
        Pair("Incesto", "incesto"),
        Pair("Lesbianas", "lesbianas"),
        Pair("Madre Hijo", "madre-hijo"),
        Pair("Mamadas", "mamadas"),
        Pair("Manga Hentai", "manga-hentai"),
        Pair("Milfs", "milfs"),
        Pair("Milftoon", "milftoon-comics"),
        Pair("Orgias", "orgias"),
        Pair("Parodias Porno", "parodias-porno"),
        Pair("Rubias", "rubias"),
        Pair("Series De Tv", "series-de-tv"),
        Pair("Tetonas", "tetonas"),
        Pair("Trios", "trios"),
        Pair("Videojuegos", "videojuegos"),
        Pair("Yuri", "yuri-2")
    ))

    // Array.from(document.querySelectorAll('form select#cat option.level-0')).map(a => `Genre("${a.innerText}", "${a.value}")`).join(',\n')
    // from https://vercomicsporno.com/
    private fun getComicList() = listOf(
        Comic("69", "1905"),
        Comic("8muses", "856"),
        Comic("Aarokira", "2668"),
        Comic("Adam 00", "1698"),
        Comic("Aquarina", "2727"),
        Comic("Arabatos", "1780"),
        Comic("Aroma Sensei", "2663"),
        Comic("Art of jaguar", "167"),
        Comic("Black and White", "361"),
        Comic("Blackadder", "83"),
        Comic("Blargsnarf", "2728"),
        Comic("Bnouait", "2706"),
        Comic("Buena trama", "2579"),
        Comic("Buru", "2736"),
        Comic("Cagri", "2751"),
        Comic("Catfightcentral", "2691"),
        Comic("cherry-gig", "2679"),
        Comic("ClaraLaine", "2697"),
        Comic("Clasicos", "2553"),
        Comic("Cobatsart", "2729"),
        Comic("Comics 3D", "1910"),
        Comic("Comics porno", "6"),
        Comic("Comics porno mexicano", "511"),
        Comic("Comics porno Simpsons", "94"),
        Comic("Comics XXX", "119"),
        Comic("CrazyDad3d", "2657"),
        Comic("Croc", "1684"),
        Comic("Dasan", "2692"),
        Comic("Doxy", "2698"),
        Comic("Drawnsex", "9"),
        Comic("DrCockula", "2708"),
        Comic("Ecchi Kimochiii", "1948"),
        Comic("EcchiFactor 2.0", "1911"),
        Comic("Erotibot", "2711"),
        Comic("Felsala", "2138"),
        Comic("Fixxxer", "2737"),
        Comic("Fotonovelas", "320"),
        Comic("Freehand", "400"),
        Comic("FrozenParody", "1766"),
        Comic("Futanari", "1732"),
        Comic("Ghettoyouth", "2730"),
        Comic("Gilftoon", "2619"),
        Comic("Glassfish", "84"),
        Comic("Gundam888", "2681"),
        Comic("Hagfish", "2599"),
        Comic("Hary Draws", "2752"),
        Comic("Hioshiru", "2673"),
        Comic("InCase", "1927"),
        Comic("Incesto 3d", "310"),
        Comic("Incognitymous", "2693"),
        Comic("Interracial", "364"),
        Comic("Inuyuru", "2699"),
        Comic("isakishi", "2721"),
        Comic("Jadenkaiba", "2064"),
        Comic("Jay Marvel", "2135"),
        Comic("Jay Naylor", "174"),
        Comic("Jhon Person", "135"),
        Comic("Jkr", "718"),
        Comic("JLullaby", "2680"),
        Comic("K/DA", "2667"),
        Comic("Kadath", "2700"),
        Comic("Kaos", "1994"),
        Comic("Kimmundo", "2669"),
        Comic("Kinkymation", "2733"),
        Comic("Kirtu", "107"),
        Comic("Kogeikun", "2738"),
        Comic("l", "1"),
        Comic("Lady Astaroth", "2722"),
        Comic("LawyBunne", "2744"),
        Comic("Lemon Font", "2750"),
        Comic("Lewdua", "2734"),
        Comic("Locofuria", "2578"),
        Comic("Manaworld", "85"),
        Comic("Manaworldcomics", "164"),
        Comic("Manga hentai", "152"),
        Comic("Matemi", "2741"),
        Comic("Meesh", "2740"),
        Comic("Melkor Mancin", "169"),
        Comic("Metal Owl", "2694"),
        Comic("Milffur", "140"),
        Comic("Milftoon", "13"),
        Comic("Milftoonbeach", "1712"),
        Comic("Mojarte", "1417"),
        Comic("Mr. E Comics", "2562"),
        Comic("MrPotatoParty", "2712"),
        Comic("Myster Box", "2670"),
        Comic("Neal D Anderson]", "2725"),
        Comic("nicekotatsu", "2749"),
        Comic("Noticias", "1664"),
        Comic("O-tako Studios", "2723"),
        Comic("Pack Imagenes", "654"),
        Comic("Palcomix", "48"),
        Comic("Pandora Box", "155"),
        Comic("Pegasus Smith", "2682"),
        Comic("Pokeporn", "1914"),
        Comic("Poonet", "648"),
        Comic("Prism Girls", "1926"),
        Comic("Privados", "858"),
        Comic("RanmaBooks", "1974"),
        Comic("Razter", "2689"),
        Comic("recreator 2099", "2671"),
        Comic("RickFoxxx", "1411"),
        Comic("RobCiveCat", "2739"),
        Comic("SaMelodii", "2701"),
        Comic("SanePerson", "2683"),
        Comic("Shadbase", "1713"),
        Comic("Sheela", "2690"),
        Comic("Sillygirl", "2129"),
        Comic("Sin Porno", "2266"),
        Comic("SunsetRiders7", "1705"),
        Comic("Taboolicious", "88"),
        Comic("Tekuho", "2601"),
        Comic("thegoodbadart", "2684"),
        Comic("TheOtherHalf", "2666"),
        Comic("Totempole", "2746"),
        Comic("Tracy Scops", "2648"),
        Comic("Transmorpher DDS", "2672"),
        Comic("Uzonegro", "2695"),
        Comic("VentZX", "2575"),
        Comic("Vercomicsporno", "1376"),
        Comic("Wherewolf", "2685"),
        Comic("Witchking00", "1815"),
        Comic("Xamrock", "2686"),
        Comic("Xierra099", "2702"),
        Comic("Xkit", "2703"),
        Comic("Y3df", "86"),
        Comic("Zzomp", "252")
    )

    open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }


}
