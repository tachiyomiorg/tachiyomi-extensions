package eu.kanade.tachiyomi.extension.pt.mangalivre

import eu.kanade.tachiyomi.multisrc.mangasproject.MangasProject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Request
import okhttp3.Response
import okhttp3.OkHttpClient
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import java.util.concurrent.TimeUnit

class MangaLivre : MangasProject("Mangá Livre", "https://mangalivre.net", "pt-br") {

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
