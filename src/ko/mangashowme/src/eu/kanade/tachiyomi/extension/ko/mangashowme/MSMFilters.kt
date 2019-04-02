package eu.kanade.tachiyomi.extension.ko.mangashowme

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl
import okhttp3.Request


// TODO: Completely Implement/Update Filters(Genre/Artist).
private class TextField(name: String, val key: String) : Filter.Text(name)
private class SearchCheckBox(val id: Int, name: String) : Filter.CheckBox(name)

private class SearchMatch : Filter.Select<String>("Match", arrayOf("AND", "OR"))
private class SearchGenresList(genres: List<SearchCheckBox>) : Filter.Group<SearchCheckBox>("Genres", genres)
private class SearchNamingList : Filter.Select<String>("Naming", searchNaming())
private class SearchStatusList : Filter.Select<String>("Status", searchStatus())

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

private fun searchStatus() = arrayOf(
        "Not Set",
        "미분류",
        "주간",
        "격주",
        "월간",
        "격월/비정기",
        "단편",
        "단행본",
        "완결"
)

private fun searchGenres() = listOf(
        SearchCheckBox(0, "17"),
        SearchCheckBox(0, "BL"),
        SearchCheckBox(0, "SF"),
        SearchCheckBox(0, "TS"),
        SearchCheckBox(0, "개그"),
        SearchCheckBox(0, "게임"),
        SearchCheckBox(0, "공포"),
        SearchCheckBox(0, "도박"),
        SearchCheckBox(0, "드라마"),
        SearchCheckBox(0, "라노벨"),
        SearchCheckBox(0, "러브코미디"),
        SearchCheckBox(0, "로맨스"),
        SearchCheckBox(0, "먹방"),
        SearchCheckBox(0, "백합"),
        SearchCheckBox(0, "붕탁"),
        SearchCheckBox(0, "순정"),
        SearchCheckBox(0, "스릴러"),
        SearchCheckBox(0, "스포츠"),
        SearchCheckBox(0, "시대"),
        SearchCheckBox(0, "애니화"),
        SearchCheckBox(0, "액션"),
        SearchCheckBox(0, "역사"),
        SearchCheckBox(0, "요리"),
        SearchCheckBox(0, "음악"),
        SearchCheckBox(0, "이세계"),
        SearchCheckBox(0, "일상"),
        SearchCheckBox(0, "전생"),
        SearchCheckBox(0, "추리"),
        SearchCheckBox(0, "판타지"),
        SearchCheckBox(0, "학원"),
        SearchCheckBox(0, "호러")
)

fun getFilters() = FilterList(
        SearchNamingList(),
        SearchStatusList(),
        SearchGenresList(searchGenres()),
        Filter.Separator(),
        SearchMatch(),
        Filter.Separator(),
        TextField("Author/Artist (Exact Search)", "author")
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
                matchFilter = filter.state
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
                    statusFilter = filter.state - 1
                }
            }

            is SearchGenresList -> {
                filter.state.forEach {
                    if (it.state) {
                        genresFilter.add(it.name)
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