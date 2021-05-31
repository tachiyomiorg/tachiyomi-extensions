package eu.kanade.tachiyomi.extension.pt.cerisescans

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class CeriseScans : Madara(
    "Cerise Scans",
    "https://cerisescans.com",
    "pt-BR",
    SimpleDateFormat("dd 'de' MMMMM 'de' yyyy", Locale("pt", "BR"))
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    override fun popularMangaSelector() = "div.page-item-detail.manga"

    override val altName: String = "Nome alternativo: "

    // [...document.querySelectorAll('div.genres li a')]
    //     .map(x => `Genre("${x.innerText.trim()}", "${x.href.replace(/.*-genre\/(.*)\//, '$1')}")`)
    //     .join(',\n')
    override fun getGenreList(): List<Genre> = listOf(
        Genre("Ação", "acao"),
        Genre("Adulto", "adulto"),
        Genre("Comédia", "comedia"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Fantasia", "fantasia"),
        Genre("Harem", "harem"),
        Genre("Harém Reverso", "harem-reverso"),
        Genre("Hentai", "hentai"),
        Genre("Histórico", "historico"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Magia", "magia"),
        Genre("Mistério", "misterio"),
        Genre("Romance", "romance"),
        Genre("Shoujo", "shoujo"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Sobrenatural", "sobrenatural"),
        Genre("Soft Yaoi", "soft-yaoi"),
        Genre("Soft Yuri", "soft-yuri"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri")
    )
}
