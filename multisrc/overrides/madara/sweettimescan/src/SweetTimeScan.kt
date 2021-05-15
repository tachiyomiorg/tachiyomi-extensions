package eu.kanade.tachiyomi.extension.pt.sweettimescan

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class SweetTimeScan : Madara(
    "Sweet Time Scan",
    "https://sweetscan.net",
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

    // The site uses some image cache plugin that made the thumbnails don't load.
    // This removes the plugin site base URL and returns the direct image URL.
    override fun imageFromElement(element: Element): String {
        return baseUrl + super.imageFromElement(element)?.substringAfter(baseUrl)
    }

    // [...document.querySelectorAll('input[name="genre[]"]')]
    //   .map(x => `Genre("${document.querySelector('label[for=' + x.id + ']').innerHTML.trim()}", "${x.value}")`)
    //   .join(',\n')
    override fun getGenreList(): List<Genre> = listOf(
        Genre("Ação", "acao"),
        Genre("Artes Marciais", "artes-marciais"),
        Genre("Aventura", "aventura"),
        Genre("Comédia", "comedia"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Escolar", "escolar"),
        Genre("Fantasia", "fantasia"),
        Genre("Histórico", "historico"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Magia", "magia"),
        Genre("Médico", "medico"),
        Genre("Mistério", "misterio"),
        Genre("Psicológico", "psicologico"),
        Genre("Reencarnação", "reencarnacao"),
        Genre("Romance", "romance"),
        Genre("Sci Fi", "sci-fi"),
        Genre("Shoujo", "shoujo"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Sobrenatural", "sobrenatural")
    )

    companion object {
        private val NOVEL_REGEX = "novel|livro".toRegex(RegexOption.IGNORE_CASE)
    }
}
