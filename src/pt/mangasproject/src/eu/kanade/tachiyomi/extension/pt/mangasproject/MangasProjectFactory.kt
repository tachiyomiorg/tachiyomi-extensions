package eu.kanade.tachiyomi.extension.pt.mangasproject

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document

@Nsfw
class MangasProjectFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        LeitorNet(),
        MangaLivre(),
        Toonei()
    )
}

class LeitorNet : MangasProject("Leitor.net", "https://leitor.net") {
    // Use the old generated id when the source did have the name "mangásPROJECT" and
    // did have mangas in their catalogue. Now they "only have webtoons" and
    // became a different website, but they still use the same structure.
    // Existing mangas and other titles in the library still work.
    override val id: Long = 2225174659569980836

    /**
     * Temporary fix to bypass Cloudflare.
     */
    override fun pageListRequest(chapter: SChapter): Request {
        val newHeaders = super.pageListRequest(chapter).headers().newBuilder()
            .set("Referer", "https://mangalivre.net/home")
            .build()

        val newChapterUrl = chapter.url
            .replace("/manga/", "/ler/")
            .replace("/(\\d+)/capitulo-".toRegex(), "/online/$1/capitulo-")

        return GET("https://mangalivre.net$newChapterUrl", newHeaders)
    }

    override fun getChapterUrl(response: Response): String {
        return super.getChapterUrl(response)
            .replace("https://mangalivre.net", baseUrl)
            .replace("/ler/", "/manga/")
            .replace("/online/", "/")
    }
}

class MangaLivre : MangasProject("Mangá Livre", "https://mangalivre.net") {
    // Hardcode the id because the language wasn't specific.
    override val id: Long = 4762777556012432014

    override fun popularMangaRequest(page: Int): Request {
        val originalRequestUrl = super.popularMangaRequest(page).url().toString()
        return GET(originalRequestUrl + DEFAULT_TYPE, sourceHeaders)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            return super.searchMangaRequest(page, query, filters)
        }

        val popularRequestUrl = super.popularMangaRequest(page).url().toString()
        val type = filters.filterIsInstance<TypeFilter>()
            .firstOrNull()?.selected?.value ?: DEFAULT_TYPE

        return GET(popularRequestUrl + type, sourceHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request().url().pathSegments().contains("search")) {
            return super.searchMangaParse(response)
        }

        return popularMangaParse(response)
    }

    private fun getContentTypes(): List<ContentType> = listOf(
        ContentType("Mangás", "manga"),
        ContentType("Manhuas", "manhua"),
        ContentType("Webtoons", "webtoon"),
        ContentType("Novels", "novel"),
        ContentType("Todos", "")
    )

    private data class ContentType(val name: String, val value: String) {
        override fun toString() = name
    }

    private class TypeFilter(contentTypes: List<ContentType>) :
        Filter.Select<ContentType>("Tipo de conteúdo", contentTypes.toTypedArray()) {

        val selected: ContentType
            get() = values[state]
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header(FILTER_WARNING),
        TypeFilter(getContentTypes())
    )

    companion object {
        private const val FILTER_WARNING = "O filtro abaixo é ignorado durante a busca!"
        private const val DEFAULT_TYPE = "manga"
    }
}

class Toonei : MangasProject("Toonei", "https://toonei.com") {

    override fun getReaderToken(document: Document): String? {
        return document.select("script:containsData(window.PAGES_KEY)").firstOrNull()
            ?.data()
            ?.substringAfter("\"")
            ?.substringBefore("\";")
    }
}
