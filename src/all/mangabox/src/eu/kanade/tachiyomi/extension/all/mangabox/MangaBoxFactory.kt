package eu.kanade.tachiyomi.extension.all.mangabox

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.RequestBody
import java.text.SimpleDateFormat
import java.util.*

class MangaBoxFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Mangakakalot(),
        Manganelo(),
        Mangafree(),
        Mangabat(),
        KonoBasho(),
        MangaOnl(),
        ChapterManga()
    )
}

class Mangakakalot : MangaBox("Mangakakalot", "http://mangakakalot.com", "en")
class Manganelo : MangaBox("Manganelo", "https://manganelo.com", "en")
class Mangafree : MangaBox("Mangafree", "http://mangafree.online", "en") {
    override val popularUrlPath = "hotmanga"
    override val latestUrlPath = "latest"
    override fun chapterListSelector() = "div#ContentPlaceHolderLeft_list_chapter_comic div.row"
}
class Mangabat : MangaBox("Mangabat", "https://mangabat.com", "en") {
    override fun popularMangaSelector() = "div.item"
    override fun latestUpdatesSelector() = "div.update_item"
    override val simpleQueryPath = "search_manga/"
    override val mangaDetailsMainSelector = "div.truyen_info"
    override val thumbnailSelector = "img.info_image_manga"
    override val descriptionSelector = "div#contentm"
    override val pageListSelector = "div.vung_doc img"
}
class KonoBasho : MangaBox("Kono-Basho", "https://kono-basho.com", "en")
class MangaOnl : MangaBox("MangaOnl", "https://mangaonl.com", "en") {
    override val popularUrlPath = "story-list-ty-topview-st-all-ca-all-1"
    override val latestUrlPath = "story-list-ty-latest-st-all-ca-all-1"
    override fun popularMangaSelector() = "div.story_item"
    override val mangaDetailsMainSelector = "div.panel_story_info"
    override val thumbnailSelector = "img.story_avatar"
    override val descriptionSelector = "div.panel_story_info_description"
    override fun chapterListSelector() = "div.chapter_list_title + ul li"
    override val pageListSelector = "div.container_readchapter img"
}
class ChapterManga : MangaBox("ChapterManga", "https://chaptermanga.com", "en", SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)) {
    override val popularUrlPath = "hot-manga"
    override val latestUrlPath = "read-latest-manga"
    override val simpleQueryPath = "search_manga/"
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
}

