package eu.kanade.tachiyomi.extension.pt.geassscanlator

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class GeassScanlator : WPMangaStream(
    "Geass Scanlator",
    "https://geassscan.xyz",
    "pt-BR",
    SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR"))
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    override val altName: String = "Nome alternativo: "

    // [...document.querySelectorAll('ul.genrez li')]
    //     .map(x => `Genre("${x.querySelector("label").innerHTML}", "${x.querySelector("input").value}")`)
    //     .join(',\n')
    override fun getGenreList(): List<Genre> = listOf(
        Genre("Ação", "acao"),
        Genre("Artes Marciais", "artes-marciais"),
        Genre("Aventura", "aventura"),
        Genre("Comédia", "comedia"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Escolar", "escolar"),
        Genre("Fantasia", "fantasia"),
        Genre("Harem", "harem"),
        Genre("Histórico", "historico"),
        Genre("Horror", "horror"),
        Genre("Magia", "magia"),
        Genre("Manga", "manga"),
        Genre("Manhua", "manhua"),
        Genre("Mistério", "misterio"),
        Genre("Romance", "romance"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo", "shoujo"),
        Genre("Shounen", "shounen"),
        Genre("Shounen Ai", "shounen-ai"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Sobrenatural", "sobrenatural"),
        Genre("Suspense", "suspense"),
        Genre("Webtoon", "webtoon")
    )
}
