package eu.kanade.tachiyomi.extension.en.manhwahot

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.OkHttpClient

@Nsfw
class ManhwaHot : FMReader("ManhwaHot", "https://manhwahot.com", "en") {
    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, period=1))
        .build()
    override fun fetchPageList(chapter: SChapter) = fetchPageListEncrypted(chapter)
}
