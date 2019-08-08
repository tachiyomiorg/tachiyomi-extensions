package eu.kanade.tachiyomi.extension.en.pururin

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder


class Pururin : ParsedHttpSource() {

    private fun pagedRequest(url: String, page: Int, queryString: String? = null): Request {
        // The site redirects page 1 -> url-without-page so we do this redirect early for optimization
        val builtUrl = if (page == 1) url else "${url}browse/newest?page=$page"
        return GET(if (queryString != null) "$url$queryString" else builtUrl)
    }

    override val name = "Pururin"

    override val baseUrl = "https://pururin.io"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun latestUpdatesSelector() = "div.container div.row-gallery a"

    override fun latestUpdatesRequest(page: Int) = pagedRequest("$baseUrl/", page)


    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.url = element.attr("href")
        manga.title = element.select("div.title").text()
        manga.thumbnail_url = "https:" + element.select("img.card-img-top").attr("data-src")

        return manga
    }

    override fun latestUpdatesNextPageSelector() = "ul.pagination a.page-link[rel=next]"


    override fun mangaDetailsRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.mangaDetailsRequest(manga)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.box.box-gallery")
        val manga = SManga.create()
        val genres = mutableListOf<String>()

        document.select("tr:has(td:contains(Contents)) li").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }

        manga.title = infoElement.select("h1").text()
        manga.author = infoElement.select("tr:has(td:contains(Artist)) a").attr("title")
        manga.artist = infoElement.select("tr:has(td:contains(Circle)) a").text()
        manga.status = SManga.COMPLETED
        manga.genre = genres.joinToString(", ")
        manga.thumbnail_url = "https:" + document.select("div.cover-wrapper v-lazy-image").attr("src")

        var tags  = ""
        genres.forEach { tags+= " <$it>" }

        manga.description = "Title: " + manga.title + "\n\n" + getDesc(document) + tags

        return manga
    }

    fun getDesc(document: Document): String {
        val infoElement = document.select("div.box.box-gallery")
        val stringBuilder = StringBuilder()
        val magazine = infoElement.select("tr:has(td:contains(Convention)) a").text()
        val parodies = infoElement.select("tr:has(td:contains(Parody)) a").text()
        val pagess = infoElement.select("tr:has(td:contains(Pages)) td:eq(1)").text()


        if (magazine.isNotEmpty()) {
            stringBuilder.append("Magazine: ")
            stringBuilder.append(magazine)
            stringBuilder.append("\n\n")
        }

        if (parodies.isNotEmpty()) {
            stringBuilder.append("Parodies: ")
            stringBuilder.append(parodies)
            stringBuilder.append("\n\n")
        }

        stringBuilder.append("Pages: ")
        stringBuilder.append(pagess)
        stringBuilder.append("\n\n")

        return stringBuilder.toString()
    }

    override fun chapterListRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.chapterListRequest(manga)
    }

    override fun chapterListSelector() = "div.gallery-action a"

    //TODO Make it work for collections
    override fun chapterFromElement(element: Element): SChapter {

        val chapter = SChapter.create()

        chapter.url = element.attr("href")
        chapter.name = "Read the chapter"

        return chapter
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("http")) {
            return GET(chapter.url, headers)
        }
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()


        val galleryInfo = document.select("gallery-read").toString().substringAfter('{').substringBefore('}')
        val id = galleryInfo.substringAfter("id&quot;:").substringBefore(',')
        val total: Int = (galleryInfo.substringAfter("total_pages&quot;:").substringBefore(',')).toInt()


        for (i in 1 until total) { //TODO 0?
            pages.add(Page(i,"", "https://cdn.pururin.io/assets/images/data/$id/$i.jpg"))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw  UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList(
        Filter.Header("CLOSE. THIS. TAB. NOW. [please]"),
        Filter.Separator()
    )


    override fun popularMangaRequest(page: Int): Request = latestUpdatesRequest(page)

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url: String? = null
        var queryString: String? = null

        if (query.isNotBlank()) {
            url = "/search?"
            queryString = "q=" + URLEncoder.encode(query, "UTF-8")
        }

        return url?.let {
            pagedRequest("$baseUrl$url", page, queryString)
        } ?: latestUpdatesRequest(page)

    }

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()


}

