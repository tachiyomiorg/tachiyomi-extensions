package eu.kanade.tachiyomi.extension.pt.imperiodosotakus

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.OkHttpClient
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class ImperioDosOtakus : Madara(
    "Império dos Otakus",
    "https://imperiodosotakus.tk",
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

    override val altName: String = "Nome alternativo: "

    companion object {
        private val NOVEL_REGEX = "novel|livro".toRegex(RegexOption.IGNORE_CASE)
    }
}
