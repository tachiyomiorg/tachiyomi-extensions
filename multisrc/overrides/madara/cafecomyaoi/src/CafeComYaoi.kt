package eu.kanade.tachiyomi.extension.pt.cafecomyaoi

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@Nsfw
class CafeComYaoi : Madara(
    "Café com Yaoi",
    "http://cafecomyaoi.com.br",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    // [...document.querySelectorAll('input[name="genre[]"]')]
    //   .map(x => `Genre("${document.querySelector('label[for=' + x.id + ']').innerHTML.trim()}", "${x.value}")`)
    //   .join(',\n')
    override fun getGenreList(): List<Genre> = listOf(
        Genre("Ação", "acao"),
        Genre("Aventura", "aventura"),
        Genre("BDSM", "bdsm"),
        Genre("BL", "bl"),
        Genre("Comédia", "comedia"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drama", "drama"),
        Genre("Fantasia", "fantasia"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Harem", "harem"),
        Genre("Histórico", "historico"),
        Genre("Horror", "horror"),
        Genre("Máfia", "mafia"),
        Genre("Mangá", "manga"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Mature", "mature"),
        Genre("Mistério", "misterio"),
        Genre("Omegaverse", "omegaverse"),
        Genre("One shot", "one-shot"),
        Genre("Psicológico", "psicologico"),
        Genre("Romance", "romance"),
        Genre("School Life", "school-life"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Shoujo", "shoujo"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Smut", "smut"),
        Genre("Sobrenatural", "sobrenatural"),
        Genre("Tragédia", "tragedia"),
        Genre("Triângulo Amoroso", "triangulo-amoroso"),
        Genre("Webcomic", "webcomic")
    )
}
