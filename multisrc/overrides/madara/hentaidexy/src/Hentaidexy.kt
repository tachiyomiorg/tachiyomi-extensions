package eu.kanade.tachiyomi.extension.en.hentaidexy

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@Nsfw
class Hentaidexy : Madara("Hentaidexy", "https://hentaidexy.com", "en") {
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
        Genre("All Ages", "all-ages"),
        Genre("Big Ass", "big-ass"),
        Genre("BL", "bl"),
        Genre("Blowjob", "blowjob"),
        Genre("Body swap", "body-swap"),
        Genre("Bondage", "bondage"),
        Genre("Censored", "censored"),
        Genre("Comedy", "comedy"),
        Genre("Comics", "comics"),
        Genre("Completed manga", "completed-manga"),
        Genre("Cooking", "cooking"),
        Genre("Creampie", "creampie"),
        Genre("Crime", "crime"),
        Genre("Cunnilingus", "cunnilingus"),
        Genre("Dirty oldman", "dirty-oldman"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Fanstasy", "fantasy"),
        Genre("Fingering", "fingering"),
        Genre("Full color", "full-color"),
        Genre("Gender bender", "gender-bender"),
        Genre("GL", "gl"),
        Genre("Gossip", "gossip"),
        Genre("Hardcore Vanilla", "hardcore-vanilla"),
        Genre("Harem", "harem"),
        Genre("Hentai", "hentai"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Incest", "incest"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Long strip", "long-strip"),
        Genre("Mafia", "mafia"),
        Genre("Magic", "magic"),
        Genre("Manga", "manga"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Manhwa Hentai Manga", "martial-manhwa-hentai-manga"),
        Genre("Martial arts", "martial-arts"),
        Genre("Mature", "mature"),
        Genre("Mecha", "mecha"),
        Genre("Medical", "medical"),
        Genre("Mystery", "mystery"),
        Genre("NTR", "ntr"),
        Genre("Office", "office"),
        Genre("One shot", "one-shot"),
        Genre("Psychological", "psychological"),
        Genre("Rape", "rape"),
        Genre("Raw", "raw"),
        Genre("Romance", "romance"),
        Genre("Sci-fi", "sci-fi"),
        Genre("School Life", "school-life"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo ai", "shoujo-ai"),
        Genre("Shounen", "shounen"),
        Genre("Shounen ai", "shounen-ai"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Smut", "smut"),
        Genre("Sports", "sports"),
        Genre("Supernatural", "supernatural"),
        Genre("Thriller", "thriller"),
        Genre("Toomics", "toomics"),
        Genre("Tragedy", "tragedy"),
        Genre("Uncensored", "uncensored"),
        Genre("Vampire", "vampire"),
        Genre("Vanilla", "vanilla"),
        Genre("Web comic", "web-comic"),
        Genre("Webtoon", "webtoon"),
        Genre("Webtoons", "webtoons"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri"),
    )
}
