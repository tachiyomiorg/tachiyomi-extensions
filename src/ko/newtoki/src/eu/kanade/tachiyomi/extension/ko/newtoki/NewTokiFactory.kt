package eu.kanade.tachiyomi.extension.ko.newtoki

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl
import okhttp3.Request
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Source changes domain names every few days (e.g. newtoki31.net to newtoki32.net)
 * The domain name was newtoki32 on 2019-11-14, this attempts to match the rate at which the domain changes
 *
 * Since 2020-09-20, They changed manga side to Manatoki.
 * It was merged after shutdown of ManaMoa.
 * This is by the head of Manamoa, as they decided to move to Newtoki.
 */
private val domainNumber = 32 + ((Date().time - SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2019-11-14")!!.time) / 595000000)

class NewTokiFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        ManaToki(domainNumber),
        NewTokiWebtoon()
    )
}

class NewTokiWebtoon : NewToki("NewToki", "https://newtoki$domainNumber.com", "webtoon") {
    // / ! DO NOT CHANGE THIS !  Prevent to treating as a new site
    override val id by lazy { generateSourceId("NewToki (Webtoon)", lang, versionId) }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/webtoon" + (if (page > 1) "/p$page" else ""))!!.newBuilder()
        filters.forEach { filter ->
            when (filter) {
                is SearchTargetTypeList -> {
                    if (filter.state > 0) {
                        url.addQueryParameter("toon", filter.values[filter.state])
                    }
                }

                is SearchSortTypeList -> {
                    url.addQueryParameter("sst", listOf("as_update", "wr_hit", "wr_good")[filter.state])
                }

                is SearchOrderTypeList -> {
                    url.addQueryParameter("sod", listOf("desc", "asc")[filter.state])
                }
            }
        }

        // Incompatible with Other Search Parameter
        if (!query.isBlank()) {
            url.addQueryParameter("stx", query)
        } else {
            filters.forEach { filter ->
                when (filter) {
                    is SearchYoilTypeList -> {
                        if (filter.state > 0) {
                            url.addQueryParameter("yoil", filter.values[filter.state])
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
        }

        return GET(url.toString())
    }

    private class SearchTargetTypeList : Filter.Select<String>("Type", arrayOf("전체", "일반웹툰", "성인웹툰", "BL/GL", "완결웹툰"))

    // [...document.querySelectorAll("form.form td")[1].querySelectorAll("a")].map((el, i) => `"${el.innerText.trim()}"`).join(',\n')
    private class SearchYoilTypeList : Filter.Select<String>(
        "Day of the Week",
        arrayOf(
            "전체",
            "월",
            "화",
            "수",
            "목",
            "금",
            "토",
            "일",
            "열흘"
        )
    )

    // [...document.querySelectorAll("form.form td")[2].querySelectorAll("a")].map((el, i) => `"${el.innerText.trim()}"`).join(',\n')
    private class SearchJaumTypeList : Filter.Select<String>(
        "Jaum",
        arrayOf(
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
            "a-z",
            "0-9"
        )
    )
    // [...document.querySelectorAll("form.form td")[3].querySelectorAll("a")].map((el, i) => `"${el.innerText.trim()}"`).join(',\n')
    private class SearchGenreTypeList : Filter.Select<String>(
        "Genre",
        arrayOf(
            "전체",
            "판타지",
            "액션",
            "개그",
            "미스터리",
            "로맨스",
            "드라마",
            "무협",
            "스포츠",
            "일상",
            "학원",
            "성인"
        )
    )

    private class SearchSortTypeList : Filter.Select<String>(
        "Sort",
        arrayOf(
            "기본(업데이트순)",
            "인기순",
            "추천순",
        )
    )

    private class SearchOrderTypeList : Filter.Select<String>(
        "Order",
        arrayOf(
            "Descending",
            "Ascending"
        )
    )

    override fun getFilterList() = FilterList(
        SearchTargetTypeList(),
        SearchSortTypeList(),
        SearchOrderTypeList(),
        Filter.Separator(),
        Filter.Header("Under 3 Filters can't use with query"),
        SearchYoilTypeList(),
        SearchJaumTypeList(),
        SearchGenreTypeList()
    )
}

fun generateSourceId(name: String, lang: String, versionId: Int): Long {
    val key = "${name.toLowerCase()}/$lang/$versionId"
    val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
    return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
}
