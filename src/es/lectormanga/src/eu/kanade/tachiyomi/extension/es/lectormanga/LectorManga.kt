package eu.kanade.tachiyomi.extension.es.lectormanga

import android.app.Application
import android.content.SharedPreferences
import eu.kanade.tachiyomi.lib.ratelimit.SpecificHostRateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

class LectorManga : ConfigurableSource, ParsedHttpSource() {

    override val name = "LectorManga"

    override val baseUrl = "https://lectormanga.com"

    override val lang = "es"

    override val supportsLatest = true

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/80.0.3987.132 Safari/537.36"

    override fun headersBuilder(): Headers.Builder {
        return Headers.Builder()
            .add("User-Agent", userAgent)
            .add("Referer", "$baseUrl/")
    }

    private val imageCDNUrl = "https://img1.followmanga.com"

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val webRateLimitInterceptor = SpecificHostRateLimitInterceptor(
        baseUrl.toHttpUrlOrNull()!!,
        preferences.getString(WEB_RATELIMIT_PREF, WEB_RATELIMIT_PREF_DEFAULT_VALUE)!!.toInt(),
        60
    )

    private val imageCDNRateLimitInterceptor = SpecificHostRateLimitInterceptor(
        imageCDNUrl.toHttpUrlOrNull()!!,
        preferences.getString(IMAGE_CDN_RATELIMIT_PREF, IMAGE_CDN_RATELIMIT_PREF_DEFAULT_VALUE)!!.toInt(),
        60
    )

    override val client: OkHttpClient = network.client.newBuilder()
        .addNetworkInterceptor(webRateLimitInterceptor)
        .addNetworkInterceptor(imageCDNRateLimitInterceptor)
        .build()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/library?order_item=likes_count&order_dir=desc&type=&filter_by=title&page=$page", headers)

    override fun popularMangaNextPageSelector() = ".pagination .page-item:not(.disabled) a[rel='next']"

    override fun popularMangaSelector() = ".col-6 .card"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        title = element.select("a").text()
        thumbnail_url = element.select("img").attr("src")
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/library?order_item=creation&order_dir=desc&page=$page", headers)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/library".toHttpUrlOrNull()!!.newBuilder()

