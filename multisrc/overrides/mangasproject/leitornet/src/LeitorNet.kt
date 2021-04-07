package eu.kanade.tachiyomi.extension.pt.leitornet

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.mangasproject.MangasProject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Request
import okhttp3.Response
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class LeitorNet : MangasProject("Leitor.net", "https://leitor.net", "pt-BR") {

    // Use the old generated id when the source did have the name "mangásPROJECT" and
    // did have mangas in their catalogue. Now they "only have webtoons" and
    // became a different website, but they still use the same structure.
    // Existing mangas and other titles in the library still work.
    override val id: Long = 2225174659569980836

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(RateLimitInterceptor(5, 1, TimeUnit.SECONDS))
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    /**
     * Temporary fix to bypass Cloudflare.
     */
    override fun pageListRequest(chapter: SChapter): Request {
        val newHeaders = super.pageListRequest(chapter).headers().newBuilder()
            .set("Referer", "https://mangalivre.net/home")
            .build()

        val newChapterUrl = chapter.url
            .replace("/manga/", "/ler/")
            .replace("/(\\d+)/capitulo-".toRegex(), "/online/$1/capitulo-")

        return GET("https://mangalivre.net$newChapterUrl", newHeaders)
    }

    override fun getChapterUrl(response: Response): String {
        return super.getChapterUrl(response)
            .replace("https://mangalivre.net", baseUrl)
            .replace("/ler/", "/manga/")
            .replace("/online/", "/")
    }
}
