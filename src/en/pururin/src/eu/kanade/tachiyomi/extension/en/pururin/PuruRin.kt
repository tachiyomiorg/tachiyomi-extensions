package eu.kanade.tachiyomi.extension.en.pururin

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Pururin : ParsedHttpSource() {

    override val name = "Pururin"

    override val baseUrl = "https://pururin.io"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun latestUpdatesSelector() = "div.container div.row-gallery a"

    override fun latestUpdatesRequest(page: Int): Request {
        return if (page == 1) {
            GET(baseUrl, headers)
        } else {
            GET("$baseUrl/browse/newest?page=$page", headers)
        }
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.select("div.title").text()
        manga.thumbnail_url = element.select("img.card-img-top").attr("abs:data-src")

        return manga
    }

    override fun latestUpdatesNextPageSelector() = "ul.pagination a.page-link[rel=next]"

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
        manga.thumbnail_url = document.select("div.cover-wrapper v-lazy-image").attr("abs:src")

        var tags  = ""
        genres.forEach { tags+= " <$it>" }

        manga.description = "Title: " + manga.title + "\n\n" + getDesc(document) + tags

        return manga
    }

    private fun getDesc(document: Document): String {
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

    override fun chapterListSelector() = "div.gallery-action a"

    //TODO Make it work for collections
    override fun chapterFromElement(element: Element): SChapter {

        val chapter = SChapter.create()

        chapter.setUrlWithoutDomain(element.attr("href"))
        chapter.name = "Read the chapter"

        return chapter
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        val galleryInfo = document.select("gallery-read").toString().substringAfter('{').substringBefore('}')
        val id = galleryInfo.substringAfter("id&quot;:").substringBefore(',')
        val total: Int = (galleryInfo.substringAfter("total_pages&quot;:").substringBefore(',')).toInt()

        for (i in 1 .. total) {
            pages.add(Page(i,"", "https://cdn.pururin.io/assets/images/data/$id/$i.jpg"))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/browse/most-popular?page=$page", headers)

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    private lateinit var tagUrl: String

    // TODO: Additional filter options, specifically the type[] parameter
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = "$baseUrl/search?q=$query&page=$page"

        if (query.isBlank()) {
            filters.forEach { filter ->
                when (filter) {
                    is Tag -> {
                        url = if (page == 1) {
                            "$baseUrl/search/tag?q=${filter.state}&type[]=3" // "Contents" tag
                        } else {
                            "$tagUrl?page=$page"
                        }
                    }
                }
            }
        }

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return if (response.request().url().toString().contains("tag?")) {
            response.asJsoup().select("table.table tbody tr a:first-of-type").attr("abs:href").let {
                if (it.isNotEmpty()) {
                    tagUrl = it
                    super.searchMangaParse(client.newCall(GET(tagUrl, headers)).execute())
                } else {
                    MangasPage(emptyList(), false)
                }
            }
        } else {
            super.searchMangaParse(response)
        }
    }

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        Tag("Tag")
    )

    private class Tag(name: String) : Filter.Text(name)
}

