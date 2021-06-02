package eu.kanade.tachiyomi.extension.pt.silencescan

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class SilenceScan : WPMangaStream(
    "Silence Scan",
    "https://silencescan.net",
    "pt-BR",
    SimpleDateFormat("MMMM dd, yyyy", Locale("pt", "BR"))
) {

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    private val json: Json by injectLazy()

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoEl = document.select("div.bigcontent, div.animefull, div.main-info").first()

        author = infoEl.select("div.imptdt:contains(Autor) i").text()
        artist = infoEl.select("div.imptdt:contains(Artista) + i").text()
        status = parseStatus(infoEl.select("div.imptdt:contains(Status) i").text())
        description = infoEl.select("h2:contains(Sinopse) + div p:not([class])").joinToString("\n") { it.text() }
        thumbnail_url = infoEl.select("div.thumb img").imgAttr()

        val genres = infoEl.select("span.mgen a[rel]")
            .map { element -> element.text() }
            .toMutableSet()

        // add series type(manga/manhwa/manhua/other) thinggy to genre
        document.select(seriesTypeSelector).firstOrNull()?.ownText()?.let {
            if (it.isEmpty().not() && genres.contains(it).not()) {
                genres.add(it)
            }
        }

        genre = genres.toList().joinToString()

        // add alternative name to manga description
        document.select(altNameSelector).firstOrNull()?.ownText()?.let {
            if (it.isEmpty().not() && it != "N/A" && it != "-") {
                description += when {
                    description!!.isEmpty() -> altName + it
                    else -> "\n\n$altName" + it
                }
            }
        }
    }

    override val seriesTypeSelector = ".imptdt:contains(Tipo) a, a[href*=type\\=]"
    override val altName: String = "Nome alternativo: "

    override fun parseStatus(element: String?): Int = when {
        element == null -> SManga.UNKNOWN
        element.contains("em andamento", true) -> SManga.ONGOING
        element.contains("completo", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.select("span.chapternum").text()
        scanlator = this@SilenceScan.name
        date_upload = element.select("span.chapterdate").firstOrNull()?.text()
            ?.let { parseChapterDate(it) } ?: 0
        setUrlWithoutDomain(element.select("div.eph-num > a").attr("href"))
    }

    override fun getGenreList(): List<Genre> = listOf(
        Genre("4-koma", "4-koma"),
        Genre("Ação", "acao"),
        Genre("Adulto", "adulto"),
        Genre("Artes marciais", "artes-marciais"),
        Genre("Comédia", "comedia"),
        Genre("Comedy", "comedy"),
        Genre("Culinária", "culinaria"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Esporte", "esporte"),
        Genre("Fantasia", "fantasia"),
        Genre("Gore", "gore"),
        Genre("Harém", "harem"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Militar", "militar"),
        Genre("Mistério", "misterio"),
        Genre("Oneshot", "oneshot"),
        Genre("Parcialmente Dropado", "parcialmente-dropado"),
        Genre("Psicológico", "psicologico"),
        Genre("Romance", "romance"),
        Genre("School Life", "school-life"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo Ai", "shoujo-ai"),
        Genre("Shounen", "shounen"),
        Genre("Slice of life", "slice-of-life"),
        Genre("Sobrenatural", "sobrenatural"),
        Genre("Supernatural", "supernatural"),
        Genre("Tragédia", "tragedia"),
        Genre("Vida Escolar", "vida-escolar"),
        Genre("Violência sexual", "violencia-sexual"),
        Genre("Yuri", "yuri")
    )

    companion object {
        private val PORTUGUESE_LOCALE = Locale("pt", "BR")
    }
}
