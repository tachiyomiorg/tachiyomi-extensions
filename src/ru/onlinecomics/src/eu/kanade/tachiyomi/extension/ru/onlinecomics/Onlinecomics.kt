package eu.kanade.tachiyomi.extension.ru.onlinecomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

class Onlinecomics: ParsedHttpSource() {
    override fun searchMangaFromElement(element: Element): SManga {
        throw Exception("Not used")
    }

    override fun searchMangaNextPageSelector(): String? {
        throw Exception("Not used")
    }

    override fun latestUpdatesSelector(): String {
        throw Exception("Not used")
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        throw Exception("Not used")
    }

    override fun latestUpdatesNextPageSelector(): String? {
        throw Exception("Not used")
    }

    override fun latestUpdatesRequest(page: Int): Request {
        throw Exception("Not used")
    }

    override val name = "Onlinecomics"

    override val baseUrl = "http://onlinecomics.su"

    override val lang = "ru"

    override val supportsLatest = false

    override fun popularMangaRequest(page: Int): Request =
            GET("$baseUrl/online-reading/comicsonline/$page")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "https://yandex.ru/search/site/?html=1&topdoc=http://jurnalu.ru/?searchid=2158851&" +
                "text=$query&web=0&encoding=&tld=ru&htmlcss=1.x&updatehash=true&searchid=2158851&" +
                "clid=&text=batman&web=0&p=&surl=&constraintid=&date=&within=&from_day=&" +
                "from_month=&from_year=&to_day=&to_month=&to_year=&available=&priceLow=&priceHigh=&" +
                "categoryId=&l10n=ru&callback=jQuery"
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }


        val hasNextPage = popularMangaNextPageSelector().let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas.distinctBy { it -> it.url }, hasNextPage)
    }

    override fun popularMangaSelector() = "div.PrewLine"

    override fun searchMangaSelector() = ""

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = baseUrl + element.select("a img").first()?.attr("src")
        element.select("div.Gname a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun popularMangaNextPageSelector() = "a.RRL"

    override fun searchMangaParse(response: Response): MangasPage {
        val body = response.body()!!.string()
                .replace("\\/", "/")
                .replace("\\-", "-")
                .removePrefix("jQuery(Ya.Site.Results.triggerResultsDelivered('")
                .removeSuffix("'))")
        val document = Jsoup.parse(body)

        val mangaList = mutableListOf<SManga>()
        document.select(".b-serp-item__title-link").forEach { element ->
            val manga = SManga.create()
            val url = element.select("a.b-serp-item__title-link").first()
                    .attr("href").removePrefix("http://www.jurnalu.ru")
            if (url.contains("/manga/")) return@forEach
            val splits = url.split('/')
            val newUrl = splits
                    .joinToString("/", limit = 4, truncated = "")
                    .removeSuffix("/")
            manga.setUrlWithoutDomain(newUrl)
            manga.title =  element.select("yass-span").text().split('/')[0]
            mangaList.add(manga)
        }

        val result = mangaList.filter { it.url.isNotEmpty() }.distinctBy { it.url }
        return MangasPage(result, false)

    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.ops").first()

        val manga = SManga.create()
        if (infoElement.select("strong").size > 5) {
            manga.author = (infoElement.select("strong")[6].text() +
                    infoElement.select("strong")[7].text())
                    .removePrefix("Издательство: ")
                    .split(" /")[0]

            val text = (document.select("p[align]")[0].parentNode() as Element).text()
            val begin = if (text.contains("Жанр:")) {
                "Жанр:"
            } else {
                "Жанры:"
            }
            val end = "Тип:"
            val tempString = text.removeRange(0, text.indexOf(begin)+begin.length)
            manga.genre = tempString.removeRange(tempString.indexOf(end), tempString.length).trim()

            manga.status = parseStatus(infoElement.text())
            manga.description = infoElement.select("div").last().text()
                    .split("Только у нас на сайте вы можете ")[0]

            manga.thumbnail_url = baseUrl + infoElement.select("div img")[1].attr("src")
        } else {
            manga.description = document.select(".remark").text()
        }
        return manga

    }

    private fun parseStatus(element: String): Int = when {
        element.contains("завершен") -> SManga.COMPLETED
        element.contains("продолжается") -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapterList = mutableListOf<SChapter>()
        chapterList.addAll(document.select(chapterListSelector()).map { chapterFromElement(it) })


        val lastPage: String? = document.select("div.navigationG .C").last()?.text()
        if (lastPage != null) {
            (2..lastPage.toInt()).forEach { i ->
                val get = GET("${response.request().url()}/$i",
                        headers = headers)
                chapterList.addAll(
                        client.newCall(get).execute().asJsoup().select(chapterListSelector()).map {
                            chapterFromElement(it)
                        }
                )
            }
        }
        return chapterList
    }

    override fun chapterListSelector() = "div.MagListLine"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = element.select("div.date").first()?.text()?.let {
            SimpleDateFormat("dd.MM.yyyy", Locale.US).parse(it).time
        } ?: 0
        return chapter
    }

    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        val basic = Regex("""#\s([0-9]+)""")
        when {
            basic.containsMatchIn(chapter.name) -> {
                basic.find(chapter.name)?.let {
                    chapter.chapter_number = it.groups[1]?.value!!.toFloat()
                }
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val navDocument = document.select(".navigation").first()
        val url = document.location()
        val pages = mutableListOf<Page>()
        navDocument.select("option").forEachIndexed { index, element ->
            pages.add(Page(index, "$url/${element.attr("value")}"))
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String = document.select("img").first().attr("src")

    override fun imageRequest(page: Page): Request {
        val imgHeader = Headers.Builder().apply {
            add("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64)")
            add("Referer", baseUrl)
        }.build()
        return GET(page.imageUrl!!, imgHeader)
    }
}