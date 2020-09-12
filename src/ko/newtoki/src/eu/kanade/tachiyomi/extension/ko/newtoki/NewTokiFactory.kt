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
 * This is by the head of Manamoa, as they decided to move to Newtoki.
 */
private val domainNumber = 33 + ((Date().time - SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2019-11-14")!!.time) / 595000000)

class NewTokiFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
            NewTokiManga(),
            NewTokiWebtoon()
    )
}

class NewTokiManga : NewToki("ManaToki", "https://manatoki$domainNumber.net", "comic") {
    // / ! DO NOT CHANGE THIS !  Only the site name changed from newtoki.
    override val id by lazy { generateSourceId("NewToki", lang, versionId) }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/comic" + (if (page > 1) "/p$page" else ""))!!.newBuilder()

        if (!query.isBlank()) {
            url.addQueryParameter("stx", query)
            return GET(url.toString())
        }

        filters.forEach { filter ->
            when (filter) {
                is SearchPublishTypeList -> {
                    if (filter.state > 0) {
                        url.addQueryParameter("publish", filter.values[filter.state])
                    }
                }

                is SearchJaumTypeList -> {
                    if (filter.state > 0) {
                        url.addQueryParameter("jaum", filter.values[filter.state])
                    }
                }

                is SearchGenreTypeList -> {
                    if (filter.state > 0) {
                        url.addQueryParameter("tag", filter.values[filter.state])
                    }
                }
            }
        }

        return GET(url.toString())
    }

    // [...document.querySelectorAll("form.form td")[2].querySelectorAll("a")].map((el, i) => `"${el.innerText.trim()}"`).join(',\n')
    private class SearchPublishTypeList : Filter.Select<String>("Publish", arrayOf(
        "전체",
        "미분류",
        "주간",
        "격주",
        "월간",
        "격월/비정기",
        "단편",
        "단행본",
        "완결"
    ))

    // [...document.querySelectorAll("form.form td")[3].querySelectorAll("a")].map((el, i) => `"${el.innerText.trim()}"`).join(',\n')
    private class SearchJaumTypeList : Filter.Select<String>("Jaum", arrayOf(
        "전체",
        "ㄱ",
        "ㄴ",
        "ㄷ",
        "ㄹ",
        "ㅁ",
        "ㅂ",
        "ㅅ",
        "ㅇ",
        "ㅈ",
        "ㅊ",
        "ㅋ",
        "ㅌ",
        "ㅍ",
        "ㅎ",
        "0-9",
        "a-z"
    ))

    // [...document.querySelectorAll("form.form td")[4].querySelectorAll("a")].map((el, i) => `"${el.innerText.trim()}"`).join(',\n')
    private class SearchGenreTypeList : Filter.Select<String>("Genre", arrayOf(
        "전체",
        "17",
        "BL",
        "SF",
        "TS",
        "개그",
        "게임",
        "공포",
        "도박",
        "드라마",
        "라노벨",
        "러브코미디",
        "로맨스",
        "먹방",
        "미스터리",
        "백합",
        "붕탁",
        "성인",
        "순정",
        "스릴러",
        "스포츠",
        "시대",
        "애니화",
        "액션",
        "역사",
        "음악",
        "이세계",
        "일상",
        "일상+치유",
        "전생",
        "추리",
        "판타지",
        "학원",
        "호러"
    ))

    override fun getFilterList() = FilterList(
        SearchPublishTypeList(),
        SearchJaumTypeList(),
        SearchGenreTypeList()
    )
}

class NewTokiWebtoon : NewToki("NewToki", "https://newtoki$domainNumber.com", "webtoon") {
    // / ! DO NOT CHANGE THIS !  Prevent to treating as a new site
    override val id by lazy { generateSourceId("NewToki (Webtoon)", lang, versionId) }

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

fun generateSourceId(name: String, lang: String, versionId: Int): Long {
    val key = "${name.toLowerCase()}/$lang/$versionId"
    val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
    return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
}
