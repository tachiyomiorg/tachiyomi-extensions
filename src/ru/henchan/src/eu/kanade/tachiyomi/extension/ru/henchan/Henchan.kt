package eu.kanade.tachiyomi.extension.ru.henchan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*


class Henchan : ParsedHttpSource() {

    override val name = "Henchan"

    override val baseUrl = "http://henchan.me"

    private val exhentaiBaseUrl = "http://exhentaidono.me"

    override val lang = "ru"

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request =
            GET("$baseUrl/mostfavorites&sort=manga?offset=${20 * (page - 1)}", headers)

    override fun latestUpdatesRequest(page: Int): Request =
            GET("$baseUrl/manga/new?offset=${20 * (page - 1)}", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var pageNum = 1
        when {
            page <  1 -> pageNum = 1
            page >= 1 -> pageNum = page
        }
        val url = if (query.isNotEmpty()) {
            "$baseUrl/?do=search&subaction=search&story=$query&search_start=$pageNum"
        } else {
            var genres = ""
            var order = ""
            for (filter in if (filters.isEmpty()) getFilterList() else filters) {
                when(filter){
                    is GenreList -> {
                        filter.state.forEach { f ->
                            if (!f.isIgnored()) {
                                genres += (if (f.isExcluded()) "-" else "") + f.id + '+'
                            }
                        }
                    }
                }
            }

            if (genres.isNotEmpty()) {
                for (filter in filters) {
                    when (filter) {
                        is OrderBy -> {
                            order = if (filter.state!!.ascending) {
                                arrayOf("&n=dateasc", "&n=favasc", "&n=abcdesc")[filter.state!!.index]
                            } else {
                                arrayOf("", "&n=favdesc", "&n=abcasc")[filter.state!!.index]
                            }
                        }
                    }
                }
                "$baseUrl/tags/${genres.dropLast(1)}&sort=manga$order?offset=${20 * (pageNum - 1)}"
            }else{
                for (filter in filters) {
                    when (filter) {
                        is OrderBy -> {
                            order = if (filter.state!!.ascending) {
                                arrayOf("manga/new&n=dateasc", "manga/new&n=favasc", "manga/new&n=abcdesc")[filter.state!!.index]
                            } else {
                                arrayOf("manga/new", "mostfavorites&sort=manga", "manga/new&n=abcasc")[filter.state!!.index]
                            }
                        }
                    }
                }
                "$baseUrl/$order?offset=${20 * (pageNum - 1)}"
            }
        }
        return GET(url, headers)
    }

    override fun popularMangaSelector() = ".content_row"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun searchMangaSelector() = ".content_row:not(:has(div.item:containsOwn(Тип)))"

    private fun String.getHQThumbnail(): String = this
            .replace("manganew_thumbs", "showfull_retina/manga")
            .replace("img.", "imgcover.")
            .replace("_henchan.me", "_hentaichan.ru")

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("img").first().attr("src").getHQThumbnail()

        val urlElem = element.select("h2 > a").first()
        manga.setUrlWithoutDomain(urlElem.attr("href"))
        manga.title = urlElem.text()

        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga =
            popularMangaFromElement(element)

