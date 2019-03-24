package eu.kanade.tachiyomi.extension.ru.henchan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Henchan : ParsedHttpSource() {

    override val name = "Henchan"

    override val baseUrl = "http://henchan.me"

    private val exhentaiBaseUrl = "http://exhentaidono.me"

    override val lang = "ru"

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request =
            GET("$baseUrl/mostfavorites&sort=manga?offset=${20 * (page - 1)}", headers)

    override fun latestUpdatesRequest(page: Int): Request =
            GET("$baseUrl/manga/new?offset=${20 * (page - 1)}", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/?do=search&subaction=search&story=$query"
        return GET(url, headers)
    }

    override fun popularMangaSelector() = ".content_row"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = mutableListOf<SManga>()
        document.select(searchMangaSelector()).forEach { element ->
            val manga = searchMangaFromElement(element)
            if (manga.url != "") {
                mangas.add(manga)
            }
        }

        return MangasPage(mangas, false)
    }

    private fun String.getHQThumbnail(): String = this.replace("manganew_thumbs", "showfull_retina/manga").replace("img.", "imgcover.")

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("img").first().attr("src").getHQThumbnail()
        element.select("h2 > a").first().let {
            val url = it.attr("href")
            if (url.contains("/manga/")) {
                manga.setUrlWithoutDomain(url)
                manga.title = it.text()
            } else {
                manga.url = ""
            }
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga =
            popularMangaFromElement(element)

    override fun searchMangaFromElement(element: Element): SManga =
            popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "#pagination > a:contains(Вперед)"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = throw Exception("Not Used")



    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.author = document.select(".row .item2 h2")[1].text()
        manga.genre = document.select(".sidetag > a:eq(2)").joinToString { it.text() }
        manga.description = document.select("#description").text()
        return manga
    }

    override fun chapterListRequest(manga: SManga): Request {
        val baseMangaUrl = baseUrl + manga.url
        if(manga.thumbnail_url?.isBlank() ?: return GET(baseMangaUrl.replace("/manga/", "/related/"), headers)){
            return GET(baseMangaUrl, headers)
        }else {
            return GET(baseMangaUrl.replace("/manga/", "/related/"), headers)
        }
    }

    override fun chapterListSelector() = ".related"

    override fun chapterListParse(response: Response): List<SChapter> {
        val responseUrl = response.request().url().toString()
        val document = response.asJsoup()

        if(responseUrl.contains("/manga/")){
            val chap = SChapter.create()
            chap.setUrlWithoutDomain(responseUrl.removePrefix(baseUrl))
            chap.name = document.select("a.title_top_a").text()
            chap.chapter_number = 0.0F
            chap.date_upload = 0L
            return listOf(chap)
        }

        if (document.select("#right > div:nth-child(4)").text().contains(" похожий на ")) {
            val chap = SChapter.create()
            chap.setUrlWithoutDomain(document.select("#left > div > a").attr("href"))
            chap.name = document.select("#right > div:nth-child(4)").text().split(" похожий на ")[1]
            chap.chapter_number = 0.0F
            chap.date_upload = 0L
            return listOf(chap)
        }


        val result = mutableListOf<SChapter>()
        result.addAll(document.select(chapterListSelector()).mapIndexed { index, element ->
            chapterFromElement(index, element)
        })

        var url = document.select("div#pagination_related a:contains(Вперед)").attr("href")
        while (url.isNotBlank()) {
            val get = GET(
                    "${response.request().url()}/$url",
                    headers = headers
            )
            val nextPage = client.newCall(get).execute().asJsoup()
            result.addAll(nextPage.select(chapterListSelector()).mapIndexed {
                index, element ->
                chapterFromElement(index, element)
            })

            url = nextPage.select("div#pagination_related a:contains(Вперед)").attr("href")
        }

        return result.reversed()
    }

    private fun chapterFromElement(index: Int, element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.select("h2 a").attr("href"))
        chapter.name = element.select("h2 a").attr("title")
        chapter.chapter_number = index.toFloat()
        chapter.date_upload = 0L
        return chapter
    }

    override fun chapterFromElement(element: Element): SChapter = throw Exception("Not Used")

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(exhentaiBaseUrl + chapter.url.replace("/manga/", "/online/") + "?development_access=true", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body()!!.string()
        val imgString = html.split("\"fullimg\": [").last().split("]").first()
        val resPages = mutableListOf<Page>()
        val imgs = imgString.split(",")
        imgs.forEachIndexed { index, s ->
            resPages.add(Page(index, imageUrl = s.trim('"', '\'', '[', ' ')))
        }
        return resPages
    }

    override fun imageUrlParse(document: Document) = throw Exception("Not Used")

    override fun pageListParse(document: Document): List<Page> = throw Exception("Not Used")
}
