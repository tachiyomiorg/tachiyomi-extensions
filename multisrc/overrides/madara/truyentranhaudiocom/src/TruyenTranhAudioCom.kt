package eu.kanade.tachiyomi.extension.vi.truyentranhaudiocom

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class TruyenTranhAudioCom : Madara("TruyenTranhAudio.com", "https://truyentranhaudio.com", "vi", SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())) {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/page/$page?s&post_type=wp-manga&m_orderby=views", headers)
    override fun popularMangaSelector() = searchMangaSelector()
    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page?s&post_type=wp-manga&m_orderby=latest", headers)
    override fun latestUpdatesSelector() = searchMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)
    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.reading-content img").map { it.attr("abs:src") }
            .filterNot { it.isNullOrEmpty() }
            .distinct()
            .mapIndexed { i, url -> Page(i, "", url) }
    }
}
