package eu.kanade.tachiyomi.extension.ko.newtoki

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.util.concurrent.TimeUnit

/*
 * ManaToki is too big to support in a Factory File., So split into separate file.
 */

class ManaToki(domainNumber: Long) : NewToki("ManaToki", "https://manatoki$domainNumber.net", "comic") {
    // / ! DO NOT CHANGE THIS !  Only the site name changed from newtoki.
    override val id by lazy { generateSourceId("NewToki", lang, versionId) }
    override val supportsLatest by lazy { getExperimentLatest() }

    override fun latestUpdatesSelector() = ".media.post-list"
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/page/update?hid=update&page=$page")
    override fun latestUpdatesNextPageSelector() = "nav.pg_wrap > .pg > strong"
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        // if this is true, Handle Only 10 mangas with accurate Details per page. (Real Latest Page has 70 mangas.)
        // Else, Parse from Latest page. which is incomplete.
        val isParseWithDetail = getLatestWithDetail()
        val reqPage = if (isParseWithDetail) ((page - 1) / 7 + 1) else page
        return rateLimitedClient.newCall(latestUpdatesRequest(reqPage))
            .asObservableSuccess()
            .map { response ->
                if (isParseWithDetail) latestUpdatesParseWithDetailPage(response, page)
                else latestUpdatesParseWithLatestPage(response)
            }
    }

    private fun latestUpdatesParseWithDetailPage(response: Response, page: Int): MangasPage {
        val document = response.asJsoup()

        // given cache time to prevent repeated lots of request in latest.
        val cacheControl = CacheControl.Builder().maxAge(28, TimeUnit.DAYS).maxStale(28, TimeUnit.DAYS).build()

        val rm = 70 * ((page - 1) / 7)
        val min = (page - 1) * 10 - rm
        val max = page * 10 - rm
        val elements = document.select("${latestUpdatesSelector()} p > a").slice(min until max)
        val mangas = elements.map { element ->
            val url = element.attr("abs:href")
            val manga = mangaDetailsParse(rateLimitedClient.newCall(GET(url, cache = cacheControl)).execute())
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

    private fun latestUpdatesParseWithLatestPage(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesElementParse(element)
        }

        val hasNextPage = try {
            !document.select(popularMangaNextPageSelector()).text().contains("10")
        } catch (_: Exception) {
            false
        }

        return MangasPage(mangas, hasNextPage)
    }

    private fun latestUpdatesElementParse(element: Element): SManga {
        val linkElement = element.select("a.btn-primary")
        val rawTitle = element.select(".post-subject > a").first().ownText().trim()

        // TODO: Make Clear Regex.
        val chapterRegex = Regex("""((?:\s+)(?:(?:(?:[0-9]+권)?(?:[0-9]+부)?(?:[0-9]*?시즌[0-9]*?)?)?(?:\s*)(?:(?:[0-9]+)(?:[-.](?:[0-9]+))?)?(?:\s*[~,]\s*)?(?:[0-9]+)(?:[-.](?:[0-9]+))?)(?:화))""")
        val title = rawTitle.trim().replace(chapterRegex, "")
        // val regexSpecialChapter = Regex("(부록|단편|외전|.+편)")
        // val lastTitleWord = excludeChapterTitle.split(" ").last()
        // val title = excludeChapterTitle.replace(lastTitleWord, lastTitleWord.replace(regexSpecialChapter, ""))

        val manga = SManga.create()
        manga.url = getUrlPath(linkElement.attr("href"))
        manga.title = title
        manga.thumbnail_url = element.select(".img-item > img").attr("src")
        manga.initialized = false
        return manga
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/comic" + (if (page > 1) "/p$page" else ""))!!.newBuilder()

        val genres = mutableListOf<String>()

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
                    filter.state.forEach {
                        if (it.state) {
                            genres.add(it.id)
                        }
                    }
                }

                is SearchSortTypeList -> {
                    url.addQueryParameter("sst", listOf("wr_datetime", "wr_hit", "wr_good", "as_update")[filter.state])
                }

                is SearchOrderTypeList -> {
                    url.addQueryParameter("sod", listOf("desc", "asc")[filter.state])
                }
            }
        }

        if (query.isNotBlank()) {
            url.addQueryParameter("stx", query)

            // Remove some filter QueryParams that not working with query
            url.setQueryParameter("publish", null)
            url.setQueryParameter("jaum", null)

            return GET(url.toString())
        }

        url.addQueryParameter("tag", genres.joinToString(","))
        return GET(url.toString())
    }

    private class SearchCheckBox(name: String, val id: String = name) : Filter.CheckBox(name)

    // [...document.querySelectorAll("form.form td")[3].querySelectorAll("span.btn")].map((el, i) => `"${el.innerText.trim()}"`).join(',\n')
    private class SearchPublishTypeList : Filter.Select<String>(
        "Publish",
        arrayOf(
            "전체",
            "주간",
            "격주",
            "월간",
            "단편",
            "단행본",
            "완결"
        )
    )

    // [...document.querySelectorAll("form.form td")[4].querySelectorAll("span.btn")].map((el, i) => `"${el.innerText.trim()}"`).join(',\n')
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

    // [...document.querySelectorAll("form.form td")[6].querySelectorAll("span.btn")].map((el, i) => `"${el.innerText.trim()}"`).join(',\n')
    private class SearchGenreTypeList : Filter.Group<SearchCheckBox>(
        "Genres",
        arrayOf(
            "전체",
            "17",
            "BL",
            "SF",
            "TS",
            "개그",
            "게임",
            "도박",
            "드라마",
            "라노벨",
            "러브코미디",
            "먹방",
            "백합",
            "붕탁",
            "순정",
            "스릴러",
            "스포츠",
            "시대",
            "애니화",
            "액션",
            "음악",
            "이세계",
            "일상",
            "전생",
            "추리",
            "판타지",
            "학원",
            "호러"
        ).map { SearchCheckBox(it) }
    )

    private class SearchSortTypeList : Filter.Select<String>(
        "Sort",
        arrayOf(
            "기본(날짜순)",
            "인기순",
            "추천순",
            "업데이트순"
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
        Filter.Header("Some filters can't use with query"),
        SearchPublishTypeList(),
        SearchJaumTypeList(),
        SearchSortTypeList(),
        SearchOrderTypeList(),
        SearchGenreTypeList()
    )
}
