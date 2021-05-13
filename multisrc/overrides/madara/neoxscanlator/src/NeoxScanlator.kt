package eu.kanade.tachiyomi.extension.pt.neoxscanlator

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Response
import rx.Observable
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class NeoxScanlator : Madara(
    "Neox Scanlator",
    "https://neoxscans.net",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
) {
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", baseUrl)
        .add("Origin", baseUrl)

    // Filter the novels in pure text format.
    override fun popularMangaSelector() = "div.page-item-detail.manga"

    override fun searchMangaParse(response: Response): MangasPage {
        val mangaPage = super.searchMangaParse(response)
        val filteredResult = mangaPage.mangas.filter { it.title.contains(NOVEL_REGEX).not() }

        return MangasPage(filteredResult, mangaPage.hasNextPage)
    }

    // Sometimes the site changes the manga URL. This override will
    // add an error instead of the HTTP 404 to inform the user to
    // migrate from Neox to Neox to update the URL.
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservable()
            .doOnNext { response ->
                if (!response.isSuccessful) {
                    response.close()
                    throw Exception(if (response.code == 404) MIGRATION_MESSAGE else "HTTP error ${response.code}")
                }
            }
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    override val altNameSelector = ".post-content_item:contains(Alternativo) .summary-content"
    override val altName = "Nome alternativo: "

    // Only status and order by filter work.
    override fun getFilterList(): FilterList = FilterList(super.getFilterList().slice(3..4))

    companion object {
        private const val MIGRATION_MESSAGE = "O URL deste mangá mudou. " +
            "Faça a migração do Neox para o Neox para atualizar a URL."

        private val NOVEL_REGEX = "novel|livro".toRegex(RegexOption.IGNORE_CASE)
    }
}
