package eu.kanade.tachiyomi.extension.pt.arthurscan

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class ArthurScan : Madara(
    "Arthur Scan",
    "https://arthurscan.xyz",
    "pt-BR",
    SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR"))
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    override fun popularMangaSelector() = "div.page-item-detail.manga"

    override val altName: String = "Nome alternativo: "

    // [...document.querySelectorAll('div.genres li a')]
    //     .map(x => `Genre("${x.innerText.slice(1, -4).trim()}", "${x.href.replace(/.*-genre\/(.*)\//, '$1')}")`)
    //     .join(',\n')
    override fun getGenreList(): List<Genre> = listOf(
        Genre("Ação", "acao"),
        Genre("Artes Marciais", "artes-marciais"),
        Genre("Aventura", "aventura"),
        Genre("Comédia", "comedia"),
        Genre("Drama", "drama"),
        Genre("Fantasia", "fantasia"),
        Genre("Harém", "harem"),
        Genre("Histórico", "historico"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Mistério", "misterio"),
        Genre("Reencarnação", "reencarnacao"),
        Genre("Romance", "romance"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shounen", "shounen"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Sobrenatural", "sobrenatural"),
        Genre("Vida Escolar", "vida-escolar"),
        Genre("Web Comic", "web-comic"),
        Genre("Web Novel", "web-novel"),
        Genre("Webtoon", "webtoon")
    )
}
