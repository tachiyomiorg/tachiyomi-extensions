package eu.kanade.tachiyomi.extension.en.hentai20

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@Nsfw
class Hentai20 : Madara("Hentai20", "https://hentai20.com", "en") {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()
        
    override fun getGenreList() = listOf(
        Genre("Action", "action"),
        Genre("Adult", "adult"),
        Genre("Adventure", "adventure"),
        Genre("BL", "bl"),
        Genre("Comedy", "comedy"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Fanstasy", "fantasy"),
        Genre("GL", "gl"),
        Genre("Harem", "harem"),
        Genre("Hentai", "hentai"),
        Genre("Horror", "horror"),
        Genre("Manga", "manga"),
        Genre("Manhwa hentai", "manhwa-hentai"),
        Genre("Martial arts", "martial-arts"),
        Genre("Mature", "mature"),
        Genre("Mystery", "mystery"),
        Genre("Psychological", "psychological"),
        Genre("Romance", "romance"),
        Genre("School Life", "school-life"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Smut", "smut"),
        Genre("Supernatural", "supernatural"),
        Genre("Thriller", "thriller"),
    )
}
