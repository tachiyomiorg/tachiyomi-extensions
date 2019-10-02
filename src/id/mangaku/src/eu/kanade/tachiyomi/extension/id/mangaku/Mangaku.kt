package eu.kanade.tachiyomi.extension.id.mangaku

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Mangaku : ParsedHttpSource() {
    override val name = "Mangaku"
    override val baseUrl = "https://mangaku.in/"
    override val lang = "id"
    override val supportsLatest = true
    var searchQuery = ""

    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl + "daftar-komik-bahasa-indonesia/", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        searchQuery = query
        return GET(baseUrl + "daftar-komik-bahasa-indonesia/", headers)
    }

    override fun popularMangaSelector() = "a.screenshot"
    override fun latestUpdatesSelector() = "div.kiri_anime div.utao"
    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.attr("rel")
        manga.url = element.attr("href")
        manga.title = element.text()
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.uta div.imgu img").attr("src")

        val mangaUrl = element.select("div.uta div.luf a.series").attr("href").replace("hhtps", "http")
        manga.url = mangaUrl.replace("hhtps", "https")
        manga.title = element.select("div.uta div.luf a.series").text()
        return manga
    }

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(manga.url, headers)
    }

    @SuppressLint("DefaultLocale")
    override fun mangaDetailsParse(document: Document): SManga {
        val infoString = document.select("#abc > p > span > small").html().toString().split("<br> ")
        val manga = SManga.create()
        infoString.forEach {
            if (it.contains("</b>")) {
                val info = it.split("</b>")
                val key = info[0].replace(":", "").replace("<b>", "").trim().toLowerCase()
                val value = info[1].replace(":", "").trim()
                when (key) {
                    "genre" -> manga.genre = value.replace("–", ", ").replace("-", ", ").trim()
                    "author" -> manga.author = value
                    "artist" -> manga.artist = value
                    "sinopsis" -> manga.description = value
                }
            }
        }
        manga.status = SManga.UNKNOWN
        manga.thumbnail_url = document.select("#abc > div > span > small > a > img").attr("src")
        return manga
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET(manga.url, headers)
    }

    override fun chapterListSelector() = "div.entry > div > table > tbody > tr > td:nth-child(1) > small > div:nth-child(2) > a"
    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val chapterUrl = element.attr("href")
        chapter.url = chapterUrl
        chapter.name = element.text()
        if (chapter.name.contains("–"))
            chapter.name = chapter.name.split("–")[1].trim()
        return chapter
    }

    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        val basic = Regex("""Chapter\s([0-9]+)""")
        when {
            basic.containsMatchIn(chapter.name) -> {
                basic.find(chapter.name)?.let {
                    chapter.chapter_number = it.groups[1]?.value!!.toFloat()
                }
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(chapter.url, headers)
    }

    override fun pageListParse(document: Document): List<Page> {
        var pageList = document.select("div.entry img")

        if (pageList.isEmpty()) {
            pageList = document.select("div.entry-content img")
        }

        val pages = mutableListOf<Page>()
        var i = 0
        pageList.forEach { element ->
            val imageUrl = element.attr("src")
            i++
            if (imageUrl.isNotEmpty()) {
                pages.add(Page(i, "", imageUrl))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""
    override fun imageRequest(page: Page): Request {
        var mainUrl = baseUrl
        var imageUrl = page.imageUrl.toString()
        if (imageUrl.contains("mangaku.co")) {
            mainUrl = "https://mangaku.co"
        }
        if (imageUrl.startsWith("//")) {
            imageUrl = "https:" + imageUrl
        } else if (imageUrl.startsWith("/")) {
            imageUrl = mainUrl + imageUrl
        }
        val imgHeader = Headers.Builder().apply {
            add("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/76.0.3809.100 Safari/537.36")
            add("referer", mainUrl)
        }.build()
        return GET(imageUrl, imgHeader)
    }

    override fun popularMangaNextPageSelector() = "next"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    @SuppressLint("DefaultLocale")
    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = arrayListOf<SManga>()
        document.select(searchMangaSelector()).forEach { element ->
            val manga = popularMangaFromElement(element)
            if (manga.title.toLowerCase().contains(searchQuery.toLowerCase())) {
                mangas.add(manga)
            }
        }
        return MangasPage(mangas, false)
    }
}
