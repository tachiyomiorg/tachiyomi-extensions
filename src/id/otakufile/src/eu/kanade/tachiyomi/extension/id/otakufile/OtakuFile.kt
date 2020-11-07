package eu.kanade.tachiyomi.extension.id.otakufile

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class OtakuFile : ParsedHttpSource() {

    override val name = "Otaku File"
    override val baseUrl = "https://otakufile.com"
    override val lang = "id"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/komik-populer", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/", headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = latestUpdatesRequest(page)

    override fun popularMangaSelector() = "div.allgreen.genrelst > ul > li"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.anipost > div.thumb > a.zeebuy > img").attr("src")
        element.select("div.anipost > div.left > a.zeebuy").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.select("h2").text()
        }
        return manga
    }

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a.next.page-numbers"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    @SuppressLint("DefaultLocale")
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        val infoElement = document.select("div.animeinfo > div.lm > div.imgdesc > div.listinfo > ul > li")

        infoElement.forEach {
            val key = it.select("b").text().toLowerCase()
            val value = it.text().replace(":", "").trim()
            when {
                key.contains("author") -> {
                    if (value.contains(',')) {
                        manga.author = value.split(',')[0].trim()
                        manga.artist = value.split(',')[1].trim()
                    } else {
                        manga.author = value
                        manga.artist = value
                    }
                }
                key.contains("genres") -> {
                    val genres = mutableListOf<String>()
                    it.select("a").forEach { elmt ->
                        val genre = elmt.text().trim()
                        genres.add(genre)
                    }
                    manga.genre = genres.joinToString(", ")
                }
            }
        }
        manga.status = SManga.UNKNOWN
        manga.description = document.select("div.animeinfo > div.rm > .desc > p").first().text()
        manga.thumbnail_url = document.select("div.animeinfo > div.lm > div.imgdesc > a > img").attr("src")

        return manga
    }

    override fun chapterListSelector() = "div.animeinfo > div.rm > div.epl > ul > li"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("span > a").first()
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = 0
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

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        var i = 0
        document.select("div#wrap p img").forEach { element ->
            val url = element.attr("src")
            i++
            if (url.isNotEmpty()) {
                pages.add(Page(i, "", url))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val imgHeader = Headers.Builder().apply {
            add("User-Agent", "Mozilla/5.0 (Linux; U; Android 4.1.1; en-gb; Build/KLP) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Safari/534.30")
            add("Referer", baseUrl)
        }.build()
        return GET(page.imageUrl!!, imgHeader)
    }
}
