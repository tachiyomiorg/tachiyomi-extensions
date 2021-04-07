package eu.kanade.tachiyomi.extension.pt.toonei

import eu.kanade.tachiyomi.multisrc.mangasproject.MangasProject
import org.jsoup.nodes.Document
import okhttp3.Request
import okhttp3.Response
import okhttp3.OkHttpClient
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import java.util.concurrent.TimeUnit

class Toonei : MangasProject("Toonei", "https://toonei.com", "pt-br") {
    
    override fun getReaderToken(document: Document): String? {
        return document.select("script:containsData(window.PAGES_KEY)").firstOrNull()
            ?.data()
            ?.substringAfter("\"")
            ?.substringBefore("\";")
    }
}
