package eu.kanade.tachiyomi.extension.pt.bananamecanica

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@Nsfw
class BananaMecanica : Madara(
    "Banana Mecânica",
    "https://leitorbm.com",
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
        Genre("Ação", "acao"),
        Genre("Action", "action"),
        Genre("Adult", "adult"),
        Genre("Adventure", "adventure"),
        Genre("Anime", "anime"),
        Genre("Cartoon", "cartoon"),
        Genre("Comédia", "comedia"),
        Genre("Comedy", "comedy"),
        Genre("Comic", "comic"),
        Genre("Cooking", "cooking"),
        Genre("Cotidiano", "cotidiano"),
        Genre("Detective", "detective"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Fantasia", "fantasia"),
        Genre("Fantasy", "fantasy"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Josei", "josei"),
        Genre("Live action", "live-action"),
        Genre("M-preg/Omegaverse", "m-preg-omegaverse"),
        Genre("Manga", "manga"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Martial Arts", "martial-arts"),
        Genre("Mature", "mature"),
        Genre("Mecha", "mecha"),
        Genre("Mistério", "misterio"),
        Genre("Mystery", "mystery"),
        Genre("Omegaverse", "omegaverse"),
        Genre("One shot", "one-shot"),
        Genre("Psicológico", "psicologico"),
        Genre("Psychological", "psychological"),
        Genre("Romance", "romance"),
        Genre("School Life", "school-life"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shotacon", "shotacon"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo Ai", "shoujo-ai"),
        Genre("Shounen", "shounen"),
        Genre("Shounen Ai", "shounen-ai"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Smut", "smut"),
        Genre("Sobrenatural", "sobrenatural"),
        Genre("Soft Yaoi", "soft-yaoi"),
        Genre("Soft Yuri", "soft-yuri"),
        Genre("Sports", "sports"),
        Genre("Supernatural", "supernatural"),
        Genre("Tragedy", "tragedy"),
        Genre("Vida Ecolar", "vida-ecolar"),
        Genre("Vida Escolar", "vida-escolar"),
        Genre("Webtoon", "webtoon"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri")
    )
}
