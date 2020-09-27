package eu.kanade.tachiyomi.extension.ko.newtoki

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit.DAYS

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
        NewTokiManga(),
        NewTokiWebtoon()
    )
}

class NewTokiManga : NewToki("ManaToki", "https://manatoki$domainNumber.net", "comic") {
    // / ! DO NOT CHANGE THIS !  Only the site name changed from newtoki.
    override val id by lazy { generateSourceId("NewToki", lang, versionId) }
    override val supportsLatest by lazy { getExperimentLatest() }

    // this does 70 request per page....
    override fun latestUpdatesSelector() = ".media.post-list p > a"
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/page/update?hid=update&page=$page")
    override fun latestUpdatesNextPageSelector() = "nav.pg_wrap > .pg > strong"
    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        // given cache time to prevent repeated lots of request in latest.
        val cacheControl = CacheControl.Builder().maxAge(14, DAYS).maxStale(14, DAYS).build()
        val mangas = document.select(latestUpdatesSelector()).map { element ->
            val url = element.attr("abs:href")
            val manga = mangaDetailsParse(client.newCall(GET(url, cache = cacheControl)).execute())
            manga.url = getUrlPath(url)
            manga
        }

        val hasNextPage = try {
            !document.select(popularMangaNextPageSelector()).text().contains("10")
        } catch (_: Exception) {
            false
        }

        return MangasPage(mangas, hasNextPage)
    }

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
    private class SearchPublishTypeList : Filter.Select<String>(
        "Publish",
        arrayOf(
            "전체",
            "미분류",
            "주간",
            "격주",
            "월간",
            "격월/비정기",
            "단편",
            "단행본",
            "완결"
        )
    )

    // [...document.querySelectorAll("form.form td")[3].querySelectorAll("a")].map((el, i) => `"${el.innerText.trim()}"`).join(',\n')
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
            "0-9",
            "a-z"
        )
    )

    // [...document.querySelectorAll("form.form td")[4].querySelectorAll("a")].map((el, i) => `"${el.innerText.trim()}"`).join(',\n')
    private class SearchGenreTypeList : Filter.Select<String>(
        "Genre",
        arrayOf(
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
        )
    )

    override fun getFilterList() = FilterList(
        Filter.Header("Filter can't use with query"),
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
                is SearchTargetTypeList -> {
                    if (filter.state > 0) {
                        url.addQueryParameter("toon", filter.values[filter.state])
                    }
                }
            }
        }

        // Imcompatible with Other Search Parametor
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

    override fun getFilterList() = FilterList(
        SearchTargetTypeList(),
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
