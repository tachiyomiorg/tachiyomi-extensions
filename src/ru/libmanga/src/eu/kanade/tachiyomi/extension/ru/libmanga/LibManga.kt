package eu.kanade.tachiyomi.extension.ru.libmanga

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.nullArray
import com.github.salomonbrys.kotson.nullString
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonElement
import com.google.gson.JsonParser
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
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale
import android.support.v7.preference.ListPreference as LegacyListPreference
import android.support.v7.preference.PreferenceScreen as LegacyPreferenceScreen

class LibManga : ConfigurableSource, HttpSource() {

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_${id}_2", 0x0000)
    }

    override val name: String = "Mangalib"

    override val lang = "ru"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override val baseUrl: String = "https://mangalib.me"

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64)")
        add("Accept", "image/webp,*/*;q=0.8")
        add("Referer", baseUrl)
    }

    private val jsonParser = JsonParser()

    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)

    private val latestUpdatesSelector = "div.updates__item"

    override fun latestUpdatesParse(response: Response): MangasPage {
        val elements = response.asJsoup().select(latestUpdatesSelector)
        val latestMangas = elements?.map { latestUpdatesFromElement(it) }
        if (latestMangas != null)
            return MangasPage(latestMangas, false) // TODO: use API
        return MangasPage(emptyList(), false)
    }

    private fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("div.cover").first().let { img ->
            manga.thumbnail_url = baseUrl + img.attr("data-src").substringAfter(baseUrl)
                .replace("cover_thumb", "cover_250x350")
        }

        element.select("a").first().let { link ->
            manga.setUrlWithoutDomain(link.attr("href"))
            manga.title = element.select("h4").first().text()
        }
        return manga
    }

    private var csrfToken: String = ""

    private fun catalogHeaders() = Headers.Builder()
        .apply {
            add("Accept", "application/json, text/plain, */*")
            add("X-Requested-With", "XMLHttpRequest")
            add("x-csrf-token", csrfToken)
        }
        .build()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/login", headers)

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        if (csrfToken.isEmpty()) {
            return client.newCall(popularMangaRequest(page))
                .asObservableSuccess()
                .flatMap { response ->
                    // Obtain token
                    val resBody = response.body()!!.string()
                    csrfToken = "_token\" content=\"(.*)\"".toRegex().find(resBody)!!.groups[1]!!.value
                    return@flatMap fetchPopularMangaFromApi(page)
                }
        }
        return fetchPopularMangaFromApi(page)
    }

    private fun fetchPopularMangaFromApi(page: Int): Observable<MangasPage> {
        return client.newCall(POST("$baseUrl/filterlist?dir=desc&sort=views&page=$page", catalogHeaders()))
            .asObservableSuccess()
            .map { response ->
                popularMangaParse(response)
            }
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val resBody = response.body()!!.string()
        val result = jsonParser.parse(resBody).obj
        val items = result["items"]
        val popularMangas = items["data"].nullArray?.map { popularMangaFromElement(it) }

        if (popularMangas != null) {
            val hasNextPage = items["next_page_url"].nullString != null
            return MangasPage(popularMangas, hasNextPage)
        }
        return MangasPage(emptyList(), false)
    }

    private fun popularMangaFromElement(el: JsonElement) = SManga.create().apply {
        val slug = el["slug"].string
        val cover = el["cover"].string
        title = el["name"].string
        thumbnail_url = "$baseUrl/uploads/cover/$slug/cover/${cover}_250x350.jpg"
        url = "/$slug"
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        if (document.select("body[data-page=home]").isNotEmpty())
            throw Exception("Can't open manga. Try log in via WebView")

        val manga = SManga.create()

        if (document.html().contains("Манга удалена по просьбе правообладателей")) {
            manga.status = SManga.LICENSED
            return manga
        }

        val body = document.select("div.media-info-list").first()
        val rawCategory = body.select("div.media-info-list__title:contains(Тип) + div").text()

        val category = when {
            rawCategory == "Комикс западный" -> "комикс"
            rawCategory.isNotBlank() -> rawCategory.toLowerCase()
            else -> "манга"
        }

        val genres = document.select(".media-tags > a").map { it.text() }
        manga.thumbnail_url = document.select(".media-sidebar__cover > img").attr("src")
        manga.author = body.select("div.media-info-list__title:contains(Автор) + div").text()
        manga.artist = body.select("div.media-info-list__title:contains(Художник) + div").text()
        manga.status = when (
            body.select("div.media-info-list__title:contains(Статус перевода) + div")
                .text()
                .toLowerCase()
        ) {
            "продолжается" -> SManga.ONGOING
            "завершен" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        manga.genre = genres.plusElement(category).joinToString { it.trim() }
        manga.description = document.select(".media-description__text").text()
        return manga
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val dataStr = response
            .asJsoup()
            .toString()
            .substringAfter("window.__DATA__ = ")
            .substringBefore("window._SITE_COLOR_")
            .substringBeforeLast(";")

        val data = jsonParser.parse(dataStr).obj
        val chaptersList = data["chapters"]["list"].nullArray
        val slug = data["manga"]["slug"].string

        return chaptersList?.map { chapterFromElement(it, slug) } ?: emptyList()
    }

    private fun chapterFromElement(chapterItem: JsonElement, slug: String): SChapter {
        val chapter = SChapter.create()

        val volume = chapterItem["chapter_volume"].int
        val number = chapterItem["chapter_number"].string

        val url = "$baseUrl/$slug/v$volume/c$number"

        chapter.setUrlWithoutDomain(url)

        val nameChapter = chapterItem["chapter_name"].nullString
        val fullNameChapter = "Том $volume. Глава $number"

        chapter.name = if (nameChapter.isNullOrBlank()) fullNameChapter else "$fullNameChapter - $nameChapter"
        chapter.date_upload = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .parse(chapterItem["chapter_created_at"].string.substringBefore(" "))?.time ?: 0L
        return chapter
    }

    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        """Глава\s(\d+)""".toRegex().find(chapter.name)?.let {
            val number = it.groups[1]?.value!!
            chapter.chapter_number = number.toFloat()
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val chapInfo = document
            .select("script:containsData(window.__info)")
            .first()
            .html()
            .split("window.__info = ")
            .last()
            .trim()
            .split(";")
            .first()

        val chapInfoJson = jsonParser.parse(chapInfo).obj
        val servers = chapInfoJson["servers"].asJsonObject
        val defaultServer: String = chapInfoJson["img"]["server"].string
        val imgUrl: String = chapInfoJson["img"]["url"].string

        val serverToUse = if (this.server == null) defaultServer else this.server
        val imageServerUrl: String = servers[serverToUse].string

        // Get pages
        val pagesArr = document
            .select("script:containsData(window.__pg)")
            .first()
            .html()
            .trim()
            .removePrefix("window.__pg = ")
            .removeSuffix(";")

        val pagesJson = jsonParser.parse(pagesArr).array
        val pages = mutableListOf<Page>()

        pagesJson.forEach { page ->
            pages.add(Page(page["p"].int, "", imageServerUrl + "/" + imgUrl + page["u"].string))
        }
        return pages
    }

    override fun imageUrlParse(response: Response): String = ""

    private fun searchMangaByIdRequest(id: String): Request {
        return GET("$baseUrl/$id", headers)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SLUG_SEARCH)) {
            val realQuery = query.removePrefix(PREFIX_SLUG_SEARCH)
            client.newCall(searchMangaByIdRequest(realQuery))
                .asObservableSuccess()
                .map { response ->
                    val details = mangaDetailsParse(response)
                    details.url = "/$realQuery"
                    MangasPage(listOf(details), false)
                }
        } else {
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { response ->
                    searchMangaParse(response)
                }
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (csrfToken.isEmpty()) {
            val tokenResponse = client.newCall(popularMangaRequest(page)).execute()
            val resBody = tokenResponse.body()!!.string()
            csrfToken = "_token\" content=\"(.*)\"".toRegex().find(resBody)!!.groups[1]!!.value
        }
        val url = HttpUrl.parse("$baseUrl/filterlist?page=$page")!!.newBuilder()
        if (query.isNotEmpty()) {
            url.addQueryParameter("name", query)
        }
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is CategoryList -> filter.state.forEach { category ->
                    if (category.state) {
                        url.addQueryParameter("types[]", category.id)
                    }
                }
                is StatusList -> filter.state.forEach { status ->
                    if (status.state) {
                        url.addQueryParameter("status[]", status.id)
                    }
                }
                is GenreList -> filter.state.forEach { genre ->
                    if (genre.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(if (genre.isIncluded()) "genres[include][]" else "genres[exclude][]", genre.id)
                    }
                }
                is OrderBy -> {
                    url.addQueryParameter("dir", if (filter.state!!.ascending) "asc" else "desc")
                    url.addQueryParameter("sort", arrayOf("rate", "name", "views", "created_at", "last_chapter_at", "chap_count")[filter.state!!.index])
                }
                is AgeList -> filter.state.forEach { age ->
                    if (age.state) {
                        url.addQueryParameter("caution[]", age.id)
                    }
                }
                is TagList -> filter.state.forEach { tag ->
                    if (tag.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(if (tag.isIncluded()) "tags[include][]" else "tags[exclude][]", tag.id)
                    }
                }
            }
        }
        return POST(url.toString(), catalogHeaders())
    }

    // Hack search method to add some results from search popup
    override fun searchMangaParse(response: Response): MangasPage {
        val searchRequest = response.request().url().queryParameter("name")
        val mangas = mutableListOf<SManga>()

        if (!searchRequest.isNullOrEmpty()) {
            val popupSearchHeaders = headers
                .newBuilder()
                .add("Accept", "application/json, text/plain, */*")
                .add("X-Requested-With", "XMLHttpRequest")
                .build()

            // +200ms
            val popup = client.newCall(
                GET("$baseUrl/search?query=$searchRequest", popupSearchHeaders)
            )
                .execute().body()!!.string()

            val jsonList = jsonParser.parse(popup).array
            jsonList.forEach {
                mangas.add(popularMangaFromElement(it))
            }
        }
        val searchedMangas = popularMangaParse(response)

        // Filtered out what find in popup search
        mangas.addAll(
            searchedMangas.mangas.filter { search ->
                mangas.find { search.title == it.title } == null
            }
        )

        return MangasPage(mangas, searchedMangas.hasNextPage)
    }

    private class SearchFilter(name: String, val id: String) : Filter.TriState(name)
    private class CheckFilter(name: String, val id: String) : Filter.CheckBox(name)

    private class CategoryList(categories: List<CheckFilter>) : Filter.Group<CheckFilter>("Тип", categories)
    private class StatusList(statuses: List<CheckFilter>) : Filter.Group<CheckFilter>("Статус перевода", statuses)
    private class GenreList(genres: List<SearchFilter>) : Filter.Group<SearchFilter>("Жанры", genres)
    private class TagList(tags: List<SearchFilter>) : Filter.Group<SearchFilter>("Теги", tags)
    private class AgeList(ages: List<CheckFilter>) : Filter.Group<CheckFilter>("Возрастное ограничение", ages)

    override fun getFilterList() = FilterList(
        OrderBy(),
        CategoryList(getCategoryList()),
        GenreList(getGenreList()),
        TagList(getTagList()),
        StatusList(getStatusList()),
        AgeList(getAgeList())
    )

    private class OrderBy : Filter.Sort(
        "Сортировка",
        arrayOf("Рейтинг", "Имя", "Просмотры", "Дате добавления", "Дате обновления", "Кол-во глав"),
        Selection(0, false)
    )

    /*
    * Use console
    * Object.entries(__FILTER_ITEMS__.types).map(([k, v]) => `SearchFilter("${v.label}", "${v.id}")`).join(',\n')
    * on /manga-list
    */
    private fun getCategoryList() = listOf(
        CheckFilter("Манга", "1"),
        CheckFilter("OEL-манга", "4"),
        CheckFilter("Манхва", "5"),
        CheckFilter("Маньхуа", "6"),
        CheckFilter("Руманга", "8"),
        CheckFilter("Комикс западный", "9")
    )

    /*
    * Use console
    * Object.entries(__FILTER_ITEMS__.status).map(([k, v]) => `SearchFilter("${v.label}", "${v.id}")`).join(',\n')
    * on /manga-list
    */
    private fun getStatusList() = listOf(
        CheckFilter("Продолжается", "1"),
        CheckFilter("Завершен", "2"),
        CheckFilter("Заморожен", "3"),
        CheckFilter("Заброшен", "4")
    )

    /*
    * Use console
    * __FILTER_ITEMS__.genres.map(it => `SearchFilter("${it.name}", "${it.id}")`).join(',\n')
    * on /manga-list
    */
    private fun getGenreList() = listOf(
        SearchFilter("арт", "32"),
        SearchFilter("боевик", "34"),
        SearchFilter("боевые искусства", "35"),
        SearchFilter("вампиры", "36"),
        SearchFilter("веб", "78"),
        SearchFilter("гарем", "37"),
        SearchFilter("гендерная интрига", "38"),
        SearchFilter("героическое фэнтези", "39"),
        SearchFilter("детектив", "40"),
        SearchFilter("дзёсэй", "41"),
        SearchFilter("додзинси", "42"),
        SearchFilter("драма", "43"),
        SearchFilter("ёнкома", "75"),
        SearchFilter("игра", "44"),
        SearchFilter("исекай", "79"),
        SearchFilter("история", "45"),
        SearchFilter("киберпанк", "46"),
        SearchFilter("кодомо", "76"),
        SearchFilter("комедия", "47"),
        SearchFilter("махо-сёдзё", "48"),
        SearchFilter("меха", "49"),
        SearchFilter("мистика", "50"),
        SearchFilter("научная фантастика", "51"),
        SearchFilter("омегаверс", "77"),
        SearchFilter("повседневность", "52"),
        SearchFilter("постапокалиптика", "53"),
        SearchFilter("приключения", "54"),
        SearchFilter("психология", "55"),
        SearchFilter("романтика", "56"),
        SearchFilter("самурайский боевик", "57"),
        SearchFilter("сверхъестественное", "58"),
        SearchFilter("сёдзё", "59"),
        SearchFilter("сёдзё-ай", "60"),
        SearchFilter("сёнэн", "61"),
        SearchFilter("сёнэн-ай", "62"),
        SearchFilter("спорт", "63"),
        SearchFilter("сэйнэн", "64"),
        SearchFilter("трагедия", "65"),
        SearchFilter("триллер", "66"),
        SearchFilter("ужасы", "67"),
        SearchFilter("фантастика", "68"),
        SearchFilter("фэнтези", "69"),
        SearchFilter("школа", "70"),
        SearchFilter("эротика", "71"),
        SearchFilter("этти", "72"),
        SearchFilter("юри", "73"),
        SearchFilter("яой", "74")
    )
    private fun getTagList() = listOf(
        SearchFilter("Азартные игры", "304"),
        SearchFilter("Алхимия", "225"),
        SearchFilter("Ангелы", "226"),
        SearchFilter("Антигерой", "175"),
        SearchFilter("Антиутопия", "227"),
        SearchFilter("Апокалипсис", "228"),
        SearchFilter("Армия", "229"),
        SearchFilter("Артефакты", "230"),
        SearchFilter("Боги", "215"),
        SearchFilter("Бои на мечах", "231"),
        SearchFilter("Борьба за власть", "231"),
        SearchFilter("Брат и сестра", "233"),
        SearchFilter("Будущее", "234"),
        SearchFilter("Ведьма", "338"),
        SearchFilter("Вестерн", "235"),
        SearchFilter("Видеоигры", "185"),
        SearchFilter("Виртуальная реальность", "195"),
        SearchFilter("Владыка демонов", "236"),
        SearchFilter("Военные", "179"),
        SearchFilter("Война", "237"),
        SearchFilter("Волшебники / маги", "281"),
        SearchFilter("Волшебные существа", "239"),
        SearchFilter("Воспоминания из другого мира", "240"),
        SearchFilter("Выживание", "193"),
        SearchFilter("ГГ женщина", "243"),
        SearchFilter("ГГ имба", "291"),
        SearchFilter("ГГ мужчина", "244"),
        SearchFilter("Геймеры", "241"),
        SearchFilter("Гильдии", "242"),
        SearchFilter("Глупый ГГ", "297"),
        SearchFilter("Гоблины", "245"),
        SearchFilter("Горничные", "169"),
        SearchFilter("Гяру", "178"),
        SearchFilter("Демоны", "151"),
        SearchFilter("Драконы", "246"),
        SearchFilter("Дружба", "247"),
        SearchFilter("Жестокий мир", "249"),
        SearchFilter("Животные компаньоны", "250"),
        SearchFilter("Завоевание мира", "251"),
        SearchFilter("Зверолюди", "162"),
        SearchFilter("Злые духи", "252"),
        SearchFilter("Зомби", "149"),
        SearchFilter("Игровые элементы", "253"),
        SearchFilter("Империи", "254"),
        SearchFilter("Квесты", "255"),
        SearchFilter("Космос", "256"),
        SearchFilter("Кулинария", "152"),
        SearchFilter("Культивация", "160"),
        SearchFilter("Легендарное оружие", "257"),
        SearchFilter("Лоли", "187"),
        SearchFilter("Магическая академия", "258"),
        SearchFilter("Магия", "168"),
        SearchFilter("Мафия", "172"),
        SearchFilter("Медицина", "153"),
        SearchFilter("Месть", "259"),
        SearchFilter("Монстр Девушки", "188"),
        SearchFilter("Монстры", "189"),
        SearchFilter("Музыка", "190"),
        SearchFilter("Навыки / способности", "260"),
        SearchFilter("Насилие / жестокость", "262"),
        SearchFilter("Наёмники", "261"),
        SearchFilter("Нежить", "263"),
        SearchFilter("Ниндая", "180"),
        SearchFilter("Обратный Гарем", "191"),
        SearchFilter("Огнестрельное оружие", "264"),
        SearchFilter("Офисные Работники", "181"),
        SearchFilter("Пародия", "265"),
        SearchFilter("Пираты", "340"),
        SearchFilter("Подземелья", "266"),
        SearchFilter("Политика", "267"),
        SearchFilter("Полиция", "182"),
        SearchFilter("Преступники / Криминал", "186"),
        SearchFilter("Призраки / Духи", "177"),
        SearchFilter("Путешествие во времени", "194"),
        SearchFilter("Разумные расы", "268"),
        SearchFilter("Ранги силы", "248"),
        SearchFilter("Реинкарнация", "148"),
        SearchFilter("Роботы", "269"),
        SearchFilter("Рыцари", "270"),
        SearchFilter("Самураи", "183"),
        SearchFilter("Система", "271"),
        SearchFilter("Скрытие личности", "273"),
        SearchFilter("Спасение мира", "274"),
        SearchFilter("Спортивное тело", "334"),
        SearchFilter("Средневековье", "173"),
        SearchFilter("Стимпанк", "272"),
        SearchFilter("Супергерои", "275"),
        SearchFilter("Традиционные игры", "184"),
        SearchFilter("Умный ГГ", "302"),
        SearchFilter("Учитель / ученик", "276"),
        SearchFilter("Философия", "277"),
        SearchFilter("Хикикомори", "166"),
        SearchFilter("Холодное оружие", "278"),
        SearchFilter("Шантаж", "279"),
        SearchFilter("Эльфы", "216"),
        SearchFilter("Якудза", "164"),
        SearchFilter("Япония", "280")

    )

    private fun getAgeList() = listOf(
        CheckFilter("Отсутствует", "0"),
        CheckFilter("16+", "1"),
        CheckFilter("18+", "2")
    )
    companion object {
        const val PREFIX_SLUG_SEARCH = "slug:"
        private const val SERVER_PREF = "MangaLibImageServer"
        private const val SERVER_PREF_Title = "Сервер изображений"
    }

    private var server: String? = preferences.getString(SERVER_PREF, null)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val serverPref = ListPreference(screen.context).apply {
            key = SERVER_PREF
            title = SERVER_PREF_Title
            entries = arrayOf("Основной", "Второй (тестовый)", "Третий (эконом трафика)")
            entryValues = arrayOf("secondary", "fourth", "compress")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                server = newValue.toString()
                true
            }
        }

        screen.addPreference(serverPref)
    }

    override fun setupPreferenceScreen(screen: LegacyPreferenceScreen) {
        val serverPref = LegacyListPreference(screen.context).apply {
            key = SERVER_PREF
            title = SERVER_PREF_Title
            entries = arrayOf("Основной", "Второй (тестовый)", "Третий (эконом трафика)")
            entryValues = arrayOf("secondary", "fourth", "compress")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                server = newValue.toString()
                true
            }
        }

        screen.addPreference(serverPref)
    }
}
