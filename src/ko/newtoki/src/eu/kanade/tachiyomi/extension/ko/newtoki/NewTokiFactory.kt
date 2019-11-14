package eu.kanade.tachiyomi.extension.ko.newtoki

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

/**
 * Source changes domain names approximately once every 10 days (e.g. newtoki31.net to newtoki32.net)
 * The domain name was newtoki32 on 2019-11-14, this should increment that by 1 for every 10 days that pass
 * If that rate holds and the code is correct, this should be accurate for a good while
 */
private val domainNumber = 32 + ((Date().time - SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2019-11-14").time) / 864000000)

class NewTokiFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
            NewTokiManga(),
            NewTokiWebtoon()
    )
}

class NewTokiManga : NewToki("NewToki", "https://newtoki$domainNumber.net", "comic")

class NewTokiWebtoon : NewToki("NewToki (Webtoon)", "https://newtoki$domainNumber.com", "webtoon") {
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