    override fun searchMangaFromElement(element: Element): SManga =
            popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "#pagination > a:contains(Вперед)"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = "#nextlink, ${popularMangaNextPageSelector()}"


    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.author = document.select(".row .item2 h2")[1].text()
        manga.genre = document.select(".sidetag > a:eq(2)").joinToString { it.text() }
        manga.description = document.select("#description").text()
        return manga
    }

    override fun chapterListRequest(manga: SManga): Request {
        val baseMangaUrl = baseUrl + manga.url
        if(manga.thumbnail_url?.isBlank() ?: return GET(baseMangaUrl.replace("/manga/", "/related/"), headers)){
            return GET(baseMangaUrl, headers)
        }else {
            return GET(baseMangaUrl.replace("/manga/", "/related/"), headers)
        }
    }

    override fun chapterListSelector() = ".related"

    override fun chapterListParse(response: Response): List<SChapter> {
        val responseUrl = response.request().url().toString()
        val document = response.asJsoup()

        if(responseUrl.contains("/manga/")){
            val chap = SChapter.create()
            chap.setUrlWithoutDomain(responseUrl.removePrefix(baseUrl))
            chap.name = document.select("a.title_top_a").text()
            chap.chapter_number = 1F

            val date = document.select("div.row4_right b")?.text()?.let {
                SimpleDateFormat("dd MMMM yyyy", Locale("ru")).parse(it).time
            } ?: 0
            chap.date_upload = date
            return listOf(chap)
        }

        if (document.select("#right > div:nth-child(4)").text().contains(" похожий на ")) {
            val chap = SChapter.create()
            chap.setUrlWithoutDomain(document.select("#left > div > a").attr("href"))
            chap.name = document.select("#right > div:nth-child(4)").text().split(" похожий на ")[1]
            chap.chapter_number = 1F
            chap.date_upload = 0L
            return listOf(chap)
        }


        val result = mutableListOf<SChapter>()
        result.addAll(document.select(chapterListSelector()).map {
            chapterFromElement(it)
        })

        var url = document.select("div#pagination_related a:contains(Вперед)").attr("href")
        while (url.isNotBlank()) {
            val get = GET(
                    "${response.request().url()}/$url",
                    headers = headers
            )
            val nextPage = client.newCall(get).execute().asJsoup()
            result.addAll(nextPage.select(chapterListSelector()).map {
                chapterFromElement(it)
            })

            url = nextPage.select("div#pagination_related a:contains(Вперед)").attr("href")
        }

        return result.reversed()
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.select("h2 a").attr("href"))
        val chapterName = element.select("h2 a").attr("title")
        chapter.name = chapterName
        chapter.chapter_number = "(глава\\s|часть\\s)(\\d+)".toRegex(RegexOption.IGNORE_CASE).find(chapterName)?.groupValues?.get(2)?.toFloat() ?: 0F
        chapter.date_upload = 0L
        return chapter
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(exhentaiBaseUrl + chapter.url.replace("/manga/", "/online/") + "?development_access=true", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body()!!.string()
        val imgString = html.split("\"fullimg\": [").last().split("]").first()
        val resPages = mutableListOf<Page>()
        val imgs = imgString.split(",")
        imgs.forEachIndexed { index, s ->
            resPages.add(Page(index, imageUrl = s.trim('"', '\'', ' ')))
        }
        return resPages
    }

    override fun imageUrlParse(document: Document) = throw Exception("Not Used")

    override fun pageListParse(document: Document): List<Page> = throw Exception("Not Used")

    private class Genre(val id: String, name: String = id.replace('_', ' ').capitalize()) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Тэги", genres)
    private class OrderBy : Filter.Sort("Сортировка",
            arrayOf("Дата", "Популярность", "Алфавит"),
            Filter.Sort.Selection(1, false))

    override fun getFilterList() = FilterList(
            OrderBy(),
            GenreList(getGenreList())
    )

    private fun getGenreList() = listOf(
            Genre("3D"),
            Genre("action"),
            Genre("ahegao"),
            Genre("bdsm"),
            Genre("foot_fetish"),
            Genre("footfuck"),
            Genre("gender_bender"),
            Genre("live"),
            Genre("lolcon"),
            Genre("megane"),
            Genre("mind_break"),
            Genre("monstergirl"),
            Genre("netorare"),
            Genre("netori"),
            Genre("nipple_penetration"),
            Genre("paizuri_(titsfuck)"),
            Genre("rpg"),
            Genre("scat"),
            Genre("shemale"),
            Genre("shooter"),
            Genre("simulation"),
            Genre("tomboy"),
            Genre("алкоголь"),
            Genre("анал"),
            Genre("андроид"),
            Genre("анилингус"),
            Genre("аркада"),
            Genre("арт"),
            Genre("бабушка"),
            Genre("без_текста"),
            Genre("без_трусиков"),
            Genre("без_цензуры"),
            Genre("беременность"),
            Genre("бикини"),
            Genre("близнецы"),
            Genre("боди-арт"),
            Genre("больница"),
            Genre("большая_грудь"),
            Genre("большие_попки"),
            Genre("буккаке"),
            Genre("в_ванной"),
            Genre("в_общественном_месте"),
            Genre("в_первый_раз"),
            Genre("в_цвете"),
            Genre("в_школе"),
            Genre("веб"),
            Genre("вибратор"),
            Genre("визуальная_новелла"),
            Genre("внучка"),
            Genre("волосатые_женщины"),
            Genre("гаремник"),
            Genre("гипноз"),
            Genre("глубокий_минет"),
            Genre("горячий_источник"),
            Genre("групповой_секс"),
            Genre("гяру_и_гангуро"),
            Genre("двойное_проникновение"),
            Genre("девочки_волшебницы"),
            Genre("девушка_туалет"),
            Genre("демоны"),
            Genre("дилдо"),
            Genre("дочь"),
            Genre("драма"),
            Genre("дыра_в_стене"),
            Genre("жестокость"),
            Genre("за_деньги"),
            Genre("зомби"),
            Genre("зрелые_женщины"),
            Genre("измена"),
            Genre("изнасилование"),
            Genre("инопланетяне"),
            Genre("инцест"),
            Genre("исполнение_желаний"),
            Genre("камера"),
            Genre("квест"),
            Genre("колготки"),
            Genre("комиксы"),
            Genre("косплей"),
            Genre("кузина"),
            Genre("купальники"),
            Genre("латекс_и_кожа"),
            Genre("магия"),
            Genre("маленькая_грудь"),
            Genre("мастурбация"),
            Genre("мать"),
            Genre("мейдочки"),
            Genre("мерзкий_дядька"),
            Genre("много_девушек"),
            Genre("молоко"),
            Genre("монстры"),
            Genre("мочеиспускание"),
            Genre("мужская_озвучка"),
            Genre("на_природе"),
            Genre("наблюдение"),
            Genre("непрямой_инцест"),
            Genre("огромная_грудь"),
            Genre("огромный_член"),
            Genre("остановка_времени"),
            Genre("парень_пассив"),
            Genre("переодевание"),
            Genre("песочница"),
            Genre("племянница"),
            Genre("пляж"),
            Genre("подглядывание"),
            Genre("подчинение"),
            Genre("похищение"),
            Genre("принуждение"),
            Genre("прозрачная_одежда"),
            Genre("психические_отклонения"),
            Genre("публично"),
            Genre("рабыни"),
            Genre("романтика"),
            Genre("сверхъестественное"),
            Genre("секс_игрушки"),
            Genre("сестра"),
            Genre("сетакон"),
            Genre("спортивная_форма"),
            Genre("спящие"),
            Genre("страпон"),
            Genre("темнокожие"),
            Genre("тентакли"),
            Genre("толстушки"),
            Genre("трап"),
            Genre("тётя"),
            Genre("учитель_и_ученик"),
            Genre("ушастые"),
            Genre("фантазии"),
            Genre("фантастика"),
            Genre("фемдом"),
            Genre("фестиваль"),
            Genre("фистинг"),
            Genre("фурри"),
            Genre("футанари"),
            Genre("футанари_имеет_парня"),
            Genre("фэнтези"),
            Genre("хоррор"),
            Genre("цундере"),
            Genre("чикан"),
            Genre("чирлидеры"),
            Genre("чулки"),
            Genre("школьники"),
            Genre("школьницы"),
            Genre("школьный_купальник"),
            Genre("эксгибиционизм"),
            Genre("эльфы"),
            Genre("эччи"),
            Genre("юмор"),
            Genre("юри"),
            Genre("яндере"),
            Genre("яой")
    )
}
