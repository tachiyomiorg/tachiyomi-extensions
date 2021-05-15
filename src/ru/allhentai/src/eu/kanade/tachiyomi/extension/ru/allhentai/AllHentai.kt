package eu.kanade.tachiyomi.extension.ru.allhentai

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

@Nsfw
class AllHentai : ParsedHttpSource() {

    override val name = "AllHentai"

    override val baseUrl = "https://allhentai.ru"

    override val lang = "ru"

    override val supportsLatest = true

    private val rateLimitInterceptor = RateLimitInterceptor(2)

    override val client: OkHttpClient = network.client.newBuilder()
        .addNetworkInterceptor(rateLimitInterceptor).build()

    override fun popularMangaSelector() = "div.tile"

    override fun latestUpdatesSelector() = "div.tile"

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/list?sortType=rate&offset=${70 * (page - 1)}&max=70", headers)

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/list?sortType=updated&offset=${70 * (page - 1)}&max=70", headers)

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("img.lazy").first()?.attr("data-original")
        element.select("h3 > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title")
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a.nextLink"

    override fun latestUpdatesNextPageSelector() = "a.nextLink"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/advanced".toHttpUrlOrNull()!!.newBuilder()
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is GenreList -> filter.state.forEach { genre ->
                    if (genre.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(genre.id, arrayOf("=", "=in", "=ex")[genre.state])
                    }
                }
                is Category -> filter.state.forEach { category ->
                    if (category.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(category.id, arrayOf("=", "=in", "=ex")[category.state])
                    }
                }
                is FilList -> filter.state.forEach { fils ->
                    if (fils.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(fils.id, arrayOf("=", "=in", "=ex")[fils.state])
                    }
                }
                is OrderBy -> {
                    if (filter.state > 0) {
                        val ord = arrayOf("not", "year", "name", "rate", "popularity", "votes", "created", "updated")[filter.state]
                        val ordUrl = "$baseUrl/list?sortType=$ord".toHttpUrlOrNull()!!.newBuilder()
                        return GET(ordUrl.toString(), headers)
                    }
                }
                is Tags -> {
                    if (filter.state > 0) {
                        val tagName = getTagsList()[filter.state].name
                        val tagUrl = "$baseUrl/list/tag/$tagName".toHttpUrlOrNull()!!.newBuilder()
                        return GET(tagUrl.toString(), headers)
                    }
                }
                else -> return@forEach
            }
        }
        if (query.isNotEmpty()) {
            url.addQueryParameter("q", query)
        }
        return GET(url.toString().replace("=%3D", "="), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    // max 200 results
    override fun searchMangaNextPageSelector(): Nothing? = null

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.leftContent").first()
        val rawCategory = infoElement.select("span.elem_category").text()
        val category = if (rawCategory.isNotEmpty()) {
            rawCategory.toLowerCase()
        } else {
            "манга"
        }

        val manga = SManga.create()
        var authorElement = infoElement.select("span.elem_author").first()?.text()
        if (authorElement == null) {
            authorElement = infoElement.select("span.elem_screenwriter").first()?.text()
        }
        manga.title = infoElement.select("h1.names .name").text()
        manga.author = authorElement
        manga.artist = infoElement.select("span.elem_illustrator").first()?.text()
        manga.genre = infoElement.select("span.elem_genre").text().split(",").plusElement(category).joinToString { it.trim() }
        manga.description = infoElement.select("div.manga-description").text()
        manga.status = parseStatus(infoElement.html())
        manga.thumbnail_url = infoElement.select("img").attr("data-full")
        return manga
    }

    private fun parseStatus(element: String): Int = when {
        element.contains("Запрещена публикация произведения по копирайту") -> SManga.LICENSED
        element.contains("<h1 class=\"names\"> Сингл") || element.contains("<b>Перевод:</b> завершен") -> SManga.COMPLETED
        element.contains("<b>Перевод:</b> продолжается") -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return if (manga.status != SManga.LICENSED) {
            client.newCall(chapterListRequest(manga))
                .asObservableSuccess()
                .map { response ->
                    chapterListParse(response, manga)
                }
        } else {
            Observable.error(java.lang.Exception("Licensed - No chapters to show"))
        }
    }

    private fun chapterListParse(response: Response, manga: SManga): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).map { chapterFromElement(it, manga) }
    }

    override fun chapterListSelector() = "div.chapters-link > table > tbody > tr:has(td > a)"

    private fun chapterFromElement(element: Element, manga: SManga): SChapter {
        val urlElement = element.select("a").first()
        val urlText = urlElement.text()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href") + "?mtr=1")

        var translators = ""
        val translatorElement = urlElement.attr("title")
        if (!translatorElement.isNullOrBlank()) {
            translators = translatorElement
                .replace("(Переводчик),", "&")
                .removeSuffix(" (Переводчик)")
        }
        chapter.scanlator = translators

        chapter.name = urlText.removeSuffix(" новое").trim()
        if (manga.title.length > 25) {
            for (word in manga.title.split(' ')) {
                chapter.name = chapter.name.removePrefix(word).trim()
            }
        }
        val dots = chapter.name.indexOf("…")
        val numbers = chapter.name.findAnyOf(IntRange(0, 9).map { it.toString() })?.first ?: 0

        if (dots in 0 until numbers) {
            chapter.name = chapter.name.substringAfter("…").trim()
        }

        chapter.date_upload = element.select("td.d-none").last()?.text()?.let {
            try {
                SimpleDateFormat("dd.MM.yy", Locale.US).parse(it)?.time ?: 0L
            } catch (e: ParseException) {
                SimpleDateFormat("dd/MM/yy", Locale.US).parse(it)?.time ?: 0L
            }
        } ?: 0
        return chapter
    }

    override fun chapterFromElement(element: Element): SChapter {
        throw Exception("Not used")
    }

    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        val basic = Regex("""\s*([0-9]+)(\s-\s)([0-9]+)\s*""")
        val extra = Regex("""\s*([0-9]+\sЭкстра)\s*""")
        val single = Regex("""\s*Сингл\s*""")
        when {
            basic.containsMatchIn(chapter.name) -> {
                basic.find(chapter.name)?.let {
                    val number = it.groups[3]?.value!!
                    chapter.chapter_number = number.toFloat()
                }
            }
            extra.containsMatchIn(chapter.name) -> // Extra chapters doesn't contain chapter number
                chapter.chapter_number = -2f
            single.containsMatchIn(chapter.name) -> // Oneshoots, doujinshi and other mangas with one chapter
                chapter.chapter_number = 1f
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body!!.string()
        val beginIndex = html.indexOf("rm_h.init( [")
        val endIndex = html.indexOf(");", beginIndex)
        val trimmedHtml = html.substring(beginIndex, endIndex)

        val p = Pattern.compile("'.*?','.*?',\".*?\"")
        val m = p.matcher(trimmedHtml)

        val pages = mutableListOf<Page>()

        var i = 0
        while (m.find()) {
            val urlParts = m.group().replace("[\"\']+".toRegex(), "").split(',')
            val url = when {
                (urlParts[1].isEmpty() && urlParts[2].startsWith("/static/")) -> {
                    baseUrl + urlParts[2]
                }
                urlParts[1].endsWith("/manga/") -> {
                    urlParts[0] + urlParts[2]
                }
                urlParts[1].isEmpty() -> {
                    val imageUrl = urlParts[2].split('?')
                    "https:" + urlParts[0] + imageUrl[0]
                }
                else -> {
                    urlParts[1] + urlParts[0] + urlParts[2]
                }
            }

            pages.add(Page(i++, "", url))
        }
        return pages
    }

    override fun pageListParse(document: Document): List<Page> {
        throw Exception("Not used")
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val imgHeader = Headers.Builder().apply {
            add("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64)")
            add("Referer", baseUrl)
        }.build()
        return GET(page.imageUrl!!, imgHeader)
    }

    private class OrderBy : Filter.Select<String>(
        "Сортировка (only)",
        arrayOf("Без сортировки", "По году", "По алфавиту", "По популярности", "Популярно сейчас", "По рейтингу", "Новинки", "По дате обновления")
    )

    private class Genre(name: String, val id: String) : Filter.TriState(name)

    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Жанры", genres)
    private class Category(categories: List<Genre>) : Filter.Group<Genre>("Категории", categories)
    private class FilList(fils: List<Genre>) : Filter.Group<Genre>("Фильтры", fils)
    private class Tags(tags: Array<String>) : Filter.Select<String>("Тэг (only)", tags)

    private data class Tag(val name: String, val url: String)

    override fun getFilterList() = FilterList(
        OrderBy(),
        GenreList(getGenreList()),
        Category(getCategoryList()),
        FilList(getFilList()),
        Tags(tagsName)
    )

    /*
    * [...document.querySelectorAll('.search-form > .form-group')[1].querySelectorAll('span.js-link')]
    *   .map((el) =>
    *      `Genre("${el.textContent.trim()}", "${el
    *          .getAttribute('onclick')
    *          .match(/el_\d+/)}")`
    *  )
    *  .join(',\n');
    *  on allhen.live/search/advanced
    */
    private fun getGenreList() = listOf(
        Genre("ahegao", "el_855"),
        Genre("анал", "el_828"),
        Genre("бдсм", "el_78"),
        Genre("без цензуры", "el_888"),
        Genre("большая грудь", "el_837"),
        Genre("большая попка", "el_3156"),
        Genre("большой член", "el_884"),
        Genre("бондаж", "el_5754"),
        Genre("в первый раз", "el_811"),
        Genre("в цвете", "el_290"),
        Genre("гарем", "el_87"),
        Genre("гендарная интрига", "el_89"),
        Genre("групповой секс", "el_88"),
        Genre("драма", "el_95"),
        Genre("зрелые женщины", "el_5679"),
        Genre("измена", "el_291"),
        Genre("изнасилование", "el_124"),
        Genre("инцест", "el_85"),
        Genre("исторический", "el_93"),
        Genre("комедия", "el_73"),
        Genre("маленькая грудь", "el_870"),
        Genre("научная фантастика", "el_76"),
        Genre("нетораре", "el_303"),
        Genre("оральный секс", "el_853"),
        Genre("романтика", "el_74"),
        Genre("тентакли", "el_69"),
        Genre("трагедия", "el_1321"),
        Genre("ужасы", "el_75"),
        Genre("футанари", "el_77"),
        Genre("фэнтези", "el_70"),
        Genre("чикан", "el_1059"),
        Genre("этти", "el_798"),
        Genre("юри", "el_84"),
        Genre("яой", "el_83")
    )

    /*
    * [...document.querySelectorAll('.search-form > .form-group')[2].querySelectorAll('span.js-link')]
    *   .map((el) =>
    *      `Genre("${el.textContent.trim()}", "${el
    *          .getAttribute('onclick')
    *          .match(/el_\d+/)}")`
    *  )
    *  .join(',\n');
    *  on allhen.live/search/advanced
    */
    private fun getCategoryList() = listOf(
        Genre("3D", "el_626"),
        Genre("Анимация", "el_5777"),
        Genre("Без текста", "el_3157"),
        Genre("Порно комикс", "el_1003"),
        Genre("Порно манхва", "el_1104")
    )

    /*
    * [...document.querySelectorAll('.search-form > .form-group')[1].querySelectorAll('span.js-link')]
    *   .map((el) =>
    *      `Genre("${el.textContent.trim()}", "${el
    *          .getAttribute('onclick')
    *          .match(/s_\w+/)}")`
    *  )
    *  .join(',\n');
    *  on allhen.live/search/advanced
    */
    private fun getFilList() = listOf(
        Genre("Высокий рейтинг", "s_high_rate"),
        Genre("Сингл", "s_single"),
        Genre("Для взрослых", "s_mature"),
        Genre("Завершенная", "s_completed"),
        Genre("Переведено", "s_translated"),
        Genre("Длинная", "s_many_chapters"),
        Genre("Ожидает загрузки", "s_wait_upload"),
        Genre("Продается", "s_sale")
    )

    /**
     * [...document.querySelectorAll('tbody .element-link')]
     *   .map((it) =>
     *      `Tag("${it.textContent.trim()}", "${it
     *          .getAttribute('href')
     *          .split('tag/')
     *          .pop()}")`
     *  )
     *  .join(',\n');
     *  on allhen.live/list/tags/sort_NAME
     */
    private fun getTagsList() = listOf(
        Tag("Без тега", "not"),
        Tag("handjob", "handjob"),
        Tag("inseki", "inseki"),
        Tag("алкоголь", "alcohol"),
        Tag("андроид", "android"),
        Tag("анилингус", "anilingus"),
        Tag("бассейн", "pool"),
        Tag("без трусиков", "without_panties"),
        Tag("беременность", "pregnancy"),
        Tag("бикини", "bikini"),
        Tag("близнецы", "twins"),
        Tag("боди-арт", "body_art"),
        Tag("больница", "hospital"),
        Tag("буккакэ", "bukkake"),
        Tag("в ванной", "in_bathroom"),
        Tag("в общественном месте", "in_public_place"),
        Tag("в транспорте", "in_vehicle"),
        Tag("вампиры", "vampires"),
        Tag("вибратор", "vibrator"),
        Tag("втянутые соски", "inverted_nipples"),
        Tag("гипноз", "hypnosis"),
        Tag("глубокий минет", "deepthroat"),
        Tag("горничные", "maids"),
        Tag("горячий источник", "hot_spring"),
        Tag("гэнгбэнг", "gangbang"),
        Tag("гяру", "gyaru"),
        Tag("двойное проникновение", "double_penetration"),
        Tag("Девочки волшебницы", "magical_girl"),
        Tag("демоны", "demons"),
        Tag("дефекация", "scat"),
        Tag("дилдо", "dildo"),
        Tag("додзинси", "doujinshi"),
        Tag("домохозяйки", "housewives"),
        Tag("дыра в стене", "hole_in_the_wall"),
        Tag("жестокость", "cruelty"),
        Tag("загар", "tan_lines"),
        Tag("зомби", "zombie"),
        Tag("инопланетяне", "aliens"),
        Tag("исполнение желаний", "granting_wish"),
        Tag("камера", "camera"),
        Tag("косплей", "cosplay"),
        Tag("кремпай", "creampie"),
        Tag("куннилингус", "cunnilingus"),
        Tag("купальник", "swimsuit"),
        Tag("лактация", "lactation"),
        Tag("латекс и кожа", "latex"),
        Tag("Ломка Психики", "mind_break"),
        Tag("магия", "magic"),
        Tag("мастурбация", "masturbation"),
        Tag("медсестра", "nurse"),
        Tag("мерзкий дядька", "terrible_oyaji"),
        Tag("много девушек", "many_girls"),
        Tag("много спермы", "a_lot_of_sperm"),
        Tag("монстрдевушки", "monstergirl"),
        Tag("монстры", "monsters"),
        Tag("мужчина крепкого телосложения", "muscle_man"),
        Tag("на природе", "outside"),
        Tag("не бритая киска", "hairy_pussy"),
        Tag("не бритые подмышки", "hairy_armpits"),
        Tag("нетори", "netori"),
        Tag("нижнее бельё", "lingerie"),
        Tag("обмен партнерами", "swinging"),
        Tag("обмен телами", "body_swap"),
        Tag("обычный секс", "normal_sex"),
        Tag("огромная грудь", "super_big_boobs"),
        Tag("орки", "orcs"),
        Tag("очки", "megane"),
        Tag("пайзури", "titsfuck"),
        Tag("парень пассив", "passive_guy"),
        Tag("пацанка", "tomboy"),
        Tag("пеггинг", "pegging"),
        Tag("переодевание", "disguise"),
        Tag("пирсинг", "piercing"),
        Tag("писают", "peeing"),
        Tag("пляж", "beach"),
        Tag("повседневность", "slice_of_life"),
        Tag("повязка на глаза", "blindfold"),
        Tag("подглядывание", "peeping"),
        Tag("подчинение", "submission"),
        Tag("похищение", "kidnapping"),
        Tag("принуждение", "forced"),
        Tag("прозрачная одежда", "transparent_clothes"),
        Tag("проституция", "prostitution"),
        Tag("психические отклонения", "mental_illness"),
        Tag("публичный секс", "public_sex"),
        Tag("пьяные", "drunk"),
        Tag("рабы", "slaves"),
        Tag("рентген зрение", "x_ray"),
        Tag("сверхъестественное", "supernatural"),
        Tag("секс втроем", "threesome"),
        Tag("секс игрушки", "sex_toys"),
        Tag("сексуально возбужденная", "horny"),
        Tag("спортивная форма", "sports_uniform"),
        Tag("спящие", "sleeping"),
        Tag("страпон", "strapon"),
        Tag("Суккуб", "succubus"),
        Tag("темнокожие", "dark_skin"),
        Tag("толстушки", "fatties"),
        Tag("трап", "trap"),
        Tag("униформа", "uniform"),
        Tag("ушастые", "eared"),
        Tag("фантазии", "dreams"),
        Tag("фемдом", "femdom"),
        Tag("фестиваль", "festival"),
        Tag("фетиш", "fetish"),
        Tag("фистинг", "fisting"),
        Tag("фурри", "furry"),
        Tag("футанари имеет парня", "futanari_on_boy"),
        Tag("футджаб", "footfuck"),
        Tag("цельный купальник", "full_swimsuit"),
        Tag("цундэрэ", "tsundere"),
        Tag("чулки", "hose"),
        Tag("шалава", "slut"),
        Tag("шантаж", "blackmail"),
        Tag("эксгибиционизм", "exhibitionism"),
        Tag("эльфы", "elves"),
        Tag("яндере", "yandere")
    )

    private val tagsName = getTagsList().map {
        it.name
    }.toTypedArray()
}
