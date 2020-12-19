package eu.kanade.tachiyomi.extension.en.astrallibrary

import eu.kanade.tachiyomi.lib.themesources.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class AstralLibrary : Madara("Astral Library", "https://www.astrallibrary.net", "en", SimpleDateFormat("d MMM", Locale.US)) {
    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response).reversed()
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga-tag/manga/?m_orderby=views&page=$page", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga-tag/manga/?m_orderby=latest&page=$page", headers)
    }
}
