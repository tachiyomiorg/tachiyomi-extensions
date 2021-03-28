package eu.kanade.tachiyomi.extension.es.mangamx

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import android.support.v7.preference.CheckBoxPreference
import android.support.v7.preference.PreferenceScreen
import android.util.Base64
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale

open class MangaMx : ConfigurableSource, ParsedHttpSource() {

    override val name = "MangaMx"

    override val baseUrl = "https://manga-mx.com"

    override val lang = "es"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private var csrfToken = ""

    override fun popularMangaRequest(page: Int) = GET(
        "$baseUrl/directorio?genero=false" +
            "&estado=false&filtro=visitas&tipo=false&adulto=${if (hideNSFWContent()) "0" else "false"}&orden=desc&p=$page",
        headers
    )

    override fun popularMangaNextPageSelector() = ".page-item a[rel=next]"

    override fun popularMangaSelector() = "#article-div a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        thumbnail_url = element.select("img").attr("data-src")
        title = element.select("div:eq(1)").text().trim()
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        csrfToken = document.select("meta[name=csrf-token]").attr("content")

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }

        val hasNextPage = popularMangaNextPageSelector().let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/recientes?p=$page", headers)

    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    override fun latestUpdatesSelector() = "div._1bJU3"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.select("img").attr("data-src")
        element.select("div a").apply {
            title = this.text().trim()
            setUrlWithoutDomain(this.attr("href"))
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val formBody = FormBody.Builder()
                .add("buscar", query)
                .add("_token", csrfToken)
                .build()
            val searchHeaders = headers.newBuilder().add("X-Requested-With", "XMLHttpRequest")
                .add("Referer", baseUrl).build()
            return POST("$baseUrl/buscar", searchHeaders, formBody)
        } else {
            val uri = Uri.parse("$baseUrl/directorio").buildUpon()

            uri.appendQueryParameter(
                "adulto",
                if (hideNSFWContent()) { "0" } else { "1" }
            )

            for (filter in filters) {
                when (filter) {
                    is StatusFilter -> uri.appendQueryParameter(
                        filter.name.toLowerCase(Locale.ROOT),
                        statusArray[filter.state].second
                    )
                    is SortBy -> {
                        uri.appendQueryParameter("filtro", sortables[filter.state!!.index].second)
                        uri.appendQueryParameter(
                            "orden",
                            if (filter.state!!.ascending) { "asc" } else { "desc" }
                        )
                    }
                    is TypeFilter -> uri.appendQueryParameter(
                        filter.name.toLowerCase(Locale.ROOT),
                        typedArray[filter.state].second
                    )
                    is GenreFilter -> uri.appendQueryParameter(
                        "genero",
                        genresArray[filter.state].second
                    )
                }
            }
            uri.appendQueryParameter("p", page.toString())
            return GET(uri.toString(), headers)
        }
    }

    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()

    override fun searchMangaSelector(): String = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaParse(response: Response): MangasPage {
        if (!response.isSuccessful) throw Exception("Búsqueda fallida ${response.code()}")
        if ("directorio" in response.request().url().toString()) {
            val document = response.asJsoup()
            val mangas = document.select(searchMangaSelector()).map { element ->
                searchMangaFromElement(element)
            }

            val hasNextPage = searchMangaNextPageSelector()?.let { selector ->
                document.select(selector).first()
            } != null

            return MangasPage(mangas, hasNextPage)
        } else {
            val body = response.body()!!.string()
            if (body == "[]") throw Exception("Término de búsqueda demasiado corto")
            val json = JsonParser().parse(body)["mangas"].asJsonArray

            val mangas = json.map { jsonElement -> searchMangaFromJson(jsonElement) }
            val hasNextPage = false
            return MangasPage(mangas, hasNextPage)
        }
    }

    private fun searchMangaFromJson(jsonElement: JsonElement): SManga = SManga.create().apply {
        title = jsonElement["nombre"].string
        setUrlWithoutDomain(jsonElement["url"].string)
        thumbnail_url = jsonElement["img"].string.replace("/thumb", "/cover")
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = document.select("img[src*=cover]").attr("abs:src")
        manga.description = document.select("div#sinopsis").last().ownText()
        manga.author = document.select("div#info-i").text().let {
            if (it.contains("Autor", true)) {
                it.substringAfter("Autor:").substringBefore("Fecha:").trim()
            } else "N/A"
        }
        manga.artist = manga.author
        manga.genre = document.select("div#categ a").joinToString(", ") { it.text() }
        manga.status = when (document.select("span#desarrollo")?.first()?.text()) {
            "En desarrollo" -> SManga.ONGOING
            // "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        return manga
    }

    override fun chapterListSelector(): String = "div#c_list a"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.text().trim()
        setUrlWithoutDomain(element.attr("href"))
        chapter_number = element.select("span").attr("data-num").toFloat()
        date_upload = parseDate(element.select("span").attr("datetime"))
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(date)?.time ?: 0
    }

    override fun pageListParse(document: Document): List<Page> {
        val encoded = document.select("script:containsData(unicap)").firstOrNull()
            ?.data()?.substringAfter("'")?.substringBefore("'")?.reversed()
            ?: throw Exception("unicap not found")
        val drop = encoded.length % 4
        val decoded = Base64.decode(encoded.dropLast(drop), Base64.DEFAULT).toString(Charset.defaultCharset())
        val path = decoded.substringBefore("||")
        return decoded.substringAfter("[").substringBefore("]").split(",").mapIndexed { i, file ->
            Page(i, "", path + file.removeSurrounding("\""))
        }
    }

    override fun imageUrlParse(document: Document) = throw Exception("Not Used")

    override fun getFilterList() = FilterList(
        Filter.Header("NOTA: Se ignoran si se usa el buscador"),
        Filter.Separator(),
        SortBy("Ordenar por", sortables),
        StatusFilter("Estado", statusArray),
        TypeFilter("Tipo", typedArray),
        GenreFilter("Géneros", genresArray)
    )

    private class StatusFilter(name: String, values: Array<Pair<String, String>>) :
        Filter.Select<String>(name, values.map { it.first }.toTypedArray())

    private class TypeFilter(name: String, values: Array<Pair<String, String>>) :
        Filter.Select<String>(name, values.map { it.first }.toTypedArray())

    private class GenreFilter(name: String, values: Array<Pair<String, String>>) :
        Filter.Select<String>(name, values.map { it.first }.toTypedArray())

    class SortBy(name: String, values: Array<Pair<String, String>>) : Filter.Sort(
        name, values.map { it.first }.toTypedArray(),
        Selection(0, false)
    )

    private val statusArray = arrayOf(
        Pair("Estado", "false"),
        Pair("En desarrollo", "1"),
        Pair("Completo", "0")
    )

    private val typedArray = arrayOf(
        Pair("Todo", "false"),
        Pair("Mangas", "0"),
        Pair("Manhwas", "1"),
        Pair("One Shot", "2"),
        Pair("Manhuas", "3"),
        Pair("Novelas", "4")
    )

    private val sortables = arrayOf(
        Pair("Visitas", "visitas"),
        Pair("Recientes", "id"),
        Pair("Alfabético", "nombre"),
    )

    /**
     * Url: https://manga-mx.com/directorio/
     * Last check: 27/03/2021
     * JS script: Array.from(document.querySelectorAll('select[name="genero"] option'))
     * .map(a => `Pair("${a.innerText}", "${a.value}")`).join(',\n')
     */
    private val genresArray = arrayOf(
        Pair("Todos", "false"),
        Pair("Comedia", "1"),
        Pair("Drama", "2"),
        Pair("Acción", "3"),
        Pair("Escolar", "4"),
        Pair("Romance", "5"),
        Pair("Ecchi", "6"),
        Pair("Aventura", "7"),
        Pair("Shōnen", "8"),
        Pair("Shōjo", "9"),
        Pair("Deportes", "10"),
        Pair("Psicológico", "11"),
        Pair("Fantasía", "12"),
        Pair("Mecha", "13"),
        Pair("Gore", "14"),
        Pair("Yaoi", "15"),
        Pair("Yuri", "16"),
        Pair("Misterio", "17"),
        Pair("Sobrenatural", "18"),
        Pair("Seinen", "19"),
        Pair("Ficción", "20"),
        Pair("Harem", "21"),
        Pair("Webtoon", "25"),
        Pair("Histórico", "27"),
        Pair("Músical", "30"),
        Pair("Ciencia ficción", "31"),
        Pair("Shōjo-ai", "32"),
        Pair("Josei", "33"),
        Pair("Magia", "34"),
        Pair("Artes Marciales", "35"),
        Pair("Horror", "36"),
        Pair("Demonios", "37"),
        Pair("Supervivencia", "38"),
        Pair("Recuentos de la vida", "39"),
        Pair("Shōnen ai", "40"),
        Pair("Militar", "41"),
        Pair("Eroge", "42"),
        Pair("Isekai", "43")
    )

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {

        val contentPref = androidx.preference.CheckBoxPreference(screen.context).apply {
            key = CONTENT_PREF
            title = CONTENT_PREF_TITLE
            summary = CONTENT_PREF_SUMMARY
            setDefaultValue(CONTENT_PREF_DEFAULT_VALUE)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit().putBoolean(CONTENT_PREF, checkValue).commit()
            }
        }

        screen.addPreference(contentPref)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {

        val contentPref = CheckBoxPreference(screen.context).apply {
            key = CONTENT_PREF
            title = CONTENT_PREF_TITLE
            summary = CONTENT_PREF_SUMMARY
            setDefaultValue(CONTENT_PREF_DEFAULT_VALUE)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit().putBoolean(CONTENT_PREF, checkValue).commit()
            }
        }

        screen.addPreference(contentPref)
    }

    private fun hideNSFWContent(): Boolean = preferences.getBoolean(CONTENT_PREF, CONTENT_PREF_DEFAULT_VALUE)

    companion object {
        private const val CONTENT_PREF = "showNSFWContent"
        private const val CONTENT_PREF_TITLE = "Ocultar contenido +18"
        private const val CONTENT_PREF_SUMMARY = "Ocultar el contenido erótico en explorar y buscar, no funciona en los mangas recientes."
        private const val CONTENT_PREF_DEFAULT_VALUE = false
    }
}
