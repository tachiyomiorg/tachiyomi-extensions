package eu.kanade.tachiyomi.extension.ko.newtoki

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Source changes domain names every few days (e.g. newtoki31.net to newtoki32.net)
 * The domain name was newtoki32 on 2019-11-14, this attempts to match the rate at which the domain changes
 *
 * Since 2020-09-20, They changed manga side to Manatoki.
 * It was merged after shutdown of ManaMoa.
 * This is by the head of Manamoa, as thety decided to move to Newtoki.
 */
private val domainNumber = 32 + ((Date().time - SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2019-11-14")!!.time) / 595000000)

class NewTokiFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
            NewTokiManga(),
            NewTokiWebtoon()
    )
}

class NewTokiManga : NewToki("ManaToki", "https://manatoki$domainNumber.net", "comic") {
    override val id by lazy {
        // / ! DO NOT CHANGE THIS !  Only the site name changed from newtoki.
        val oldName = "NewToki"
        val key = "${oldName.toLowerCase()}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }
}

class NewTokiWebtoon : NewToki("NewToki", "https://newtoki$domainNumber.com", "webtoon") {
    override val id by lazy {
        // / ! DO NOT CHANGE THIS !  Prevent to treating as a new site
        val oldName = "NewToki (Webtoon)"
        val key = "${oldName.toLowerCase()}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

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

    private val htmlDataRegex = Regex("""html_data\+='([^']+)'""")

    override fun pageListParse(document: Document): List<Page> {
        val script = document.select("script:containsData(html_data)").firstOrNull()?.data() ?: throw Exception("script not found")

        return htmlDataRegex.findAll(script).map { it.groupValues[1] }
            .asIterable()
            .flatMap { it.split(".") }
            .joinToString("") { it.toIntOrNull(16)?.toChar()?.toString() ?: "" }
            .let { Jsoup.parse(it) }
            .select("img[alt]")
            .mapIndexed { i, img -> Page(i, "", img.attr("abs:data-original")) }
    }

    private class SearchTypeList : Filter.Select<String>("Type", arrayOf("전체", "일반웹툰", "성인웹툰", "BL/GL", "완결웹툰"))

    override fun getFilterList() = FilterList(
            SearchTypeList()
    )
}
