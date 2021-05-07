package eu.kanade.tachiyomi.extension.pt.wonderland

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class Wonderland : Madara(
    "Wonderland",
    "https://landwebtoons.site",
    "pt-BR",
    SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR"))
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    override val popularMangaUrlSelector = "div.post-title a:not([target])"

    // [...document.querySelectorAll('div.genres li a')]
    //     .map(x => `Genre("${x.innerText.slice(1, -4)}", "${x.href.replace(/.*-genre\/(.*)\//, '$1')}")`)
    //     .join(',\n')
    override fun getGenreList(): List<Genre> = listOf(
        Genre("Ação", "acao"),
        Genre("Comédia", "comedia"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Fantasia ", "fantasia"),
        Genre("Histórico", "historico"),
        Genre("Horror", "horror"),
        Genre("Josei", "josei"),
        Genre("Mistério", "misterio"),
        Genre("Psicológico", "psicologico"),
        Genre("Romance", "romance"),
        Genre("Shoujo", "shoujo"),
        Genre("Slice Of Life", "slice-of-life"),
        Genre("Smut", "smut"),
        Genre("Sobrenatural", "sobrenatural")
    )
}
