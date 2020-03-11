package eu.kanade.tachiyomi.extension.all.mangabox

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaBoxFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Mangakakalot(),
        Manganelo(),
        Mangafree(),
        Mangabat(),
        KonoBasho(),
        MangaOnl()
        //ChapterManga()
    )
}

//TODO: Alternate search/filters for some sources that don't use query parameters

class Mangakakalot : MangaBox("Mangakakalot", "https://mangakakalot.com", "en") {
    override fun searchMangaSelector() = "${super.searchMangaSelector()}, div.list-truyen-item-wrap"
}

class Manganelo : MangaBox("Manganelo", "https://manganelo.com", "en") {
    // Nelo's date format is part of the base class
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/genre-all/$page?type=topview", headers)
    override fun popularMangaSelector() = "div.content-genres-item"
    override val latestUrlPath = "genre-all/"
    override fun searchMangaSelector() = "div.search-story-item, div.content-genres-item"
    override fun getFilterList() = FilterList()
}

class Mangafree : MangaBox("Mangafree", "http://mangafree.online", "en") {
    override val popularUrlPath = "hotmanga/"
    override val latestUrlPath = "latest/"
    override fun popularMangaParse(response: Response): MangasPage {
        return response.asJsoup().let { document ->
            val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }

            val hasNextPage = document.select("script:containsData(setpagination)").last().data()
                .substringAfter("setPagination(").substringBefore(")").split(",").let {
                    it[0] != it[1]
                }
            MangasPage(mangas, hasNextPage)
        }
    }
    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)
    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)
    override fun chapterListSelector() = "div#ContentPlaceHolderLeft_list_chapter_comic div.row"
    override fun getFilterList() = FilterList()
}

class Mangabat : MangaBox("Mangabat", "https://mangabat.com", "en", SimpleDateFormat("MMM dd,yy", Locale.ENGLISH)) {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga-list-all/$page?type=topview", headers)
    override fun popularMangaSelector() = "div.list-story-item"
    override val latestUrlPath = "manga-list-all/"
    override fun searchMangaSelector() = "div.list-story-item"
}

class KonoBasho : MangaBox("Kono-Basho", "https://kono-basho.com", "en", SimpleDateFormat("MMM dd,yy", Locale.ENGLISH)) {
    // Basically a dupe of Manganelo
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/genre-all/$page?type=topview", headers)
    override fun popularMangaSelector() = "div.content-genres-item"
    override val latestUrlPath = "genre-all/"
    override fun searchMangaSelector() = "div.search-story-item"
    override fun getFilterList() = FilterList()
}

class MangaOnl : MangaBox("MangaOnl", "https://mangaonl.com", "en") {
    override val popularUrlPath = "story-list-ty-topview-st-all-ca-all-"
    override val latestUrlPath = "story-list-ty-latest-st-all-ca-all-"
    override fun popularMangaSelector() = "div.story_item"
    override val mangaDetailsMainSelector = "div.panel_story_info, ${super.mangaDetailsMainSelector}" //Some manga link to Nelo
    override val thumbnailSelector = "img.story_avatar, ${super.thumbnailSelector}"
    override val descriptionSelector = "div.panel_story_info_description, ${super.descriptionSelector}"
    override fun chapterListSelector() = "div.chapter_list_title + ul li, ${super.chapterListSelector()}"
    override val pageListSelector = "div.container_readchapter img, ${super.pageListSelector}"
    override fun getFilterList() = FilterList()
}

class ChapterManga : MangaBox("ChapterManga", "https://chaptermanga.com", "en", SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)) {
    override val popularUrlPath = "hot-manga-page-"
    override val latestUrlPath = "read-latest-manga-page-"
    override fun chapterListRequest(manga: SManga): Request {
        val response = client.newCall(GET(baseUrl + manga.url, headers)).execute()
        val cookie = response.headers("set-cookie")
            .filter{ it.contains("laravel_session") }
            .map{ it.substringAfter("=").substringBefore(";") }
        val document = response.asJsoup()
        val token = document.select("meta[name=\"csrf-token\"]").attr("content")
        val script = document.select("script:containsData(manga_slug)").first()
        val mangaSlug = script.data().substringAfter("manga_slug : \'").substringBefore("\'")
        val mangaId = script.data().substringAfter("manga_id : \'").substringBefore("\'")
        val tokenHeaders = headers.newBuilder()
            .add("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .add("X-CSRF-Token", token)
            .add("Cookie", cookie.toString())
            .build()
        val body = RequestBody.create(null, "manga_slug=$mangaSlug&manga_id=$mangaId")

        return POST("$baseUrl/get-chapter-list", tokenHeaders, body)
    }
    override fun chapterListSelector() = "div.row"
    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply {
        chapter_number = Regex("""[Cc]hapter\s\d*""").find(name)?.value?.substringAfter(" ")?.toFloatOrNull() ?: 0F
    }
    // TODO chapterlistparse -- default chapter order could be better
    override fun getFilterList() = FilterList()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val site = baseUrl.substringAfter("//")
        val searchHeaders = headers.newBuilder().add("Content-Type", "application/x-www-form-urlencoded").build()
        val body = RequestBody.create(null, "q=site%3A$site+inurl%3A$site%2Fread-manga+${query.replace(" ", "+")}&b=&kl=us-en")

        return POST("https://duckduckgo.com/html/", searchHeaders, body)
    }
    override fun searchMangaParse(response: Response): MangasPage {
        val mangas = response.asJsoup().select(searchMangaSelector())
            .filter{ it.text().startsWith("Read") }
            .map{ searchMangaFromElement(it) }

        return MangasPage(mangas, false)
    }
    override fun searchMangaSelector() = "div.result h2 a"
    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.text().substringAfter("Read").substringBeforeLast("online").trim()
            setUrlWithoutDomain(element.attr("href"))
        }
    }
}