        url.addQueryParameter("title", query)
        url.addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is Types -> {
                    url.addQueryParameter("type", filter.toUriPart())
                }
                is Demography -> {
                    url.addQueryParameter("demography", filter.toUriPart())
                }
                is FilterBy -> {
                    url.addQueryParameter("filter_by", filter.toUriPart())
                }
                is SortBy -> {
                    if (filter.state != null) {
                        url.addQueryParameter("order_item", SORTABLES[filter.state!!.index].second)
                        url.addQueryParameter(
                            "order_dir",
                            if (filter.state!!.ascending) { "asc" } else { "desc" }
                        )
                    }
                }
                is WebcomicFilter -> {
                    url.addQueryParameter(
                        "webcomic",
                        when (filter.state) {
                            Filter.TriState.STATE_INCLUDE -> "true"
                            Filter.TriState.STATE_EXCLUDE -> "false"
                            else -> ""
                        }
                    )
                }
                is FourKomaFilter -> {
                    url.addQueryParameter(
                        "yonkoma",
                        when (filter.state) {
                            Filter.TriState.STATE_INCLUDE -> "true"
                            Filter.TriState.STATE_EXCLUDE -> "false"
                            else -> ""
                        }
                    )
                }
                is AmateurFilter -> {
                    url.addQueryParameter(
                        "amateur",
                        when (filter.state) {
                            Filter.TriState.STATE_INCLUDE -> "true"
                            Filter.TriState.STATE_EXCLUDE -> "false"
                            else -> ""
                        }
                    )
                }
                is EroticFilter -> {
                    url.addQueryParameter(
                        "erotic",
                        when (filter.state) {
                            Filter.TriState.STATE_INCLUDE -> "true"
                            Filter.TriState.STATE_EXCLUDE -> "false"
                            else -> ""
                        }
                    )
                }
                is GenreList -> {
                    filter.state
                        .filter { genre -> genre.state }
                        .forEach { genre -> url.addQueryParameter("genders[]", genre.id) }
                }
            }
        }

        return GET(url.build().toString(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("h1:has(small)").text()
        genre = document.select("a.py-2").joinToString(", ") {
            it.text()
        }
        description = document.select(".col-12.mt-2")?.text()
        status = parseStatus(document.select(".status-publishing")?.text().orEmpty())
        thumbnail_url = document.select(".text-center img.img-fluid").attr("src")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Publicándose") -> SManga.ONGOING
        status.contains("Finalizado") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> = mutableListOf<SChapter>().apply {
        val document = response.asJsoup()

        // One-shot
        if (document.select("#chapters").isEmpty()) {
            return document.select(oneShotChapterListSelector()).map { oneShotChapterFromElement(it) }
        }

        // Regular list of chapters
        val chapterNames = document.select("#chapters h4.text-truncate")
        val chapterNumbers = chapterNames.map { it.text().substringAfter("Capítulo").substringBefore("|").trim().toFloat() }
        val chapterInfos = document.select("#chapters .chapter-list")

        chapterNames.forEachIndexed { index, _ ->
            val scanlator = chapterInfos[index].select("li")
            if (getScanlatorPref()) {
                scanlator.forEach { add(regularChapterFromElement(chapterNames[index].text(), it, chapterNumbers[index])) }
            } else {
                scanlator.last { add(regularChapterFromElement(chapterNames[index].text(), it, chapterNumbers[index])) }
            }
        }
    }

    override fun chapterListSelector() = throw UnsupportedOperationException("Not used")

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    private fun oneShotChapterListSelector() = "div.chapter-list-element > ul.list-group li.list-group-item"

    private fun oneShotChapterFromElement(element: Element) = SChapter.create().apply {
        url = element.select("div.row > .text-right > a").attr("href")
        name = "One Shot"
        scanlator = element.select("div.col-12.col-sm-12.col-md-4.text-truncate span")?.text()
        date_upload = element.select("span.badge.badge-primary.p-2").first()?.text()?.let { parseChapterDate(it) }
            ?: 0
    }

    private fun regularChapterFromElement(chapterName: String, info: Element, number: Float) = SChapter.create().apply {
        url = info.select("div.row > .text-right > a").attr("href")
        name = chapterName
        scanlator = info.select("div.col-12.col-sm-12.col-md-4.text-truncate span")?.text()
        date_upload = info.select("span.badge.badge-primary.p-2").first()?.text()?.let {
            parseChapterDate(it)
        } ?: 0
        chapter_number = number
    }

    private fun parseChapterDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .parse(date)?.time ?: 0
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val currentUrl = client.newCall(GET(chapter.url, headers)).execute().asJsoup().body().baseUri()

        // Get /cascade instead of /paginate to get all pages at once
        val newUrl = if (getPageMethodPref() == "cascade" && currentUrl.contains("paginated")) {
            currentUrl.substringBefore("paginated") + "cascade"
        } else if (getPageMethodPref() == "paginated" && currentUrl.contains("cascade")) {
            currentUrl.substringBefore("cascade") + "paginated"
        } else currentUrl

        return GET(newUrl, headers)
    }

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        if (getPageMethodPref() == "cascade") {
            document.select("div.viewer-container img").forEach {
                add(
                    Page(
                        size,
                        "",
                        it.let {
                            if (it.hasAttr("data-src"))
                                it.attr("abs:data-src") else it.attr("abs:src")
                        }
                    )
                )
            }
        } else {
            val pageList = document.select("#viewer-pages-select").first().select("option").map { it.attr("value").toInt() }
            val url = document.baseUri().substringBefore("/paginated") // Accounts for url ending in number "/paginated/1"
            pageList.forEach {
                add(Page(it, "$url/paginated/$it"))
            }
        }
    }

    // Note: At this moment (13/07/2020) it's necessary to make the image request without headers to prevent 403.
    override fun imageRequest(page: Page) = GET(page.imageUrl!!)

    override fun imageUrlParse(document: Document): String = document.select("img.viewer-image").attr("src")

    private fun searchMangaByIdRequest(id: String) = GET("$baseUrl/$MANGA_URL_CHUNK/$id", headers)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            val realQuery = query.removePrefix(PREFIX_ID_SEARCH)

            client.newCall(searchMangaByIdRequest(realQuery))
                .asObservableSuccess()
                .map { response ->
                    val details = mangaDetailsParse(response)
                    details.url = "/$MANGA_URL_CHUNK/$realQuery"
                    MangasPage(listOf(details), false)
                }
        } else {
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { response ->
                    searchMangaParse(response)
                }
        }
    }

    private class Types : UriPartFilter(
        "Filtrar por tipo",
        arrayOf(
            Pair("Ver todos", ""),
            Pair("Manga", "manga"),
            Pair("Manhua", "manhua"),
            Pair("Manhwa", "manhwa"),
            Pair("Novela", "novel"),
            Pair("One shot", "one_shot"),
            Pair("Doujinshi", "doujinshi"),
            Pair("Oel", "oel")
        )
    )

    private class Demography : UriPartFilter(
        "Filtrar por demografía",
        arrayOf(
            Pair("Ver todas", ""),
            Pair("Seinen", "seinen"),
            Pair("Shoujo", "shoujo"),
            Pair("Shounen", "shounen"),
            Pair("Josei", "josei"),
            Pair("Kodomo", "kodomo")
        )
    )

    private class FilterBy : UriPartFilter(
        "Campo de orden",
        arrayOf(
            Pair("Título", "title"),
            Pair("Autor", "author"),
            Pair("Compañia", "company")
        )
    )

    class SortBy : Filter.Sort(
        "Ordenar por",
        SORTABLES.map { it.first }.toTypedArray(),
        Selection(0, false)
    )

    private class WebcomicFilter : Filter.TriState("Webcomic")

    private class FourKomaFilter : Filter.TriState("Yonkoma")

    private class AmateurFilter : Filter.TriState("Amateur")

    private class EroticFilter : Filter.TriState("Erótico")

    private class Genre(name: String, val id: String) : Filter.CheckBox(name)

    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Filtrar por géneros", genres)

    override fun getFilterList() = FilterList(
        Types(),
        Demography(),
        Filter.Separator(),
        FilterBy(),
        SortBy(),
        Filter.Separator(),
        WebcomicFilter(),
        FourKomaFilter(),
        AmateurFilter(),
        EroticFilter(),
        GenreList(getGenreList())
    )

    // Array.from(document.querySelectorAll('#advancedSearch .custom-checkbox'))
    // .map(a => `Genre("${a.querySelector('label').innerText}", "${a.querySelector('input').value}")`).join(',\n')
    // on https://lectormanga.com/library
    // Last revision 13/07/2020
    private fun getGenreList() = listOf(
        Genre("Acción", "1"),
        Genre("Aventura", "2"),
        Genre("Comedia", "3"),
        Genre("Drama", "4"),
        Genre("Recuentos de la vida", "5"),
        Genre("Ecchi", "6"),
        Genre("Fantasia", "7"),
        Genre("Magia", "8"),
        Genre("Sobrenatural", "9"),
        Genre("Horror", "10"),
        Genre("Misterio", "11"),
        Genre("Psicológico", "12"),
        Genre("Romance", "13"),
        Genre("Ciencia Ficción", "14"),
        Genre("Thriller", "15"),
        Genre("Deporte", "16"),
        Genre("Girls Love", "17"),
        Genre("Boys Love", "18"),
        Genre("Harem", "19"),
        Genre("Mecha", "20"),
        Genre("Supervivencia", "21"),
        Genre("Reencarnación", "22"),
        Genre("Gore", "23"),
        Genre("Apocalíptico", "24"),
        Genre("Tragedia", "25"),
        Genre("Vida Escolar", "26"),
        Genre("Historia", "27"),
        Genre("Militar", "28"),
        Genre("Policiaco", "29"),
        Genre("Crimen", "30"),
        Genre("Superpoderes", "31"),
        Genre("Vampiros", "32"),
        Genre("Artes Marciales", "33"),
        Genre("Samurái", "34"),
        Genre("Género Bender", "35"),
        Genre("Realidad Virtual", "36"),
        Genre("Ciberpunk", "37"),
        Genre("Musica", "38"),
        Genre("Parodia", "39"),
        Genre("Animación", "40"),
        Genre("Demonios", "41"),
        Genre("Familia", "42"),
        Genre("Extranjero", "43"),
        Genre("Niños", "44"),
        Genre("Realidad", "45"),
        Genre("Telenovela", "46"),
        Genre("Guerra", "47"),
        Genre("Oeste", "48")
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {

        val scanlatorPref = androidx.preference.CheckBoxPreference(screen.context).apply {
            key = SCANLATOR_PREF
            title = SCANLATOR_PREF_TITLE
            summary = SCANLATOR_PREF_SUMMARY
            setDefaultValue(SCANLATOR_PREF_DEFAULT_VALUE)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit().putBoolean(SCANLATOR_PREF, checkValue).commit()
            }
        }

        val pageMethodPref = androidx.preference.ListPreference(screen.context).apply {
            key = PAGE_METHOD_PREF
            title = PAGE_METHOD_PREF_TITLE
            entries = arrayOf("Cascada", "Páginado")
            entryValues = arrayOf("cascade", "paginated")
            summary = PAGE_METHOD_PREF_SUMMARY
            setDefaultValue(PAGE_METHOD_PREF_DEFAULT_VALUE)

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val setting = preferences.edit().putString(PAGE_METHOD_PREF, newValue as String).commit()
                    setting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        // Rate limit
        val apiRateLimitPreference = androidx.preference.ListPreference(screen.context).apply {
            key = WEB_RATELIMIT_PREF
            title = WEB_RATELIMIT_PREF_TITLE
            summary = WEB_RATELIMIT_PREF_SUMMARY
            entries = ENTRIES_ARRAY
            entryValues = ENTRIES_ARRAY

            setDefaultValue(WEB_RATELIMIT_PREF_DEFAULT_VALUE)
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val setting = preferences.edit().putString(WEB_RATELIMIT_PREF, newValue as String).commit()
                    setting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        val imgCDNRateLimitPreference = androidx.preference.ListPreference(screen.context).apply {
            key = IMAGE_CDN_RATELIMIT_PREF
            title = IMAGE_CDN_RATELIMIT_PREF_TITLE
            summary = IMAGE_CDN_RATELIMIT_PREF_SUMMARY
            entries = ENTRIES_ARRAY
            entryValues = ENTRIES_ARRAY

            setDefaultValue(IMAGE_CDN_RATELIMIT_PREF_DEFAULT_VALUE)
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val setting = preferences.edit().putString(IMAGE_CDN_RATELIMIT_PREF, newValue as String).commit()
                    setting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        screen.addPreference(scanlatorPref)
        screen.addPreference(pageMethodPref)
        screen.addPreference(apiRateLimitPreference)
        screen.addPreference(imgCDNRateLimitPreference)
    }

    private fun getScanlatorPref(): Boolean = preferences.getBoolean(SCANLATOR_PREF, SCANLATOR_PREF_DEFAULT_VALUE)

    private fun getPageMethodPref() = preferences.getString(PAGE_METHOD_PREF, PAGE_METHOD_PREF_DEFAULT_VALUE)

    companion object {
        private const val SCANLATOR_PREF = "scanlatorPref"
        private const val SCANLATOR_PREF_TITLE = "Mostrar todos los scanlator"
        private const val SCANLATOR_PREF_SUMMARY = "Se mostraran capítulos repetidos pero con diferentes Scanlators"
        private const val SCANLATOR_PREF_DEFAULT_VALUE = true

        private const val PAGE_METHOD_PREF = "pageMethodPref"
        private const val PAGE_METHOD_PREF_TITLE = "Método para descargar imágenes"
        private const val PAGE_METHOD_PREF_SUMMARY = "Previene ser banneado por el servidor cuando se usa la configuración \"Cascada\" ya que esta reduce la cantidad de solicitudes.\nPuedes usar \"Páginado\" cuando las imágenes no carguen usando \"Cascada\".\nConfiguración actual: %s"
        private const val PAGE_METHOD_PREF_DEFAULT_VALUE = "cascade"

        private const val WEB_RATELIMIT_PREF = "webRatelimitPreference"
        // Ratelimit permits per second for main website
        private const val WEB_RATELIMIT_PREF_TITLE = "Ratelimit por minuto para el sitio web"
        // This value affects network request amount to TMO url. Lower this value may reduce the chance to get HTTP 429 error, but loading speed will be slower too. Tachiyomi restart required. \nCurrent value: %s
        private const val WEB_RATELIMIT_PREF_SUMMARY = "Este valor afecta la cantidad de solicitudes de red a la URL de TMO. Reducir este valor puede disminuir la posibilidad de obtener un error HTTP 429, pero la velocidad de descarga será más lenta. Se requiere reiniciar Tachiyomi. \nValor actual: %s"
        private const val WEB_RATELIMIT_PREF_DEFAULT_VALUE = "10"

        private const val IMAGE_CDN_RATELIMIT_PREF = "imgCDNRatelimitPreference"
        // Ratelimit permits per second for image CDN
        private const val IMAGE_CDN_RATELIMIT_PREF_TITLE = "Ratelimit por minuto para descarga de imágenes"
        // This value affects network request amount for loading image. Lower this value may reduce the chance to get error when loading image, but loading speed will be slower too. Tachiyomi restart required. \nCurrent value: %s
        private const val IMAGE_CDN_RATELIMIT_PREF_SUMMARY = "Este valor afecta la cantidad de solicitudes de red para descargar imágenes. Reducir este valor puede disminuir errores al cargar imagenes, pero la velocidad de descarga será más lenta. Se requiere reiniciar Tachiyomi. \nValor actual: %s"
        private const val IMAGE_CDN_RATELIMIT_PREF_DEFAULT_VALUE = "10"

        private val ENTRIES_ARRAY = (1..10).map { i -> i.toString() }.toTypedArray()

        const val PREFIX_ID_SEARCH = "id:"
        const val MANGA_URL_CHUNK = "gotobook"

        private val SORTABLES = listOf(
            Pair("Me gusta", "likes_count"),
            Pair("Alfabético", "alphabetically"),
            Pair("Puntuación", "score"),
            Pair("Creación", "creation"),
            Pair("Fecha estreno", "release_date")
        )
    }
}
