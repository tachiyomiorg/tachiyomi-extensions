package eu.kanade.tachiyomi.extension.en.manhuascan

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.annotations.Nsfw
import okhttp3.OkHttpClient

@Nsfw
class ManhuaScan : FMReader("ManhuaScan", "https://manhuascan.com", "en") {
    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1))
        .build()
    override fun fetchPageList(chapter: SChapter) = fetchPageListEncrypted(chapter)
}
