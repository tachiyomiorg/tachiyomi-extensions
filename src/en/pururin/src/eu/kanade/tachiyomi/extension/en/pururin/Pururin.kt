package eu.kanade.tachiyomi.extension.en.pururin

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Nsfw
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

        manga.description = getDesc(document)

        return manga
    }

    private fun getDesc(document: Document): String {
        val infoElement = document.select("div.box.box-gallery")
        val uploader = infoElement.select("tr:has(td:contains(Uploader)) .user-link")?.text()
        val pages = infoElement.select("tr:has(td:contains(Pages)) td:eq(1)").text()
        val ratingCount = infoElement.select("tr:has(td:contains(Ratings)) span[itemprop=\"ratingCount\"]")?.attr("content")

        val rating = infoElement.select("tr:has(td:contains(Ratings)) gallery-rating").attr(":rating")?.toFloatOrNull()?.let {
            if (it > 5.0f) minOf(it, 5.0f) // cap rating to 5.0 for rare cases where value exceeds 5.0f
            else it
        }

        val multiDescriptions = listOf(
            "Convention",
            "Parody",
            "Circle",
            "Category",
            "Character",
            "Language"
        ).map { it to infoElement.select("tr:has(td:contains($it)) a").map { v -> v.text() } }
            .filter { !it.second.isNullOrEmpty() }
            .map { "${it.first}: ${it.second.joinToString()}" }

        val descriptions = listOf(
            multiDescriptions.joinToString("\n\n"),
            uploader?.let { "Uploader: $it" },
            pages?.let { "Pages: $it" },
            rating?.let { "Ratings: $it" + (ratingCount?.let { c -> " ($c ratings)" } ?: "") }
        )

        return descriptions.joinToString("\n\n")
    }

    override fun chapterListParse(response: Response) = with(response.asJsoup()) {
        val mangaInfoElements = this.select(".table-gallery-info tr td:first-child").map {
            it.text() to it.nextElementSibling()
        }.toMap()

        val chapters = this.select(".table-collection tbody tr")
        if (!chapters.isNullOrEmpty())
            chapters.map {
                val details = it.select("td")
                SChapter.create().apply {
                    chapter_number = details[0].text().removePrefix("#").toFloat()
                    name = details[1].select("a").text()
                    setUrlWithoutDomain(details[1].select("a").attr("href"))

                    if (it.hasClass("active") && mangaInfoElements.containsKey("Scanlator"))
                        scanlator = mangaInfoElements.getValue("Scanlator").select("li a")?.joinToString { s -> s.text() }
                }
            }
        else
            listOf(
                SChapter.create().apply {
                    name = "Chapter"
                    setUrlWithoutDomain(response.request().url().toString())

                    if (mangaInfoElements.containsKey("Scanlator"))
                        scanlator = mangaInfoElements.getValue("Scanlator").select("li a")?.joinToString { s -> s.text() }
                }
            )
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(
        "$baseUrl${chapter.url.let {
            it.substringAfterLast("/").let { titleUri ->
                it.replace(titleUri, "01/$titleUri")
            }.replace("gallery", "read")
        }}"
    )

    override fun chapterListSelector(): String = throw UnsupportedOperationException("Not used")

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException("Not used")

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        val galleryInfo = document.select("gallery-read").toString().substringAfter('{').substringBefore('}')
        val id = galleryInfo.substringAfter("id&quot;:").substringBefore(',')
        val total: Int = (galleryInfo.substringAfter("total_pages&quot;:").substringBefore(',')).toInt()

        for (i in 1..total) {
            pages.add(Page(i, "", "https://cdn.pururin.io/assets/images/data/$id/$i.jpg"))
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
