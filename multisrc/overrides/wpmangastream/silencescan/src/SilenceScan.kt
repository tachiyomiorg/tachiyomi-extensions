package eu.kanade.tachiyomi.extension.pt.silencescan

import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class SilenceScan : WPMangaStream(
    "Silence Scan",
    "https://silencescan.net",
    "pt-BR",
    SimpleDateFormat("MMMM dd, yyyy", Locale("pt", "BR"))
) {
    private val rateLimitInterceptor = RateLimitInterceptor(4)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoEl = document.select("div.bigcontent, div.animefull").first()

        author = infoEl.select("b:contains(Autor) + span").text()
        artist = infoEl.select("b:contains(Artista) + span").text()
        status = parseStatus(infoEl.select("div.imptdt:contains(Status) i").text())
        description = infoEl.select("h2:contains(Sinopse) + div p").joinToString("\n") { it.text() }
        genre = infoEl.select("b:contains(Gêneros) + span a").joinToString { it.text() }
        thumbnail_url = infoEl.select("div.thumb img").imgAttr()
    }

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.select("span.chapternum").text()
        scanlator = this@SilenceScan.name
        date_upload = element.select("span.chapterdate").firstOrNull()?.text()
            ?.let { parseChapterDate(it) } ?: 0
        setUrlWithoutDomain(element.select("div.eph-num > a").attr("href"))
    }

    override fun pageListParse(document: Document): List<Page> {
        val chapterObj = document.select("script:containsData(ts_reader)").first()
            .data()
            .substringAfter("run(")
            .substringBeforeLast(");")
            .let { JSON_PARSER.parse(it) }
            .obj

        if (chapterObj["sources"].array.size() == 0) {
            return emptyList()
        }

        val firstServerAvailable = chapterObj["sources"].array[0].obj

        return firstServerAvailable["images"].array
            .mapIndexed { i, pageUrl -> Page(i, "", pageUrl.string) }
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
        private val JSON_PARSER by lazy { JsonParser() }
    }
}
