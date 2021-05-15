package eu.kanade.tachiyomi.extension.pt.fenixscanlator

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class FenixScanlator : Madara(
    "Fênix Scanlator",
    "https://fenixscanlator.xyz",
    "pt-BR",
    SimpleDateFormat("dd 'de' MMMMM 'de' yyyy", Locale("pt", "BR"))
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    // [...document.querySelectorAll('input[name="genre[]"]')]
    //   .map(x => `Genre("${document.querySelector('label[for=' + x.id + ']').innerHTML.trim()}", "${x.value}")`)
    //   .join(',\n')
    override fun getGenreList(): List<Genre> = listOf(
        Genre("Action", "action"),
        Genre("Adult", "adult"),
        Genre("Adventure", "adventure"),
        Genre("Anime", "anime"),
        Genre("Cartoon", "cartoon"),
        Genre("Comedy", "comedy"),
        Genre("Comic", "comic"),
        Genre("Cooking", "cooking"),
        Genre("Delinquentes", "delinquentes"),
        Genre("Detective", "detective"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Fantasmas", "fantasmas"),
        Genre("Fantasy", "fantasy"),
        Genre("Gastronomia", "gastronomia"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Live action", "live-action"),
        Genre("Long Strip", "long-strip"),
        Genre("Magia", "magia"),
        Genre("Manga", "manga"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Martial Arts", "martial-arts"),
        Genre("Mature", "mature"),
        Genre("Mecha", "mecha"),
        Genre("Medicina", "medicina"),
        Genre("Monstros", "monstros"),
        Genre("Mystery", "mystery"),
        Genre("One shot", "one-shot"),
        Genre("Pós-Apocalíptico", "pos-apocaliptico"),
        Genre("Psychological", "psychological"),
        Genre("Realidade Virtual", "realidade-virtual"),
        Genre("Reencarnação", "reencarnacao"),
        Genre("Romance", "romance"),
        Genre("School Life", "school-life"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo Ai", "shoujo-ai"),
        Genre("Shounen", "shounen"),
        Genre("Shounen Ai", "shounen-ai"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Smut", "smut"),
        Genre("Sobrevivência", "sobrevivencia"),
        Genre("Soft Yaoi", "soft-yaoi"),
        Genre("Soft Yuri", "soft-yuri"),
        Genre("Sports", "sports"),
        Genre("Super Herói", "super-heroi"),
        Genre("Supernatural", "supernatural"),
        Genre("Thriller", "thriller"),
        Genre("Tragedy", "tragedy"),
        Genre("Vídeo Games", "video-games"),
        Genre("Web Comic", "web-comic"),
        Genre("Webtoon", "webtoon"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri")
    )
}
