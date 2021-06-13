package eu.kanade.tachiyomi.extension.en.yaoimanga

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@Nsfw
class YaoiManga : Madara("Yaoi.mobi", "https://yaoi.mobi", "en") {
    private val rateLimitInterceptor = RateLimitInterceptor(1)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()
        
    override fun getGenreList() = listOf(
        Genre("Action", "action"),
        Genre("Adult", "adult"),
        Genre("Adventure", "adventure"),
        Genre("Anime", "anime"),
        Genre("BL", "bl"),
        Genre("Boys Love", "boyslove"),
        Genre("Cartoon", "cartoon"),
        Genre("Comedy", "comedy"),
        Genre("Comic", "comic"),
        Genre("Cooking", "cooking"),
        Genre("Detective", "detective"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Fanstasy", "fantasy"),
        Genre("Free hentai", "free-hentai"),
        Genre("Gender bender", "gender-bender"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Live action", "live-action"),
        Genre("Manga", "manga"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Martial arts", "martial-arts"),
        Genre("Mature", "mature"),
        Genre("Mecha", "mecha"),
        Genre("Medical", "medical"),
        Genre("Mystery", "mystery"),
        Genre("One shot", "one-shot"),
        Genre("Premium", "premium"),
        Genre("Psychological", "psychological"),
        Genre("Raw", "raw"),
        Genre("Romance", "romance"),
        Genre("School life", "school-life"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo ai", "shoujo-ai"),
        Genre("Shounen", "shounen"),
        Genre("Shounen ai", "shounen-ai"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Smut", "smut"),
        Genre("Soft Yaoi", "soft-yaoi"),
        Genre("Soft Yuri", "soft-yuri"),
        Genre("Sports", "sports"),
        Genre("Supernatural", "supernatural"),
        Genre("Tragedy", "tragedy"),
        Genre("Webtoon", "webtoon"),
        Genre("Webtoons", "webtoons"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri"),
    )
}
