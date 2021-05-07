package eu.kanade.tachiyomi.extension.pt.fudidoscanlator

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.OkHttpClient
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@Nsfw
class FudidoScanlator : Madara(
    "Fudido Scanlator",
    "https://fudidoscan.com",
    "pt-BR",
    SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR"))
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    override fun popularMangaSelector() = "div.page-item-detail.manga"

    // The source has novels in text format, so we need to filter them.
    override fun searchMangaParse(response: Response): MangasPage {
        val mangaPage = super.searchMangaParse(response)
        val filteredResult = mangaPage.mangas.filter { it.title.contains(NOVEL_REGEX).not() }

        return MangasPage(filteredResult, mangaPage.hasNextPage)
    }

    // [...document.querySelectorAll('div.genres li a')]
    //     .map(x => `Genre("${x.innerText.slice(1, -4)}", "${x.href.replace(/.*-genre\/(.*)\//, '$1')}")`)
    //     .join(',\n')
    override fun getGenreList(): List<Genre> = listOf(
        Genre("Ação", "acao"),
        Genre("Artes Marciais", "artes-marciais"),
        Genre("Aventura", "aventura"),
        Genre("Comédia", "comedia"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Escolar", "escolar"),
        Genre("Fantasia ", "fantasia"),
        Genre("Ficção", "ficcao"),
        Genre("Harém", "harem"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Magia ", "magia"),
        Genre("Mistério", "misterio"),
        Genre("Protagonista badass", "protagonista-badass"),
        Genre("Psicológico", "psicologico"),
        Genre("Romance", "romance"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo", "shoujo"),
        Genre("Shounen", "shounen"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Sobrenatural", "sobrenatural"),
        Genre("Super Poderes", "super-poderes"),
        Genre("Suspense", "suspense"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri")
    )

    companion object {
        private val NOVEL_REGEX = "novel|livro".toRegex(RegexOption.IGNORE_CASE)
    }
}
