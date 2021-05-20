package eu.kanade.tachiyomi.extension.ja.tonarinoyoungjump

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element

class TonariNoYoungJump : GigaViewer(
    "Tonari no Young Jump",
    "https://tonarinoyj.jp",
    "ja",
    "https://cdn-img.tonarinoyj.jp/public/page"
) {

    override val supportsLatest: Boolean = false

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(::imageIntercept)
        .build()

    override val publisher: String = "集英社"

    override fun popularMangaSelector(): String = "ul.daily-series li.daily-series-item > a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("h4.daily-series-title").text()
        thumbnail_url = element.select("div.daily-series-thumb img").attr("data-src")
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun chapterListSelector(): String = "li.episode"

    override fun getCollections(): List<Collection> = listOf(
        Collection("連載一覧", ""),
        Collection("読切作品", "oneshot"),
        Collection("連載終了作品", "finished")
    )
}
