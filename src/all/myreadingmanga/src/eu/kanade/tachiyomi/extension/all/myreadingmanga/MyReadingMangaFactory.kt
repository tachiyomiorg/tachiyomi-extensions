package eu.kanade.tachiyomi.extension.all.myreadingmanga

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.Request
import okhttp3.Response

class MyReadingMangaFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(MyReadingManga("en", "English")) +
        moreLanguagesList.map { MoreMRMLanguages(it.first, it.second) }
}

private val moreLanguagesList = listOf(
    Pair("fr", "French")
)

/**
 * Incorporating more languages in to search got to be a bit of a pain
 * So leaving search mostly as-is for English, using a new class for other languages
 */
class MoreMRMLanguages(tachiLang: String, private val siteLang: String) : MyReadingManga(tachiLang, siteLang) {
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var fqIndex = 0
        val uri = Uri.parse("$baseUrl/search/").buildUpon()
                .appendQueryParameter("wpsolr_q", query)
                .appendQueryParameter("wpsolr_fq[$fqIndex]", "lang_str:$siteLang")
                .appendQueryParameter("wpsolr_page", page.toString())
            filters.forEach {
                if (it is UriFilter) {
                    fqIndex++
                    it.addToUri(uri, "wpsolr_fq[$fqIndex]")
                }
            }
        return GET(uri.toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        // Filter Assist - Caches Pages required for filter parsing
        if (!filtersCached) {
            filterAssist(baseUrl)
            filterAssist("$baseUrl/cats/")
            filtersCached = true
        }
        return popularMangaParse(response)
    }

    override fun getFilterList(): FilterList {
        return FilterList(
            GenreFilter(returnFilter(getCache(baseUrl), ".tagcloud a[href*=/genre/]", "href")),
            TagFilter(returnFilter(getCache(baseUrl), ".tagcloud a[href*=/tag/]", "href")),
            CatFilter(returnFilter(getCache("$baseUrl/cats/"), ".links a", "abs:href"))
        )
    }

    private class GenreFilter(GENRES: Array<Pair<String, String>>) : UriSelectFilter("Genre", "genre_str", arrayOf(Pair("", "Any"), *GENRES))
    private class TagFilter(POPTAG: Array<Pair<String, String>>) : UriSelectFilter("Popular Tags", "tags", arrayOf(Pair("", "Any"), *POPTAG))
    private class CatFilter(CATID: Array<Pair<String, String>>) : UriSelectFilter("Categories", "categories", arrayOf(Pair("", "Any"), *CATID))

    private open class UriSelectFilter(
        displayName: String,
        val uriValuePrefix: String,
        val vals: Array<Pair<String, String>>,
        val firstIsUnspecified: Boolean = true,
        defaultValue: Int = 0
    ) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue), UriFilter {
        override fun addToUri(uri: Uri.Builder, uriParam: String) {
            if (state != 0 || !firstIsUnspecified)
                uri.appendQueryParameter(uriParam, "$uriValuePrefix:${vals[state].second}")
        }
    }

    private interface UriFilter {
        fun addToUri(uri: Uri.Builder, uriParam: String)
    }
}
