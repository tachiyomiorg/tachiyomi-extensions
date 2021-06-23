package eu.kanade.tachiyomi.extension.en.mangakitsune

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

@Nsfw
class MangaKitsune : Madara("MangaKitsune", "https://mangakitsune.com", "en", dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()



        
    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response).reversed()

    override val pageListParseSelector = ".reading-content div.text-left :has(>img)"
    
    override fun getGenreList() = listOf(
        Genre("Action", "action"),
        Genre("Adult", "adult"),
        Genre("Adventure", "adventure"),
        Genre("Anime", "anime"),
        Genre("Cartoon", "cartoon"),
        Genre("Comedy", "comedy"),
        Genre("Comic", "comic"),
        Genre("Cooking", "cooking"),
        Genre("Detective", "detective"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Fanstasy", "fantasy"),
        Genre("Gender bender", "gender-bender"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Josei", "josei"),
        Genre("Live action", "live-action"),
        Genre("Lolicon", "lolicon"),
        Genre("Manga", "manga"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Martial arts", "martial-arts"),
        Genre("Mature", "mature"),
        Genre("Mecha", "mecha"),
        Genre("Mystery", "mystery"),
        Genre("One shot", "one-shot"),
        Genre("Psychological", "psychological"),
        Genre("Romance", "romance"),
        Genre("School Life", "school-life"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shotacon", "shotacon"),
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
