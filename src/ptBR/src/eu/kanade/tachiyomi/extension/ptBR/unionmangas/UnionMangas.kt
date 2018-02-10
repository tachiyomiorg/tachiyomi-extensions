package eu.kanade.tachiyomi.extension.ptBR.unionmangas

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class UnionMangas : ParsedHttpSource() {

    override val name = "Union Mangás"

    override val baseUrl = "http://unionmangas.cc"

    override val lang = "pt-BR"

    override val supportsLatest = true

    // Sometimes the site it's very slow...
    override val client =
        network.client.newBuilder()
                .connectTimeout(3, TimeUnit.MINUTES)
                .readTimeout(3, TimeUnit.MINUTES)
                .writeTimeout(3, TimeUnit.MINUTES)
                .build()

    private val langRegex: String = "( )?\\(Pt-Br\\)"

    private val catalogHeaders = Headers.Builder().apply {
        add("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64)")
        add("Host", "unionmangas.cc")
        add("Referer", baseUrl)
    }.build()

    override fun popularMangaSelector(): String = "div.bloco-manga"

    override fun popularMangaRequest(page: Int): Request {
        val pageStr = if (page != 1) "/$page" else ""
        return GET("$baseUrl/mangas/visualizacoes$pageStr", catalogHeaders)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("a img").first()?.attr("src")
        element.select("a").last().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text().replace(langRegex.toRegex(), "")
        }

        return manga
    }

    override fun popularMangaNextPageSelector() = ".pagination li:contains(Next)"

    override fun latestUpdatesSelector() = "div.row[style=margin-bottom: 10px;] > div.col-md-12"

    override fun latestUpdatesRequest(page: Int): Request {
        val form = FormBody.Builder().apply {
            add("pagina", page.toString())
        }

        return POST("$baseUrl/assets/noticias.php", headers, form.build())
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun latestUpdatesNextPageSelector() = "div#linha-botao-mais"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
       return GET("$baseUrl/busca/$query/$page", headers)
    }

    override fun searchMangaSelector() = ".bloco-manga"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.thumbnail_url = element.select("a img.img-thumbnail").first().attr("src")
        element.select("a").last().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text().replace(langRegex.toRegex(), "")
        }

        return manga
    }

    override fun searchMangaNextPageSelector() = "ul.pagination li:not(.active)"

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.tamanho-bloco-perfil").first()

        val manga = SManga.create()

        val author = infoElement.select("div.row:eq(2) div.col-md-8:eq(4)").first()?.text()
        manga.author = removeLabel(author)

        val artist = infoElement.select("div.row:eq(2) div.col-md-8:eq(5)").first()?.text()
        manga.artist = removeLabel(artist)

        val genre = infoElement.select("div.row:eq(2) div.col-md-8:eq(3)").first()?.text()
        manga.genre = removeLabel(genre)

        manga.status = infoElement.select("div.row:eq(2) div.col-md-8:eq(6)").first()?.text().orEmpty().let { parseStatus(it) }

        manga.description = infoElement.select("div.row:eq(2) div.col-md-8:eq(8)").first()?.text()
        manga.thumbnail_url = infoElement.select(".img-thumbnail").first()?.attr("src")

        // Need to grab title again because the ellipsize in search.
        manga.title = infoElement.select("h2").first()!!.text().replace(langRegex.toRegex(), "")

        return manga
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ativo") -> SManga.ONGOING
        status.contains("Completo") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun removeLabel(text: String?): String {
        return text!!.substring(text!!.indexOf(":") + 1)
    }

    override fun chapterListSelector() = "div.row.lancamento-linha"

    override fun chapterFromElement(element: Element): SChapter {
        val firstColumn = element.select("div.col-md-6:eq(0)")
        val secondColumn = element.select("div.col-md-6:eq(1)")

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(firstColumn.select("a").first().attr("href"))
        chapter.name = firstColumn.select("a").first().text()
        chapter.date_upload = firstColumn.select("span").last()?.text()?.let { parseChapterDate(it) } ?: 0
        chapter.scanlator = secondColumn?.text()

        return chapter
    }

    private fun parseChapterDate(date: String) : Long {
        return try {
            SimpleDateFormat("(dd/MM/yyyy)", Locale.ENGLISH).parse(date).time
        } catch (e: ParseException) {
            0L
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = document.select("img.img-responsive.img-manga")

        return pages
                .filter { it.attr("src").contains("leitor") }
                .mapIndexed { i, element -> Page(i, "", "http://" + element.attr("src"))}
    }

    override fun imageUrlParse(document: Document) = ""
}