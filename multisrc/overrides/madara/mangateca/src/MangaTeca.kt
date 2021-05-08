package eu.kanade.tachiyomi.extension.pt.mangateca

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MangaTeca : Madara(
    "MangaTeca",
    "https://www.mangateca.com",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", baseUrl)
        .add("Origin", baseUrl)

    override fun chapterFromElement(element: Element): SChapter {
        val parsedChapter = super.chapterFromElement(element)

        parsedChapter.date_upload = element.select("img").firstOrNull()?.attr("alt")
            ?.let { parseChapterDate(it) }
            ?: parseChapterDate(element.select("span.chapter-release-date i").firstOrNull()?.text())

        return parsedChapter
    }
}
