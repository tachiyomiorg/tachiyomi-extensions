package eu.kanade.tachiyomi.extension.all.madara

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.*

class MadaraFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
            Mangasushi(),
            NinjaScans(),
            ReadManhua(),
            ZeroScans()
    )
}

class Mangasushi : LoadMadara("Mangasushi", "https://mangasushi.net", "en") {
    override fun latestUpdatesSelector() = "div.page-item-detail"
}
class NinjaScans : PageMadara("NinjaScans", "https://ninjascans.com", "en", urlModifier = "/manhua")
class ReadManhua : LoadMadara("ReadManhua", "https://readmanhua.net", "en", dateFormat = SimpleDateFormat("dd MMM yy", Locale.US))
class ZeroScans : PageMadara("ZeroScans", "https://zeroscans.com", "en")

open class LoadMadara(
        name: String,
        baseUrl: String,
        lang: String,
        dateFormat: SimpleDateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
) : Madara(name, baseUrl, lang, dateFormat) {
    override fun popularMangaRequest(page: Int): Request {
        val form = FormBody.Builder().apply {
            add("action", "madara_load_more")
            add("page", (page-1).toString())
            add("template", "madara-core/content/content-archive")
            add("vars[manga_archives_item_layout]", "default")
            add("vars[meta_key]", "_latest_update")
            add("vars[order]", "desc")
            add("vars[paged]", (page-1).toString())
            add("vars[post_status]", "publish")
            add("vars[post_type]", "wp-manga")
            add("vars[sidebar]", "right")
            add("vars[template]", "archive")
        }
        return POST("$baseUrl/wp-admin/admin-ajax.php", headers, form.build(), CacheControl.FORCE_NETWORK)
    }

    override fun popularMangaNextPageSelector(): String? = "body:not(:has(.no-posts))"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val form = FormBody.Builder().apply {
            add("action", "madara_load_more")
            add("page", (page-1).toString())
            add("template", "madara-core/content/content-search")
            add("vars[s]", query)
            add("vars[orderby]", "")
            add("vars[paged]", (page-1).toString())
            add("vars[template]", "search")
            add("vars[post_type]", "wp-manga")
            add("vars[post_status]", "publish")
            add("vars[manga_archives_item_layout]", "default")
        }
        return POST("$baseUrl/wp-admin/admin-ajax.php", headers, form.build(), CacheControl.FORCE_NETWORK)
    }
}

open class PageMadara(
        name: String,
        baseUrl: String,
        lang: String,
        private val urlModifier: String = "/manga",
        dateFormat: SimpleDateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
) : Madara(name, baseUrl, lang, dateFormat) {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl$urlModifier/page/$page", headers)
    override fun popularMangaNextPageSelector() = "div.nav-previous"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/page/$page/?s=$query&post_type=wp-manga", headers)

}
