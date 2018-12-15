package eu.kanade.tachiyomi.extension.ko.mangashowme

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl
import okhttp3.Request


// TODO: Completely Implement/Update Filters(Genre/Artist).
// private class TextField(name: String, val key: String) : Filter.Text(name)
private class SearchCheckBox(val id: Int, name: String) : Filter.CheckBox(name)

private class SearchFieldMatch : Filter.Select<String>("Search Match", arrayOf("Not Set", "AND", "OR"))
private class SearchTagMatch : Filter.Select<String>("Tag Match", arrayOf("AND", "OR"))
private class SearchGenresList(genres: List<SearchCheckBox>) : Filter.Group<SearchCheckBox>("Genres", genres)
private class SearchNamingList(naming: List<SearchCheckBox>) : Filter.Group<SearchCheckBox>("Naming", naming)
private class SearchStatusList(status: List<SearchCheckBox>) : Filter.Group<SearchCheckBox>("Status", status)

private fun searchNaming() = listOf(
        SearchCheckBox(0, "ㄱ"),
        SearchCheckBox(1, "ㄲ"),
        SearchCheckBox(2, "ㄴ"),
        SearchCheckBox(3, "ㄷ"),
        SearchCheckBox(4, "ㄸ"),
        SearchCheckBox(5, "ㄹ"),
        SearchCheckBox(6, "ㅁ"),
        SearchCheckBox(7, "ㅂ"),
        SearchCheckBox(8, "ㅃ"),
        SearchCheckBox(9, "ㅅ"),
        SearchCheckBox(10, "ㅆ"),
        SearchCheckBox(11, "ㅇ"),
        SearchCheckBox(12, "ㅈ"),
        SearchCheckBox(13, "ㅉ"),
        SearchCheckBox(14, "ㅊ"),
        SearchCheckBox(15, "ㅋ"),
        SearchCheckBox(16, "ㅌ"),
        SearchCheckBox(17, "ㅍ"),
        SearchCheckBox(18, "ㅎ"),
        SearchCheckBox(19, "A-Z"),
        SearchCheckBox(20, "0-9")
)

private fun searchStatus() = listOf(
        SearchCheckBox(0, "미분류"),
        SearchCheckBox(1, "주간"),
        SearchCheckBox(2, "격주"),
        SearchCheckBox(3, "월간"),
        SearchCheckBox(4, "격월/비정기"),
        SearchCheckBox(5, "단편"),
        SearchCheckBox(6, "단행본"),
        SearchCheckBox(7, "완결")
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
        SearchNamingList(searchNaming()),
        SearchStatusList(searchStatus()),
        SearchGenresList(searchGenres()),
        Filter.Separator(),
        SearchFieldMatch(),
        SearchTagMatch()
        //Filter.Separator(),
        //TextField("Author/Artist (Accurate full name)", "author")
)

fun searchComplexFilterMangaRequestBuilder(baseUrl: String, page: Int, query: String, filters: FilterList): Request {
    // normal search function.
    fun normalSearch(state: Int = 0): Request {
        val url = HttpUrl.parse("$baseUrl/bbs/search.php?url=$baseUrl/bbs/search.php")!!.newBuilder()

        if (state > 0) {
            url.addQueryParameter("sop", arrayOf("and", "or")[state - 1])
        }

        url.addQueryParameter("stx", query)

        if (page > 1) {
            url.addQueryParameter("page", "${page - 1}")
        }

        return GET(url.toString())
    }

    val nameFilter = mutableListOf<Int>()
    val statusFilter = mutableListOf<Int>()
    val genresFilter = mutableListOf<String>()
    var matchFieldFilter = 0
    var matchTagFilter = 1

    filters.forEach { filter ->
        when (filter) {
            is SearchFieldMatch -> {
                matchFieldFilter = filter.state
            }
        }
    }

    filters.forEach { filter ->
        when (filter) {
            is SearchTagMatch -> {
                if (filter.state > 0) {
                    matchTagFilter = filter.state + 1
                }
            }

            is SearchNamingList -> {
                filter.state.forEach {
                    if (it.state) {
                        nameFilter.add(it.id)
                    }
                }
            }

            is SearchStatusList -> {
                filter.state.forEach {
                    if (it.state) {
                        statusFilter.add(it.id)
                    }
                }
            }

            is SearchGenresList -> {
                filter.state.forEach {
                    if (it.state) {
                        genresFilter.add(it.name)
                    }
                }
            }

//            is TextField -> {
//                if (type == 4 && filter.key == "author") {
//                    if (filter.key.length > 1) {
//                        return GET("$baseUrl/bbs/page.php?hid=manga_list&sfl=4&stx=${filter.state}")
//                    }
//                }
//            }
        }
    }

    // If Query is over 2 length, just go to normal search
    if (query.length > 1) {
        return normalSearch(matchFieldFilter)
    }

    if (nameFilter.isEmpty() && statusFilter.isEmpty() && genresFilter.isEmpty()) {
        return GET("$baseUrl/bbs/page.php?hid=manga_list")
    }

    val url = HttpUrl.parse("$baseUrl/bbs/page.php?hid=manga_list")!!.newBuilder()
    url.addQueryParameter("search_type", matchTagFilter.toString())
    url.addQueryParameter("_1", nameFilter.joinToString(","))
    url.addQueryParameter("_2", statusFilter.joinToString(","))
    url.addQueryParameter("_3", genresFilter.joinToString(","))
    if (page > 1) {
        url.addQueryParameter("page", "${page - 1}")
    }

    return GET(url.toString())
}