package eu.kanade.tachiyomi.extension.all.mangabox

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class MangaBoxFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Mangakakalot(),
        Manganelo(),
        Mangabat(),
        OtherMangakakalot(),
        Mangairo()
    )
}

class Mangakakalot : MangaBox("Mangakakalot", "https://mangakakalot.com", "en") {
    override fun headersBuilder(): Headers.Builder = super.headersBuilder().set("Referer", "https://manganelo.com") // for covers
    override fun searchMangaSelector() = "${super.searchMangaSelector()}, div.list-truyen-item-wrap"
}

class Manganelo : MangaBox("Manganelo", "https://manganelo.com", "en") {
    // Nelo's date format is part of the base class
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/genre-all/$page?type=topview", headers)
    override fun popularMangaSelector() = "div.content-genres-item"
    override val latestUrlPath = "genre-all/"
    override fun searchMangaSelector() = "div.search-story-item, div.content-genres-item"
    override fun getAdvancedGenreFilters(): List<AdvGenre> = getGenreFilters()
        .drop(1)
        .map { AdvGenre(it.first, it.second) }
}

class Mangabat : MangaBox("Mangabat", "https://mangabat.com", "en", SimpleDateFormat("MMM dd,yy", Locale.ENGLISH)) {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga-list-all/$page?type=topview", headers)
    override fun popularMangaSelector() = "div.list-story-item"
    override val latestUrlPath = "manga-list-all/"
    override fun searchMangaSelector() = "div.list-story-item"
    override fun getAdvancedGenreFilters(): List<AdvGenre> = getGenreFilters()
        .drop(1)
        .map { AdvGenre(it.first, it.second) }
}

class OtherMangakakalot : MangaBox("Mangakakalots (unoriginal)", "https://mangakakalots.com", "en") {
    override fun searchMangaSelector(): String = "${super.searchMangaSelector()}, div.list-truyen-item-wrap"
    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector()).map { mangaFromElement(it) }
        val hasNextPage = !response.request().url().toString()
            .contains(document.select(searchMangaNextPageSelector()).attr("href"))

        return MangasPage(mangas, hasNextPage)
    }
    override fun searchMangaNextPageSelector() = "div.group_page a:last-of-type"
    override fun getStatusFilters(): Array<Pair<String?, String>> = arrayOf(
        Pair("all", "ALL"),
        Pair("Completed", "Completed"),
        Pair("Ongoing", "Ongoing")
    )
    override fun getGenreFilters(): Array<Pair<String?, String>> = arrayOf(
        Pair("all", "ALL"),
        Pair("Action", "Action"),
        Pair("Adult", "Adult"),
        Pair("Adventure", "Adventure"),
        Pair("Comedy", "Comedy"),
        Pair("Cooking", "Cooking"),
        Pair("Doujinshi", "Doujinshi"),
        Pair("Drama", "Drama"),
        Pair("Ecchi", "Ecchi"),
        Pair("Fantasy", "Fantasy"),
        Pair("Gender bender", "Gender bender"),
        Pair("Harem", "Harem"),
        Pair("Historical", "Historical"),
        Pair("Horror", "Horror"),
        Pair("Isekai", "Isekai"),
        Pair("Josei", "Josei"),
        Pair("Manhua", "Manhua"),
        Pair("Manhwa", "Manhwa"),
        Pair("Martial arts", "Martial arts"),
        Pair("Mature", "Mature"),
        Pair("Mecha", "Mecha"),
        Pair("Medical", "Medical"),
        Pair("Mystery", "Mystery"),
        Pair("One shot", "One shot"),
        Pair("Psychological", "Psychological"),
        Pair("Romance", "Romance"),
        Pair("School life", "School life"),
        Pair("Sci fi", "Sci fi"),
        Pair("Seinen", "Seinen"),
        Pair("Shoujo", "Shoujo"),
        Pair("Shoujo ai", "Shoujo ai"),
        Pair("Shounen", "Shounen"),
        Pair("Shounen ai", "Shounen ai"),
        Pair("Slice of life", "Slice of life"),
        Pair("Smut", "Smut"),
        Pair("Sports", "Sports"),
        Pair("Supernatural", "Supernatural"),
        Pair("Tragedy", "Tragedy"),
        Pair("Webtoons", "Webtoons"),
        Pair("Yaoi", "Yaoi"),
        Pair("Yuri", "Yuri")
    )
}

class Mangairo : MangaBox("Mangairo", "https://m.mangairo.com", "en", SimpleDateFormat("MMM-dd-yy", Locale.ENGLISH)) {
    override val popularUrlPath = "manga-list/type-topview/ctg-all/state-all/page-"
    override fun popularMangaSelector() = "div.story-item"
    override val latestUrlPath = "manga-list/type-latest/ctg-all/state-all/page-"
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/$simpleQueryPath${normalizeSearchQuery(query)}?page=$page", headers)
    }
    override fun searchMangaSelector() = "div.story-item"
    override fun searchMangaFromElement(element: Element): SManga = mangaFromElement(element, "h2 a")
    override fun searchMangaNextPageSelector() = "div.group-page a.select + a:not(.go-p-end)"
    override val mangaDetailsMainSelector = "${super.mangaDetailsMainSelector}, div.story_content"
    override val thumbnailSelector = "${super.thumbnailSelector}, div.story_info_left img"
    override val descriptionSelector = "${super.descriptionSelector}, div#story_discription p"
    override fun chapterListSelector() = "${super.chapterListSelector()}, div#chapter_list li"
    override val alternateChapterDateSelector = "p"
    override val pageListSelector = "${super.pageListSelector}, div.panel-read-story img"
    // will have to write a separate searchMangaRequest to get filters working for this source
    override fun getFilterList() = FilterList()
}
