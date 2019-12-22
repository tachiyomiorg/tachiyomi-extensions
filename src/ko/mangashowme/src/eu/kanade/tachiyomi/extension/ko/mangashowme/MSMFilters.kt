package eu.kanade.tachiyomi.extension.ko.mangashowme

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl
import okhttp3.Request


// TODO: Completely Implement/Update Filters(Genre/Artist).
private class TextField(name: String, val key: String) : Filter.Text(name)

private class SearchCheckBox(name: String, val id: String = name) : Filter.CheckBox(name)

private class SearchMatch : Filter.Select<String>("Match", arrayOf("AND", "OR"))
private class SearchGenresList(genres: List<SearchCheckBox>) : Filter.Group<SearchCheckBox>("Genres", genres)
private class SearchNamingList : Filter.Select<String>("Naming", searchNaming())
private class SearchStatusList : Filter.Select<String>("Status", searchStatus())

// [`"Not Set"`, ...[...document.querySelectorAll(".categories ul[data-type='1'] li")].map((el, i) => `"${el.innerText.trim()}"`)].join(',\n')
private fun searchNaming() = arrayOf(
        "Not Set",
        "ㄱ",
        "ㄲ",
        "ㄴ",
        "ㄷ",
        "ㄸ",
        "ㄹ",
        "ㅁ",
        "ㅂ",
        "ㅃ",
        "ㅅ",
        "ㅆ",
        "ㅇ",
        "ㅈ",
        "ㅉ",
        "ㅊ",
        "ㅋ",
        "ㅌ",
        "ㅍ",
        "ㅎ",
        "A-Z",
        "0-9"
)

// [`"Not Set"`, ...[...document.querySelectorAll(".categories ul[data-type='2'] li")].map((el, i) => `"${el.innerText.trim()}"`)].join(',\n')
private fun searchStatus() = arrayOf(
        "Not Set",
        "주간",
        "격주",
        "월간",
        "격월/비정기",
        "단편",
        "단행본",
        "완결"
)

// [...document.querySelectorAll(".categories ul[data-type='3'] li")].map((el, i) => `SearchCheckBox("${el.innerText.trim()}")`).join(',\n')
private fun searchGenres() = listOf(
        SearchCheckBox("17"),
        SearchCheckBox("BL"),
        SearchCheckBox("SF"),
        SearchCheckBox("TS"),
        SearchCheckBox("개그"),
        SearchCheckBox("게임"),
        SearchCheckBox("공포"),
        SearchCheckBox("도박"),
        SearchCheckBox("드라마"),
        SearchCheckBox("라노벨"),
        SearchCheckBox("러브코미디"),
        SearchCheckBox("먹방"),
        SearchCheckBox("백합"),
        SearchCheckBox("붕탁"),
        SearchCheckBox("순정"),
        SearchCheckBox("스릴러"),
        SearchCheckBox("스포츠"),
        SearchCheckBox("시대"),
        SearchCheckBox("애니화"),
        SearchCheckBox("액션"),
        SearchCheckBox("음악"),
        SearchCheckBox("이세계"),
        SearchCheckBox("일상"),
        SearchCheckBox("전생"),
        SearchCheckBox("추리"),
        SearchCheckBox("판타지"),
        SearchCheckBox("학원"),
        SearchCheckBox("호러")
)

fun getFilters() = FilterList(
        SearchNamingList(),
        SearchStatusList(),
        SearchGenresList(searchGenres()),
        Filter.Separator(),
        SearchMatch(),
        Filter.Separator(),
        TextField("Author/Artist (Exact search)", "author")
)

fun searchComplexFilterMangaRequestBuilder(baseUrl: String, page: Int, query: String, filters: FilterList): Request {
    var nameFilter: Int? = null
    var statusFilter: Int? = null
    val genresFilter = mutableListOf<String>()
    var matchFilter = 1
    var authorFilter: String? = null

    filters.forEach { filter ->
        when (filter) {
            is SearchMatch -> {
                matchFilter = filter.state + 1
            }

            is TextField -> {
                if (filter.key == "author" && !filter.state.isEmpty()) {
                    authorFilter = filter.state
                }
            }
        }
    }

    if (!authorFilter.isNullOrEmpty()) {
        return GET("$baseUrl/bbs/page.php?hid=manga_list&sfl=4&stx=$authorFilter&page=${page - 1}")
    }

    filters.forEach { filter ->
        when (filter) {
            is SearchNamingList -> {
                if (filter.state > 0) {
                    nameFilter = filter.state - 1
                }
            }

            is SearchStatusList -> {
                if (filter.state > 0) {
                    statusFilter = filter.state
                }
            }

            is SearchGenresList -> {
                filter.state.forEach {
                    if (it.state) {
                        genresFilter.add(it.id)
                    }
                }
            }
        }
    }

    if (query.isEmpty() && nameFilter == null && statusFilter == null && genresFilter.isEmpty()) {
        return GET("$baseUrl/bbs/page.php?hid=manga_list")
    }

    val url = HttpUrl.parse("$baseUrl/bbs/page.php?hid=manga_list")!!.newBuilder()
    url.addQueryParameter("search_type", matchFilter.toString())
    url.addQueryParameter("_0", query)
    url.addQueryParameter("_1", nameFilter?.toString() ?: "")
    url.addQueryParameter("_2", statusFilter?.toString() ?: "")
    url.addQueryParameter("_3", genresFilter.joinToString(","))
    if (page > 1) {
        url.addQueryParameter("page", "${page - 1}")
    }

    return GET(url.toString())
}
