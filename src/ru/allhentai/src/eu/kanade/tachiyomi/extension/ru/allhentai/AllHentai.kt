package eu.kanade.tachiyomi.extension.ru.allhentai

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
import okhttp3.HttpUrl
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
        val url = HttpUrl.parse("$baseUrl/search/advanced")!!.newBuilder()
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is GenreList -> filter.state.forEach { genre ->
                    if (genre.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(genre.id, arrayOf("=", "=in", "=ex")[genre.state])
                    }
                }
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
        manga.author = infoElement.select("span.elem_author").first()?.text()
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

        chapter.date_upload = element.select("td.hidden-xxs").last()?.text()?.let {
            try {
                SimpleDateFormat("dd.MM.yy", Locale.US).parse(it).time
            } catch (e: ParseException) {
                SimpleDateFormat("dd/MM/yy", Locale.US).parse(it).time
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
        val html = response.body()!!.string()
        val beginIndex = html.indexOf("rm_h.init( [")
        val endIndex = html.indexOf(");", beginIndex)
        val trimmedHtml = html.substring(beginIndex, endIndex)

        val p = Pattern.compile("'.*?','.*?',\".*?\"")
        val m = p.matcher(trimmedHtml)

        val pages = mutableListOf<Page>()

        var i = 0
        while (m.find()) {
            val urlParts = m.group().replace("[\"\']+".toRegex(), "").split(',')
            val url = if (urlParts[1].isEmpty() && urlParts[2].startsWith("/static/")) {
                baseUrl + urlParts[2]
            } else {
                if (urlParts[1].endsWith("/manga/")) {
                    urlParts[0] + urlParts[2]
                } else if (urlParts[1].isEmpty()) {
                    val imageUrl = urlParts[2].split('?')
                    "https:" + urlParts[0] + imageUrl[0]
                } else {
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

    private class Genre(name: String, val id: String) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    /* [...document.querySelectorAll("tr.advanced_option:nth-child(1) > td:nth-child(3) span.js-link")]
    *  .map(el => `Genre("${el.textContent.trim()}", $"{el.getAttribute('onclick')
    *  .substr(31,el.getAttribute('onclick').length-33)"})`).join(',\n')
    *  on https://readmanga.me/search/advanced
    */
    override fun getFilterList() = FilterList(
        GenreList(getGenreList())
    )

    private fun getGenreList() = listOf(
        Genre("3D", "el_626"),
        Genre("ahegao", "el_855"),
        Genre("footfuck", "el_912"),
        Genre("gender bender", "el_89"),
        Genre("handjob", "el_1254"),
        Genre("megane", "el_962"),
        Genre("Mind break", "el_705"),
        Genre("netori", "el_1356"),
        Genre("paizuri (titsfuck)", "el_1027"),
        Genre("scat", "el_1221"),
        Genre("tomboy", "el_881"),
        Genre("x-ray", "el_1992"),
        Genre("алкоголь", "el_1000"),
        Genre("анал", "el_828"),
        Genre("андроид", "el_1752"),
        Genre("анилингус", "el_1037"),
        Genre("арт", "el_1190"),
        Genre("бдсм", "el_78"),
        Genre("Без текста", "el_3157"),
        Genre("без трусиков", "el_993"),
        Genre("без цензуры", "el_888"),
        Genre("беременность", "el_922"),
        Genre("бикини", "el_1126"),
        Genre("близнецы", "el_1092"),
        Genre("боди-арт", "el_1130"),
        Genre("Больница", "el_289"),
        Genre("большая грудь", "el_837"),
        Genre("Большая попка", "el_3156"),
        Genre("борьба", "el_72"),
        Genre("буккакэ", "el_82"),
        Genre("в бассейне", "el_3599"),
        Genre("в ванной", "el_878"),
        Genre("в государственном учреждении", "el_86"),
        Genre("в общественном месте", "el_866"),
        Genre("в первый раз", "el_811"),
        Genre("в транспорте", "el_3246"),
        Genre("в цвете", "el_290"),
        Genre("вампиры", "el_1250"),
        Genre("веб", "el_1104"),
        Genre("вибратор", "el_867"),
        Genre("втроем", "el_3711"),
        Genre("гарем", "el_87"),
        Genre("гипноз", "el_1423"),
        Genre("глубокий минет", "el_3555"),
        Genre("горячий источник", "el_1209"),
        Genre("групповой секс", "el_88"),
        Genre("гяру и гангуро", "el_844"),
        Genre("двойное проникновение", "el_911"),
        Genre("Девочки волшебницы", "el_292"),
        Genre("девчонки", "el_875"),
        Genre("демоны", "el_1139"),
        Genre("дилдо", "el_868"),
        Genre("додзинси", "el_92"),
        Genre("Домохозяйка", "el_300"),
        Genre("драма", "el_95"),
        Genre("дыра в стене", "el_1420"),
        Genre("жестокость", "el_883"),
        Genre("золотой дождь", "el_1007"),
        Genre("зомби", "el_1099"),
        Genre("зрелые женщины", "el_1441"),
        Genre("Измена", "el_291"),
        Genre("изнасилование", "el_124"),
        Genre("инопланетяне", "el_990"),
        Genre("инцест", "el_85"),
        Genre("исполнение желаний", "el_909"),
        Genre("исторический", "el_93"),
        Genre("камера", "el_869"),
        Genre("колготки", "el_849"),
        Genre("комикс", "el_1003"),
        Genre("косплей", "el_1024"),
        Genre("кремпай", "el_3709"),
        Genre("куннилингус", "el_5383"),
        Genre("купальники", "el_845"),
        Genre("латекс и кожа", "el_1047"),
        Genre("магия", "el_1128"),
        Genre("маленькая грудь", "el_870"),
        Genre("мастурбация", "el_882"),
        Genre("медсестра", "el_5688"),
        Genre("мейдочки", "el_994"),
        Genre("Мерзкий дядька", "el_2145"),
        Genre("милф", "el_5679"),
        Genre("много девушек", "el_860"),
        Genre("много спермы", "el_1020"),
        Genre("молоко", "el_1029"),
        Genre("монстрдевушки", "el_1022"),
        Genre("монстры", "el_917"),
        Genre("мочеиспускание", "el_1193"),
        Genre("мужчина крепкого телосложения", "el_5715"),
        Genre("на природе", "el_842"),
        Genre("наблюдение", "el_928"),
        Genre("научная фантастика", "el_76"),
        Genre("не бритая киска", "el_4237"),
        Genre("не бритые подмышки", "el_4238"),
        Genre("Нетораре", "el_303"),
        Genre("обмен телами", "el_5120"),
        Genre("обычный секс", "el_1012"),
        Genre("огромная грудь", "el_1207"),
        Genre("огромный член", "el_884"),
        Genre("омораси", "el_81"),
        Genre("оральный секс", "el_853"),
        Genre("орки", "el_3247"),
        Genre("парень пассив", "el_861"),
        Genre("парни", "el_874"),
        Genre("переодевание", "el_1026"),
        Genre("пляж", "el_846"),
        Genre("повседневность", "el_90"),
        Genre("подглядывание", "el_978"),
        Genre("подчинение", "el_885"),
        Genre("похищение", "el_1183"),
        Genre("превозмогание", "el_71"),
        Genre("принуждение", "el_929"),
        Genre("прозрачная одежда", "el_924"),
        Genre("проституция", "el_3563"),
        Genre("психические отклонения", "el_886"),
        Genre("публично", "el_1045"),
        Genre("пьяные", "el_2055"),
        Genre("рабыни", "el_1433"),
        Genre("романтика", "el_74"),
        Genre("сверхъестественное", "el_634"),
        Genre("секс игрушки", "el_871"),
        Genre("сексуально возбужденная", "el_925"),
        Genre("сибари", "el_80"),
        Genre("сильный", "el_913"),
        Genre("слабая", "el_455"),
        Genre("спортивная форма", "el_891"),
        Genre("спящие", "el_972"),
        Genre("страпон", "el_872"),
        Genre("Суккуб", "el_677"),
        Genre("темнокожие", "el_611"),
        Genre("тентакли", "el_69"),
        Genre("толстушки", "el_1036"),
        Genre("трагедия", "el_1321"),
        Genre("трап", "el_859"),
        Genre("ужасы", "el_75"),
        Genre("униформа", "el_1008"),
        Genre("ушастые", "el_991"),
        Genre("фантазии", "el_1124"),
        Genre("фемдом", "el_873"),
        Genre("Фестиваль", "el_1269"),
        Genre("фетиш", "el_1137"),
        Genre("фистинг", "el_821"),
        Genre("фурри", "el_91"),
        Genre("футанари", "el_77"),
        Genre("футанари имеет парня", "el_1426"),
        Genre("фэнтези", "el_70"),
        Genre("цельный купальник", "el_1257"),
        Genre("цундере", "el_850"),
        Genre("чикан", "el_1059"),
        Genre("чулки", "el_889"),
        Genre("шлюха", "el_763"),
        Genre("эксгибиционизм", "el_813"),
        Genre("Эльфы", "el_286"),
        Genre("эччи", "el_798"),
        Genre("юмор", "el_73"),
        Genre("юные", "el_1162"),
        Genre("юри", "el_84"),
        Genre("яндере", "el_823"),
        Genre("яой", "el_83")
    )
}
