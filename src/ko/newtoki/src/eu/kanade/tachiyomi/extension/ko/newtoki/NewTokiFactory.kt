package eu.kanade.tachiyomi.extension.ko.newtoki

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl
import okhttp3.Request

private const val baseDomain = "newtoki10"

class NewTokiFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
            NewTokiManga(),
            NewTokiWebtoon()
    )
}

class NewTokiManga : NewToki("NewToki", "https://$baseDomain.net", "comic")

class NewTokiWebtoon : NewToki("NewToki (Webtoon)", "https://$baseDomain.com", "webtoon") {
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/webtoon" + (if (page > 1) "/p$page" else ""))!!.newBuilder()
        filters.forEach { filter ->
            when (filter) {
                is SearchTypeList -> {
                    if (filter.state > 0) {
                        url.addQueryParameter("toon", filter.values[filter.state])
                    }
                }
            }
        }

        if (!query.isBlank()) {
            url.addQueryParameter("stx", query)
        }

        return GET(url.toString())
    }

    private class SearchTypeList : Filter.Select<String>("Type", arrayOf("전체", "일반웹툰", "성인웹툰", "BL/GL", "완결웹툰"))

    override fun getFilterList() = FilterList(
            SearchTypeList()
    )
}