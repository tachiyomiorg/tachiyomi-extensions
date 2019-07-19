package eu.kanade.tachiyomi.extension.ru.desu

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class Desu : HttpSource() {
    override val name = "Desu"

    override val baseUrl = "http://desu.me/manga/api"

    override val lang = "ru"

    override val supportsLatest = true

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Tachiyomi")
        add("Referer", baseUrl)
    }

    private fun mangaPageFromJSON(json: String, next: Boolean): MangasPage {
        val arr = JSONArray(json)
        val ret = ArrayList<SManga>(arr.length())
        for (i in 0 until arr.length()) {
            var obj = arr.getJSONObject(i)
            ret.add(SManga.create().apply {
                mangaFromJSON(obj, false)
            })
        }
        return MangasPage(ret, next)
    }

    private fun SManga.mangaFromJSON(obj: JSONObject, chapter: Boolean) {
        val id = obj.getInt("id")
        url = "/$id"
        title = obj.getString("name")
        thumbnail_url = obj.getJSONObject("image").getString("original")
        description = obj.getString("description")
        genre = if (chapter) {
            val jsonArray = obj.getJSONArray("genres")
            var genreList = kotlin.collections.mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                genreList.add(jsonArray.getJSONObject(i).getString("russian"))
            }
            genreList.joinToString()
        } else {
            obj.getString("genres")
        }
        status = when (obj.getString("status")) {
            "ongoing" -> SManga.ONGOING
            "released" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }


    override fun popularMangaRequest(page: Int) = GET("$baseUrl/?limit=50&order=popular&page=$page")

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/?limit=50&order=updated&page=$page")

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = "$baseUrl/?limit=20&page=$page"
        var types = mutableListOf<Type>()
        var genres = mutableListOf<Genre>()
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is OrderBy -> url += "&order=" + arrayOf("popular", "updated", "name")[filter.state]
                is TypeList -> filter.state.forEach { type -> if (type.state) types.add(type) }
                is GenreList -> filter.state.forEach {genre -> if (genre.state) genres.add(genre) }
            }
        }

        if (!types.isEmpty()) {
            url += "&kinds=" + types.joinToString(",") { it.id }
        }
        if (!genres.isEmpty()) {
            url += "&genres=" + genres.joinToString(",") { it.id }
        }
        if (!query.isEmpty()) {
            url += "&search=$query"
        }
        return GET(url)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val res = response.body()!!.string()
        val obj = JSONObject(res).getJSONArray("response")
        val nav = JSONObject(res).getJSONObject("pageNavParams")
        val count = nav.getInt("count")
        val limit = nav.getInt("limit")
        val page = nav.getInt("page")
        return mangaPageFromJSON(obj.toString(), count > page*limit)
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val obj = JSONObject(response.body()!!.string()).getJSONObject("response")
        mangaFromJSON(obj, true)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val obj = JSONObject(response.body()!!.string()).getJSONObject("response")
        val ret = ArrayList<SChapter>()

        val cid = obj.getInt("id")

        val arr = obj.getJSONObject("chapters").getJSONArray("list")
        for (i in 0 until arr.length()) {
            val obj2 = arr.getJSONObject(i)
            ret.add(SChapter.create().apply {
                val ch = obj2.getString("ch")
                val title = if (obj2.getString("title") == "null") "" else obj2.getString("title")
                if (title.isEmpty()) {
                    name = "Глава $ch"
                } else {
                    name = "$ch - $title"
                }
                val id = obj2.getString("id")
                url = "/$cid/chapter/$id"
                chapter_number = ch.toFloat()
                date_upload = obj2.getLong("date")*1000
            })
        }
        return ret
    }

    override fun pageListParse(response: Response): List<Page> {
        val obj = JSONObject(response.body()!!.string()).getJSONObject("response")
        val pages = obj.getJSONObject("pages")
        val list = pages.getJSONArray("list")
        val ret = ArrayList<Page>(list.length())
        for (i in 0 until list.length()) {
            ret.add(Page(i, "", list.getJSONObject(i).getString("img")))
        }
        return ret
    }

    override fun imageUrlParse(response: Response)
            = throw UnsupportedOperationException("This method should not be called!")

    private class OrderBy : Filter.Select<String>("Сортировка",
            arrayOf("Популярность", "Дата", "Имя"))

    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Жанр", genres)
    private class TypeList(types: List<Type>) : Filter.Group<Type>("Тип", types)

    private class Type(name: String, val id: String) : Filter.CheckBox(name)
    private class Genre(name: String, val id: String) : Filter.CheckBox(name)
    override fun getFilterList() = FilterList(
            OrderBy(),
            TypeList(getTypeList()),
            GenreList(getGenreList())
    )

    private fun getTypeList() = listOf(
            Type("Манга", "manga"),
            Type("Манхва", "manhwa"),
            Type("Маньхуа", "manhua"),
            Type("Ваншот", "one_shot"),
            Type("Комикс", "comics")
    )

    private fun getGenreList() = listOf(
            Genre("Безумие", "Dementia"),
            Genre("Боевые искусства", "Martial Arts"),
            Genre("Вампиры", "Vampire"),
            Genre("Военное", "Military"),
            Genre("Гарем", "Harem"),
            Genre("Демоны", "Demons"),
            Genre("Детектив", "Mystery"),
            Genre("Детское", "Kids"),
            Genre("Дзёсей", "Josei"),
            Genre("Додзинси", "Doujinshi"),
            Genre("Драма", "Drama"),
            Genre("Игры", "Game"),
            Genre("Исторический", "Historical"),
            Genre("Комедия", "Comedy"),
            Genre("Космос", "Space"),
            Genre("Магия", "Magic"),
            Genre("Машины", "Cars"),
            Genre("Меха", "Mecha"),
            Genre("Музыка", "Music"),
            Genre("Пародия", "Parody"),
            Genre("Повседневность", "Slice of Life"),
            Genre("Полиция", "Police"),
            Genre("Приключения", "Adventure"),
            Genre("Психологическое", "Psychological"),
            Genre("Романтика", "Romance"),
            Genre("Самураи", "Samurai"),
            Genre("Сверхъестественное", "Supernatural"),
            Genre("Сёдзе", "Shoujo"),
            Genre("Сёдзе Ай", "Shoujo Ai"),
            Genre("Сейнен", "Seinen"),
            Genre("Сёнен", "Shounen"),
            Genre("Сёнен Ай", "Shounen Ai"),
            Genre("Смена пола", "Gender Bender"),
            Genre("Спорт", "Sports"),
            Genre("Супер сила", "Super Power"),
            Genre("Триллер", "Thriller"),
            Genre("Ужасы", "Horror"),
            Genre("Фантастика", "Sci-Fi"),
            Genre("Фэнтези", "Fantasy"),
            Genre("Хентай", "Hentai"),
            Genre("Школа", "School"),
            Genre("Экшен", "Action"),
            Genre("Этти", "Ecchi"),
            Genre("Юри", "Yuri"),
            Genre("Яой", "Yaoi")
    )
}
