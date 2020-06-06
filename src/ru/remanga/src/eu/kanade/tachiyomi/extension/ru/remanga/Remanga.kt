package eu.kanade.tachiyomi.extension.ru.remanga

import BookDto
import BranchesDto
import GenresDto
import LibraryDto
import MangaDetDto
import PageDto
import PageWrapperDto
import SeriesWrapperDto
import UserDto
import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.PreferenceScreen
import android.widget.Toast
import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.Jsoup
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Remanga : ConfigurableSource, HttpSource() {
    override val name = "Remanga"

    override val baseUrl = "https://remanga.org"

    override val lang = "ru"

    override val supportsLatest = true

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Tachiyomi")
        add("Referer", baseUrl)
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client: OkHttpClient =
        network.client.newBuilder()
            .authenticator { _, response ->
                if (response.request().header("Authorization") != null) {
                    null // Give up, we've already failed to authenticate.
                } else {
                    if (username.isEmpty() or password.isEmpty()) {
                        return@authenticator null
                    }
                    val token = this.login(username, password)
                    response.request().newBuilder()
                        .addHeader("Authorization", "bearer $token")
                        .build()
                }
            }
            .build()

    private val count = 30

    private var branches = mutableMapOf<String, List<BranchesDto>>()

    private fun login(username: String, password: String): String {
        val jsonObject = JSONObject()
        jsonObject.put("user", username)
        jsonObject.put("password", password)
        val body = RequestBody.create(MEDIA_TYPE, jsonObject.toString())
        val response = client.newCall(POST("$baseUrl/api/users/login/", headers, body)).execute()
        val user = gson.fromJson<SeriesWrapperDto<UserDto>>(response.body()?.charStream()!!)
        return user.content.access_token
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/api/search/catalog/?ordering=rating&count=$count&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/titles/last-chapters/?page=$page&count=$count", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaParse(response: Response): MangasPage {
        val page = gson.fromJson<PageWrapperDto<LibraryDto>>(response.body()?.charStream()!!)
        val mangas = page.content.map {
            it.toSManga()
        }
        return MangasPage(mangas, page.props.page < page.props.total_pages)
    }

    private fun LibraryDto.toSManga(): SManga =
        SManga.create().apply {
            title = en_name
            url = "/api/titles/$dir/"
            thumbnail_url = "$baseUrl/${img.high}"
        }

    private fun parseDate(date: String?): Long =
        if (date == null)
            Date().time
        else {
            try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(date).time
            } catch (ex: Exception) {
                try {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S", Locale.US).parse(date).time
                } catch (ex: Exception) {
                    Date().time
                }
            }
        }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = HttpUrl.parse("$baseUrl/api/search/catalog/?page=$page")!!.newBuilder()
        if (query.isNotEmpty()) {
            url = HttpUrl.parse("$baseUrl/api/search/?page=$page")!!.newBuilder()
            url.addQueryParameter("query", query)
        }
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is OrderBy -> {
                    val ord = arrayOf("id", "chapter_date", "rating", "votes", "views", "random")[filter.state!!.index]
                    url.addQueryParameter("ordering", if (filter.state!!.ascending) "-$ord" else ord)
                }
                is CategoryList -> filter.state.forEach { category ->
                    if (category.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(if (category.isIncluded()) "categories" else "exclude_categories", category.id)
                    }
                }
                is TypeList -> filter.state.forEach { type ->
                    if (type.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(if (type.isIncluded()) "types" else "exclude_types", type.id)
                    }
                }
                is StatusList -> filter.state.forEach { status ->
                    if (status.state) {
                        url.addQueryParameter("status", status.id)
                    }
                }
                is AgeList -> filter.state.forEach { age ->
                    if (age.state) {
                        url.addQueryParameter("age_limit", age.id)
                    }
                }
                is GenreList -> filter.state.forEach { genre ->
                    if (genre.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(if (genre.isIncluded()) "genres" else "exclude_genres", genre.id)
                    }
                }
            }
        }
        return GET(url.toString(), headers)
    }

    private fun parseStatus(status: Int): Int {
        return when (status) {
            0 -> SManga.COMPLETED
            1 -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    private fun parseType(type: GenresDto): GenresDto {
        return when (type.name) {
            "Западный комикс" -> GenresDto(type.id, "Комикс")
            else -> type
        }
    }

    private fun MangaDetDto.toSManga(): SManga {
        val o = this
        return SManga.create().apply {
            title = en_name
            url = "/api/titles/$dir/"
            thumbnail_url = "$baseUrl/${img.high}"
            this.description = Jsoup.parse(o.description).text()
            genre = (genres + parseType(type)).joinToString { it.name }
            status = parseStatus(o.status.id)
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val series = gson.fromJson<SeriesWrapperDto<MangaDetDto>>(response.body()?.charStream()!!)
        branches[series.content.en_name] = series.content.branches
        return series.content.toSManga()
    }

    private fun mangaBranches(manga: SManga): List<BranchesDto> {
        val response = client.newCall(GET("$baseUrl/${manga.url}")).execute()
        val series = gson.fromJson<SeriesWrapperDto<MangaDetDto>>(response.body()?.charStream()!!)
        branches[series.content.en_name] = series.content.branches
        return series.content.branches
    }

    private fun selector(b: BranchesDto): Int = b.count_chapters
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val branch = branches.getOrElse(manga.title) { mangaBranches(manga) }
        return when {
            branch.isEmpty() -> {
                return Observable.just(listOf())
            }
            manga.status == SManga.LICENSED -> {
                Observable.error(Exception("Licensed - No chapters to show"))
            }
            else -> {
                val branchId = branch.maxBy { selector(it) }!!.id
                client.newCall(chapterListRequest(branchId))
                    .asObservableSuccess()
                    .map { response ->
                        chapterListParse(response)
                    }
            }
        }
    }

    private fun chapterListRequest(branch: Long): Request {
        return GET("$baseUrl/api/titles/chapters/?branch_id=$branch", headers)
    }

    private fun chapterName(book: BookDto): String {
        val chapterId = if (book.chapter % 1 == 0f) book.chapter.toInt() else book.chapter
        var chapterName = "${book.tome} - $chapterId"
        if (book.name.isNotBlank() && chapterName != chapterName) {
            chapterName += "- $chapterName"
        }
        return chapterName
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = gson.fromJson<PageWrapperDto<BookDto>>(response.body()?.charStream()!!)
        return chapters.content.filter { !it.is_paid }.map { chapter ->
            SChapter.create().apply {
                chapter_number = chapter.chapter
                name = chapterName(chapter)
                url = "/api/titles/chapters/${chapter.id}"
                date_upload = parseDate(chapter.upload_date)
            }
        }.sortedByDescending { it.chapter_number }
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun pageListParse(response: Response): List<Page> {
        val page = gson.fromJson<SeriesWrapperDto<PageDto>>(response.body()?.charStream()!!)
        return page.content.pages.map {
            Page(it.page, "", it.link)
        }
    }

    private class SearchFilter(name: String, val id: String) : Filter.TriState(name)
    private class CheckFilter(name: String, val id: String) : Filter.CheckBox(name)

    private class CategoryList(categories: List<SearchFilter>) : Filter.Group<SearchFilter>("Категории", categories)
    private class TypeList(types: List<SearchFilter>) : Filter.Group<SearchFilter>("Типы", types)
    private class StatusList(statuses: List<CheckFilter>) : Filter.Group<CheckFilter>("Статус", statuses)
    private class GenreList(genres: List<SearchFilter>) : Filter.Group<SearchFilter>("Жанры", genres)
    private class AgeList(ages: List<CheckFilter>) : Filter.Group<CheckFilter>("Возрастное ограничение", ages)

    override fun getFilterList() = FilterList(
        OrderBy(),
        GenreList(getGenreList()),
        CategoryList(getCategoryList()),
        TypeList(getTypeList()),
        StatusList(getStatusList()),
        AgeList(getAgeList())
    )

    private class OrderBy : Filter.Sort("Сортировка",
        arrayOf("Новизне", "Последним обновлениям", "Популярности", "Лайкам", "Просмотрам", "Мне повезет"),
        Selection(2, false))

    private fun getAgeList() = listOf(
        CheckFilter("Для всех", "0"),
        CheckFilter("16+", "1"),
        CheckFilter("18+", "2")
    )

    private fun getTypeList() = listOf(
        SearchFilter("Манга", "0"),
        SearchFilter("Манхва", "1"),
        SearchFilter("Маньхуа", "2"),
        SearchFilter("Западный комикс", "3"),
        SearchFilter("Русскомикс", "4"),
        SearchFilter("Индонезийский комикс", "5"),
        SearchFilter("Новелла", "6"),
        SearchFilter("Другое", "7")
    )

    private fun getStatusList() = listOf(
        CheckFilter("Закончен", "0"),
        CheckFilter("Продолжается", "1"),
        CheckFilter("Заморожен", "2")
    )

    private fun getCategoryList() = listOf(
        SearchFilter("алхимия", "47"),
        SearchFilter("ангелы", "48"),
        SearchFilter("антигерой", "26"),
        SearchFilter("антиутопия", "49"),
        SearchFilter("апокалипсис", "50"),
        SearchFilter("аристократия", "117"),
        SearchFilter("армия", "51"),
        SearchFilter("артефакты", "52"),
        SearchFilter("боги", "45"),
        SearchFilter("борьба за власть", "52"),
        SearchFilter("будущее", "55"),
        SearchFilter("в цвете", "6"),
        SearchFilter("вампиры", "112"),
        SearchFilter("веб", "5"),
        SearchFilter("вестерн", "56"),
        SearchFilter("видеоигры", "35"),
        SearchFilter("виртуальная реальность", "44"),
        SearchFilter("владыка демонов", "57"),
        SearchFilter("военные", "29"),
        SearchFilter("волшебные существа", "59"),
        SearchFilter("воспоминания из другого мира", "60"),
        SearchFilter("врачи / доктора", "116"),
        SearchFilter("выживание", "41"),
        SearchFilter("гг женщина", "63"),
        SearchFilter("гг мужчина", "64"),
        SearchFilter("гг силён с самого начала", "110"),
        SearchFilter("геймеры", "61"),
        SearchFilter("гильдии", "62"),
        SearchFilter("гяру", "28"),
        SearchFilter("девушки-монстры", "37"),
        SearchFilter("демоны", "15"),
        SearchFilter("драконы", "66"),
        SearchFilter("дружба", "67"),
        SearchFilter("ёнкома", "62"),
        SearchFilter("жестокий мир", "69"),
        SearchFilter("животные компаньоны", "70"),
        SearchFilter("завоевание мира", "71"),
        SearchFilter("зверолюди", "19"),
        SearchFilter("зомби", "14"),
        SearchFilter("игровые элементы", "73"),
        SearchFilter("исекай", "115"),
        SearchFilter("квесты", "75"),
        SearchFilter("космос", "76"),
        SearchFilter("кулинария", "16"),
        SearchFilter("культивация", "18"),
        SearchFilter("лоли", "108"),
        SearchFilter("магическая академия", "78"),
        SearchFilter("магия", "22"),
        SearchFilter("мафия", "24"),
        SearchFilter("медицина", "17"),
        SearchFilter("месть", "79"),
        SearchFilter("монстры", "38"),
        SearchFilter("музыка", "39"),
        SearchFilter("навыки / способности", "80"),
        SearchFilter("наёмники", "81"),
        SearchFilter("насилие / жестокость", "82"),
        SearchFilter("нежить", "83"),
        SearchFilter("ниндзя", "30"),
        SearchFilter("оборотни", "113"),
        SearchFilter("обратный гарем", "40"),
        SearchFilter("пародия", "85"),
        SearchFilter("подземелья", "86"),
        SearchFilter("политика", "87"),
        SearchFilter("полиция", "32"),
        SearchFilter("преступники / криминал", "36"),
        SearchFilter("призраки / духи", "27"),
        SearchFilter("прокачка", "118"),
        SearchFilter("путешествия во времени", "43"),
        SearchFilter("разумные расы", "88"),
        SearchFilter("ранги силы", "68"),
        SearchFilter("реинкарнация", "13"),
        SearchFilter("роботы", "89"),
        SearchFilter("рыцари", "90"),
        SearchFilter("самураи", "33"),
        SearchFilter("сборник", "10"),
        SearchFilter("сингл", "11"),
        SearchFilter("система", "91"),
        SearchFilter("скрытие личности", "93"),
        SearchFilter("спасение мира", "94"),
        SearchFilter("средневековье", "25"),
        SearchFilter("спасение мира", "94"),
        SearchFilter("средневековье", "25"),
        SearchFilter("стимпанк", "92"),
        SearchFilter("супергерои", "95"),
        SearchFilter("традиционные игры", "34"),
        SearchFilter("тупой гг", "109"),
        SearchFilter("умный гг", "111"),
        SearchFilter("управление", "114"),
        SearchFilter("философия", "97"),
        SearchFilter("хентай", "12"),
        SearchFilter("хикикомори", "21"),
        SearchFilter("шантаж", "99"),
        SearchFilter("эльфы", "46")
    )

    private fun getGenreList() = listOf(
        SearchFilter("арт", "1"),
        SearchFilter("бдсм", "44"),
        SearchFilter("боевик", "2"),
        SearchFilter("боевые искусства", "3"),
        SearchFilter("вампиры", "4"),
        SearchFilter("гарем", "5"),
        SearchFilter("гендерная интрига", "6"),
        SearchFilter("героическое фэнтези", "7"),
        SearchFilter("детектив", "8"),
        SearchFilter("дзёсэй", "9"),
        SearchFilter("додзинси", "10"),
        SearchFilter("драма", "11"),
        SearchFilter("игра", "12"),
        SearchFilter("история", "13"),
        SearchFilter("киберпанк", "14"),
        SearchFilter("кодомо", "15"),
        SearchFilter("комедия", "16"),
        SearchFilter("махо-сёдзё", "17"),
        SearchFilter("меха", "18"),
        SearchFilter("мистика", "19"),
        SearchFilter("научная фантастика", "20"),
        SearchFilter("повседневность", "21"),
        SearchFilter("постапокалиптика", "22"),
        SearchFilter("приключения", "23"),
        SearchFilter("психология", "24"),
        SearchFilter("романтика", "25"),
        SearchFilter("сверхъестественное", "27"),
        SearchFilter("сёдзё", "28"),
        SearchFilter("сёдзё-ай", "29"),
        SearchFilter("сёнэн", "30"),
        SearchFilter("сёнэн-ай", "31"),
        SearchFilter("спорт", "32"),
        SearchFilter("сэйнэн", "33"),
        SearchFilter("трагедия", "34"),
        SearchFilter("триллер", "35"),
        SearchFilter("ужасы", "36"),
        SearchFilter("фантастика", "37"),
        SearchFilter("фэнтези", "38"),
        SearchFilter("школа", "39"),
        SearchFilter("эротика", "42"),
        SearchFilter("этти", "40"),
        SearchFilter("юри", "41"),
        SearchFilter("яой", "43")
    )

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        screen.addPreference(screen.editTextPreference(USERNAME_TITLE, USERNAME_DEFAULT, username))
        screen.addPreference(screen.editTextPreference(PASSWORD_TITLE, PASSWORD_DEFAULT, password))
    }

    private fun androidx.preference.PreferenceScreen.editTextPreference(title: String, default: String, value: String): androidx.preference.EditTextPreference {
        return androidx.preference.EditTextPreference(context).apply {
            key = title
            this.title = title
            summary = value
            this.setDefaultValue(default)
            dialogTitle = title

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(title, newValue as String).commit()
                    Toast.makeText(context, "Restart Tachiyomi to apply new setting.", Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addPreference(screen.supportEditTextPreference(USERNAME_TITLE, USERNAME_DEFAULT, username))
        screen.addPreference(screen.supportEditTextPreference(PASSWORD_TITLE, PASSWORD_DEFAULT, password))
    }

    private fun PreferenceScreen.supportEditTextPreference(title: String, default: String, value: String): EditTextPreference {
        return EditTextPreference(context).apply {
            key = title
            this.title = title
            summary = value
            this.setDefaultValue(default)
            dialogTitle = title

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(title, newValue as String).commit()
                    Toast.makeText(context, "Restart Tachiyomi to apply new setting.", Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }
    }

    private fun getPrefUsername(): String = preferences.getString(USERNAME_TITLE, USERNAME_DEFAULT)!!
    private fun getPrefPassword(): String = preferences.getString(PASSWORD_TITLE, PASSWORD_DEFAULT)!!

    private val gson by lazy { Gson() }
    private val username by lazy { getPrefUsername() }
    private val password by lazy { getPrefPassword() }

    companion object {
        private val MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8")
        private const val USERNAME_TITLE = "Username"
        private const val USERNAME_DEFAULT = ""
        private const val PASSWORD_TITLE = "Password"
        private const val PASSWORD_DEFAULT = ""
    }
}
