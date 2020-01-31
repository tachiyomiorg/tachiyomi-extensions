package eu.kanade.tachiyomi.extension.ru.comx

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ComX : ParsedHttpSource() {
    override val name = "Com-x"

    override val baseUrl = "https://com-x.life"

    override val lang = "ru"

    override val supportsLatest = true

    private val userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:72.0) Gecko/20100101 Firefox/72.0" // in case of change regenerate antibot cookie

    override val client = super.client.newBuilder()
        .addNetworkInterceptor(RateLimitInterceptor(4))
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: MutableList<Cookie>) {}
            override fun loadForRequest(url: HttpUrl): MutableList<Cookie> {
                return ArrayList<Cookie>().apply {
                    add(Cookie.Builder()
                        .domain("com-x.life")
                        .path("/")
                        .name("antibot")
                        .value("e28a31fb6bcbc2858bdf53fac455d54a")  // avoid - https://antibot.cloud/. Change cookie if userAgent changes
                        .build())
                }
            }

        })
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", userAgent)
        .add("Referer", baseUrl)

    override fun popularMangaSelector() = "div.shortstory1"

    override fun latestUpdatesSelector() = "ul.last-comix li"

    override fun popularMangaRequest(page: Int): Request =
              GET("$baseUrl/comix/page/$page/", headers)

    override fun latestUpdatesRequest(page: Int): Request =
            GET(baseUrl, headers)

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = baseUrl + element.select("img").first().attr("src")
        element.select("div.info-poster1 a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = baseUrl + element.select("img").first().attr("src")
        element.select("a.comix-last-title").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }


    override fun popularMangaNextPageSelector() = "div.nextprev:last-child"

    override fun latestUpdatesNextPageSelector(): Nothing? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return POST("$baseUrl/comix/",
                body = FormBody.Builder()
                        .add("do", "search")
                        .add("story", query)
                        .add("subaction", "search")
                        .build(),
                headers = headers
        )
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    // max 200 results
    override fun searchMangaNextPageSelector(): Nothing? = null

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.maincont").first()

        val manga = SManga.create()
        manga.author = infoElement.select("p:eq(2)").text().removePrefix("Издатель: ")
        manga.genre = infoElement.select("p:eq(3)").text()
                .removePrefix("Жанр: ")

        manga.status = parseStatus(infoElement.select("p:eq(4)").text()
                .removePrefix("Статус: "))

        val text = infoElement.select("*").text()
        if (!text.contains("Добавить описание на комикс")) {
            val fromRemove = "Отслеживать"
            val toRemove = "Читать комикс"
            val desc = text.removeRange(0,  text.indexOf(fromRemove)+fromRemove.length)
            manga.description = desc.removeRange(desc.indexOf(toRemove)+toRemove.length, desc.length)
        }

        val src = infoElement.select("img").attr("src")
        if (src.contains(baseUrl)) {
            manga.thumbnail_url = src
        } else {
            manga.thumbnail_url = baseUrl + src
        }
        return manga
    }

    private fun parseStatus(element: String): Int = when {
        element.contains("Продолжается") -> SManga.ONGOING
        element.contains("Завершён") ||
                element.contains("Лимитка") ||
                element.contains("Ван шот") ||
                element.contains("Графический роман") -> SManga.COMPLETED

        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "li[id^=cmx-]"

    private fun chapterResponseParse(document: Document) : List<SChapter> {
        return document.select(chapterListSelector()).map { chapterFromElement(it) }
    }

    private fun chapterPageListParse(document: Document) : List<String> {
        return document.select("span[class=\"\"]").map { it -> it.text() }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val id = response.request().url().toString().removePrefix("$baseUrl/").split('-')[0]

        val list = mutableListOf<SChapter>()
        list += chapterResponseParse(document)

        val pages = chapterPageListParse(document).distinct()

        for (page in pages) {
            val post = POST("$baseUrl/engine/mods/comix/listPages.php",
                    body = FormBody.Builder()
                            .add("newsid", id)
                            .add("page", page)
                            .build(),
                    headers = headers)

            list += chapterResponseParse(client.newCall(post).execute().asJsoup())
        }

        return list
    }

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()
        val urlText = urlElement.text()
        val chapter = SChapter.create()
        chapter.name = urlText.split('/')[0] // Remove english part of name
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.date_upload = 0
        return chapter
    }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body()!!.string()
        val beginTag = "comix_images=["
        val beginIndex = html.indexOf(beginTag)
        val endTag = "], comix_link='"
        val endIndex = html.indexOf(endTag, beginIndex)
        val comixIndex = html.indexOf("', page=", endIndex)

        val link = html.substring(endIndex + endTag.length, comixIndex)
        val urls: List<String> = html.substring(beginIndex + beginTag.length, endIndex)
                .split(',')


        val pages = mutableListOf<Page>()
        for (i in urls.indices) {
            pages.add(Page(i, "", link+(urls[i].removeSurrounding("'"))))
        }
        return pages
    }

    override fun pageListParse(document: Document): List<Page> {
        throw Exception("Not used")
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!, headers)
    }
}
