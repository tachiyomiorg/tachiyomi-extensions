package eu.kanade.tachiyomi.extension.pt.modescanlator

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class ModeScanlator : Madara(
    "Mode Scanlator",
    "https://modescanlator.com",
    "pt-BR",
    SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR"))
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    override fun popularMangaSelector() = "div.page-item-detail.manga"

    override val altName: String = "Nome alternativo: "

    // [...document.querySelectorAll('div.c-genres-block div.genres li a')]
    //     .map(x => `Genre("${x.innerText.trim().slice(0, -4)}", "${x.href.replace(/.*generos\/(.*)\//, '$1')}")`)
    //     .join(',\n')
    override fun getGenreList(): List<Genre> = listOf(
        Genre("Ação", "acao"),
        Genre("Artes Marciais", "artes-marciais"),
        Genre("Aventura", "aventura"),
        Genre("Comédia", "comedia"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Esportes", "esportes"),
        Genre("Fantasia", "fantasia"),
        Genre("Harem", "harem"),
        Genre("Histórico", "historico"),
        Genre("Horror", "horror"),
        Genre("Manga", "manga"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Psicológico", "psicologico"),
        Genre("Romance", "romance"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Webtoon", "webtoon"),
        Genre("Zumbis", "zumbis")
    )
}
