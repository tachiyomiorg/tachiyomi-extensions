package eu.kanade.tachiyomi.extension.en.mangalibrary

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request

class MangaLibrary : Madara("Manga Library", "https://mangalibrary.net", "en") {

    override fun chapterListSelector() = "li.wp-manga-chapter  "

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga-library/$page?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga-library/$page?m_orderby=latest", headers)
}
