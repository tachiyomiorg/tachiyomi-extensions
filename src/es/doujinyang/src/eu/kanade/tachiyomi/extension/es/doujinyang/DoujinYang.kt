package eu.kanade.tachiyomi.extension.es.doujinyang

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

open class DoujinYang : ParsedHttpSource() {

    override val name = "Doujin-Yang"
    override val baseUrl = "https://doujin-yang.es"
    override val lang = "es"
    override val supportsLatest = true

    override fun popularMangaSelector() = "article[id=item]"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun searchMangaSelector() = "article[id=item]"
    override fun chapterListSelector() = throw Exception("Not Used")

    override fun popularMangaNextPageSelector() = "a[href*=directorio]:containsOwn(Última)"
    override fun latestUpdatesNextPageSelector() = "nav#paginacion a:contains(Última)"
    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/directorio/?orden=visitas&p=$page", headers)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/reciente/doujin?p=$page", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = if (query.isNotBlank()) {
            Uri.parse(baseUrl).buildUpon()
                .appendQueryParameter("s", query)
        } else {
            val uri = Uri.parse("$baseUrl/directorio").buildUpon()
            // Append uri filters
            filters.forEach {
                if (it is UriFilter)
                    it.addToUri(uri)
            }
            uri.appendQueryParameter("p", page.toString())
        }
        return GET(uri.toString(), headers)
    }

    // override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl + manga.url, headers)
    // override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url, headers)

    override fun chapterListRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector())
            .distinctBy { it.select("a").first().attr("abs:href") }
            .map { latestUpdatesFromElement(it) }
        val hasNextPage = latestUpdatesNextPageSelector().let { selector ->
            document.select(selector).first()
        } != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.select("a").first().attr("abs:href"))
        manga.title = element.select("a").first().text().trim()
        return manga
    }

    override fun searchMangaFromElement(element: Element) = mangaFromElement(element)

    private fun mangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.select("a").first().attr("abs:href"))
        manga.title = element.select("h2").text().trim()
        // manga.thumbnail_url = "https:" + element.select("img").attr("src")
        manga.thumbnail_url = element.select("img").attr("abs:src")
        return manga
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.asJsoup().select("div#c_list a").map { element ->
            SChapter.create().apply {
                name = element.select("h3").text()
                setUrlWithoutDomain(element.attr("abs:href"))
            }
        }
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd kk:mm:ss", Locale.US).parse(date)?.time ?: 0
    }

    override fun chapterFromElement(element: Element) = throw Exception("Not used")

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = document.select("img[src*=cover]").attr("abs:src")
        manga.description = document.select("div[id=sinopsis]").last().ownText()
        manga.author = document.select("div[id=info-i]").text().let {
            if (it.contains("Autor", true)) {
                it.substringAfter("Autor:").substringBefore("Fecha:").trim()
            } else "N/A"
        }
        manga.artist = manga.author
        val glist = document.select("div[id=categ] a[href*=genero]").map { it.text() }
        manga.genre = glist.joinToString(", ")
        manga.status = when (document.select("span[id=desarrollo]")?.first()?.text()) {
            "En desarrollo" -> SManga.ONGOING
            // "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        return manga
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return POST(
            baseUrl + chapter.url,
            headersBuilder().add("Content-Type", "application/x-www-form-urlencoded").build(),
            RequestBody.create(null, "info")
        )
    }

    override fun pageListParse(response: Response): List<Page> {
        return response.body()!!.string().substringAfter(",[").substringBefore("]")
            .replace(Regex("""[\\"]"""), "").split(",").let { list ->
                val path = "https:" + list[0]
                list.drop(1).mapIndexed { i, img -> Page(i, "", path + img) }
            }
    }

    override fun pageListParse(document: Document) = throw Exception("Not Used")
    override fun imageUrlParse(document: Document) = throw Exception("Not Used")

    override fun getFilterList() = FilterList(
        Filter.Header("NOTA: ¡La búsqueda de títulos no funciona!"), // "Title search not working"
        Filter.Separator(),
        GenreFilter(),
        LetterFilter(),
        StatusFilter(),
        SortFilter()
    )

    class GenreFilter : UriPartFilter(
        "Género", "genero", arrayOf(
            Pair("all", "All"),
            Pair("1", "Ahegao"),
            Pair("379", "Alien"),
            Pair("2", "Anal"),
            Pair("490", "Android18"),
            Pair("717", "Angel"),
            Pair("633", "Asphyxiation"),
            Pair("237", "Bandages"),
            Pair("77", "Bbw"),
            Pair("143", "Bdsm"),
            Pair("23", "Blackmail"),
            Pair("113", "Blindfold"),
            Pair("24", "Blowjob"),
            Pair("166", "Blowjobface"),
            Pair("25", "Body Writing"),
            Pair("314", "Bodymodification"),
            Pair("806", "Bodystocking"),
            Pair("366", "Bodysuit"),
            Pair("419", "Bodyswap"),
            Pair("325", "Bodywriting"),
            Pair("5", "Bondage"),
            Pair("51", "Bukkake"),
            Pair("410", "Catgirl"),
            Pair("61", "Chastitybelt"),
            Pair("78", "Cheating"),
            Pair("293", "Cheerleader"),
            Pair("62", "Collar"),
            Pair("120", "Compilation"),
            Pair("74", "Condom"),
            Pair("63", "Corruption"),
            Pair("191", "Corset"),
            Pair("234", "Cosplaying"),
            Pair("389", "Cowgirl"),
            Pair("256", "Crossdressing"),
            Pair("179", "Crotchtattoo"),
            Pair("689", "Crown"),
            Pair("733", "Cumflation"),
            Pair("385", "Cumswap"),
            Pair("251", "Cunnilingus"),
            Pair("75", "Darkskin"),
            Pair("180", "Daughter"),
            Pair("52", "Deepthroat"),
            Pair("28", "Defloration"),
            Pair("198", "Demon"),
            Pair("145", "Demongirl"),
            Pair("64", "Drugs"),
            Pair("95", "Drunk"),
            Pair("462", "Femalesonly"),
            Pair("82", "Femdom"),
            Pair("139", "Ffmthreesome"),
            Pair("823", "Fftthreesome"),
            Pair("55", "Full Color"),
            Pair("181", "Fullbodytattoo"),
            Pair("203", "Fullcensorship"),
            Pair("111", "Fullcolor"),
            Pair("114", "Gag"),
            Pair("3", "Glasses"),
            Pair("515", "Gloryhole"),
            Pair("116", "Humanpet"),
            Pair("32", "Humiliation"),
            Pair("147", "Latex"),
            Pair("12", "Maid"),
            Pair("4", "Milf"),
            Pair("245", "Military"),
            Pair("414", "Milking"),
            Pair("34", "Mind Control"),
            Pair("68", "Mindbreak"),
            Pair("124", "Mindcontrol"),
            Pair("645", "Nun"),
            Pair("312", "Nurse"),
            Pair("272", "Robot"),
            Pair("7", "Romance"),
            Pair("761", "Sundress"),
            Pair("412", "Tailplug"),
            Pair("253", "Tutor"),
            Pair("259", "Twins"),
            Pair("207", "Twintails"),
            Pair("840", "Valkyrie"),
            Pair("530", "Vampire"),
            Pair("16", "Yuri"),
            Pair("273", "Zombie")
        )
    )

    class LetterFilter : UriPartFilter(
        "Letra", "letra", arrayOf(
            Pair("all", "All"),
            Pair("a", "A"),
            Pair("b", "B"),
            Pair("c", "C"),
            Pair("d", "D"),
            Pair("e", "E"),
            Pair("f", "F"),
            Pair("g", "G"),
            Pair("h", "H"),
            Pair("i", "I"),
            Pair("j", "J"),
            Pair("k", "K"),
            Pair("l", "L"),
            Pair("m", "M"),
            Pair("n", "N"),
            Pair("o", "O"),
            Pair("p", "P"),
            Pair("q", "Q"),
            Pair("r", "R"),
            Pair("s", "S"),
            Pair("t", "T"),
            Pair("u", "U"),
            Pair("v", "V"),
            Pair("w", "W"),
            Pair("x", "X"),
            Pair("y", "Y"),
            Pair("z", "Z")
        )
    )

    class StatusFilter : UriPartFilter(
        "Estado", "estado", arrayOf(
            Pair("all", "All"), Pair("1", "En desarrollo"), Pair("0", "Finalizado")
        )
    )

    class SortFilter : UriPartFilterreq(
        "Sort", "orden", arrayOf(
            Pair("visitas", "Visitas"),
            Pair("desc", "Descendente"),
            Pair("asc", "Ascendente"),
            Pair("lanzamiento", "Lanzamiento"),
            Pair("nombre", "Nombre")
        )
    )

    /**
     * Class that creates a select filter. Each entry in the dropdown has a name and a display name.
     * If an entry is selected it is appended as a query parameter onto the end of the URI.
     * If `firstIsUnspecified` is set to true, if the first entry is selected, nothing will be appended on the the URI.
     */
    // vals: <name, display>
    open class UriPartFilter(
        displayName: String,
        private val uriParam: String,
        private val vals: Array<Pair<String, String>>,
        private val firstIsUnspecified: Boolean = true,
        defaultValue: Int = 0
    ) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue),
        UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if (state != 0 || !firstIsUnspecified)
                uri.appendQueryParameter(uriParam, vals[state].first)
        }
    }

    open class UriPartFilterreq(
        displayName: String,
        private val uriParam: String,
        private val vals: Array<Pair<String, String>>
    ) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray()), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            uri.appendQueryParameter(uriParam, vals[state].first)
        }
    }

    /**
     * Represents a filter that is able to modify a URI.
     */
    private interface UriFilter {
        fun addToUri(uri: Uri.Builder)
    }
}
