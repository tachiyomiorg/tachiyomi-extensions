package eu.kanade.tachiyomi.extension.en.mangalazy

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Nsfw
class MangaLazy : Madara("MangaLazy", "https://mangalazy.com", "en") {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()
        
    override val pageListParseSelector = ".reading-content div.z_content:nth-child(2) > img:nth-child(n)"
}
