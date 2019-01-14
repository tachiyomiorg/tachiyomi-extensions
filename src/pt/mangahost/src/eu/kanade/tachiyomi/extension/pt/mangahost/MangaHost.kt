package eu.kanade.tachiyomi.extension.pt.mangahost

    import eu.kanade.tachiyomi.network.GET
    import eu.kanade.tachiyomi.source.model.*
    import eu.kanade.tachiyomi.source.online.ParsedHttpSource
    import eu.kanade.tachiyomi.util.asJsoup
    import okhttp3.*
    import org.jsoup.nodes.Document
    import org.jsoup.nodes.Element
    import java.text.ParseException
    import java.text.SimpleDateFormat
    import java.util.*
    import java.util.regex.Matcher
    import java.util.regex.Pattern

class MangaHost : ParsedHttpSource() {

    override val name = "Manga Host"

    override val baseUrl = "https://mangahost1.com/"

    override val lang = "pt"

    override val supportsLatest = true

    private val langRegex: String = "( )?\\(Pt-Br\\)"

    override fun popularMangaSelector(): String = "a.pull-left"

    override fun popularMangaRequest(page: Int): Request {
        val pageStr = if (page != 1) "/$page" else ""
        return GET("$baseUrl/mangas/mais-visualizados$pageStr", headers)
    }

    override fun popularMangaFromElement(element: Element) : SManga {
        val manga = SManga.create()
        element.select("a").last().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = element.getElementsByClass("manga").attr("alt")
        }

        return manga
    }

    override fun popularMangaNextPageSelector() = ".paginador.wp-pagenavi a:contains(next)"

    override fun latestUpdatesSelector() = "div.thumbnail"

    override fun latestUpdatesRequest(page: Int): Request {
        val pageStr = if (page != 1) "/$page" else ""
        return GET("$baseUrl/mangas/novos$pageStr", headers)
}

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun latestUpdatesNextPageSelector() = "paginador.wp-pagenavi a:contains(next)"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
       return GET("$baseUrl"+"find/$query", headers)
    }

    override fun searchMangaSelector() = ".table-hover tr"

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }


    override fun searchMangaNextPageSelector() = null

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.margin-bottom-20").first()

        val manga = SManga.create()
        val author = infoElement.select("li:contains(Autor: )").text()
        manga.author = removeLabel(author)

        val artist = infoElement.select("li:contains(Desenho (Art): )").text()
        manga.artist = removeLabel(artist)

        val genre = infoElement.select("li:contains(Categoria(s): )").text()
        manga.genre = removeLabel(genre)

        manga.description = infoElement.select("div#divSpdInText").text()

        manga.status = infoElement.select("li:contains(Status: )").first()?.text().orEmpty().let { parseStatus(it) }
        manga.thumbnail_url = infoElement.select(".thumbnail").first()?.attr("src")

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

    override fun chapterListSelector() :String {
        return "section.clearfix.margin-bottom-20 ul.list_chapters li," +
                "section.clearfix.margin-bottom-20 table.table-hover.table-condensed tbody tr"
    }

    override fun chapterFromElement(element: Element): SChapter {

        val chapter: SChapter = SChapter.create()

        if (element.`is`("li")){

            val urlChapterLong = element.select("a").toString()
            val p2 = Pattern.compile("(?<=href=\\')(.+?)(?=\\')")
            val p1 = Pattern.compile("(?<=Ler Online - )(.+)(?=\\[)")
            val p3 = Pattern.compile("(?<=Adicionado em )(.+)(?=\\<)")
            val p4 = Pattern.compile("(?<=Traduzido por \\<strong\\>\t\t\t\t\t\t)(.+)(?=\t\t\t\t\\<\\/)")
            val m2 = p2.matcher(urlChapterLong)
            val m1 = p1.matcher(urlChapterLong)
            val m3 = p3.matcher(urlChapterLong)
            val m4 = p4.matcher(urlChapterLong)

            if (m2.find() && m1.find() && m3.find() && m4.find()) {
                chapter.setUrlWithoutDomain(m2.group())
                chapter.name = m1.group().toString()
                chapter.date_upload = m3.group().let { parseChapterDate(it) }
                chapter.scanlator = m4.group().toString()
            }
        }

        else {

            val urlElement = element.select("a.capitulo").first()
            chapter.setUrlWithoutDomain(element.select("a").first().attr("href"))
            chapter.name = urlElement.text()
            chapter.date_upload = element.select("td:eq(2)").text()?.let { parseChapterDate2(it) }?:0
            chapter.scanlator = element.select("td:eq(1) a").attr("title")
        }
            return chapter
    }


    private fun parseChapterDate(date: String) : Long {
        return try {
            SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).parse(date).time
        } catch (e: ParseException) {
            0L
        }
    }

    private fun parseChapterDate2(date: String) : Long {
        return try {
            SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).parse(date).time
        } catch (e: ParseException) {
            0L
        }
    }

    override fun pageListParse(document: Document): List<Page> {

        val pages = mutableListOf<Page>()
        var m : Matcher
        var p : Pattern
        val pageSize= mutableListOf<String>()
        val links = document.select("script").toString()
        document.select("div.pull-right > select.pages").first().getElementsByTag("option").forEach{
            pageSize.add(it.attr("value"))
        }

        for (i in 1 until pageSize.size+1) {
            p = Pattern.compile("(?<=\\'img_$i'\\ssrc=')(.+?)(?='\\s)")
            m = p.matcher(links)
            m.find()
            pages.add(Page(i-1, "", m.group(1)))
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""

}
