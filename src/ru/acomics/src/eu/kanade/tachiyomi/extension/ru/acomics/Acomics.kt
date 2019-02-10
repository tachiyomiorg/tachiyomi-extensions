package eu.kanade.tachiyomi.extension.ru.acomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class AComics : ParsedHttpSource() {

    override val name = "AComics"

    override val baseUrl = "https://acomics.ru"

    override val lang = "ru"

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request =
            GET("$baseUrl/comics?categories=&ratings[]=1&ratings[]=2&ratings[]=3&ratings[]=4&ratings[]=5ratings[]=6&&type=0&updatable=0&subscribe=0&issue_count=2&sort=subscr_count&skip=${10 * (page - 1)}", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url: String = if (query.isNotEmpty()) {
            "$baseUrl/search?keyword=$query"
        } else {
            val categories = mutableListOf<Int>()
            var status = "0"
            val rating = mutableListOf<Int>()
            for (filter in if (filters.isEmpty()) getFilterList() else filters) {
                when (filter) {
                    is GenreList -> {
                        filter.state.forEach {
                            if (it.state) {
                                categories.add(it.id)
                            }
                        }
                    }
                    is Status -> {
                        if (filter.state == 1) {
                            status = "no"
                        }
                        if (filter.state == 2) {
                            status = "yes"
                        }
                    }
                    is Rating -> {
                        filter.state.forEach {
                            if (it.state) {
                                rating.add(it.id)
                            }
                        }
                    }
                }
            }
            "$baseUrl/comics?categories=${categories.joinToString(",")}&${rating.joinToString { "ratings[]=$it" }}&type=0&updatable=$status&subscribe=0&issue_count=2&sort=subscr_count&skip=${10 * (page - 1)}"
        }
        return GET(url, headers)
    }

    override fun latestUpdatesRequest(page: Int): Request =
            GET("$baseUrl/comics?categories=&ratings[]=2&ratings[]=3&ratings[]=4&ratings[]=5&type=0&updatable=0&subscribe=0&issue_count=2&sort=last_update?skip=${10 * (page - 1)}", headers)


    override fun popularMangaSelector() = "table.list-loadable > tbody > tr"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = baseUrl + element.select("a > img").first().attr("src")
        element.select("div.title > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href") + "/about")
            manga.title = it.text()
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga =
            popularMangaFromElement(element)

    override fun searchMangaFromElement(element: Element): SManga =
            popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "span.button:not(:has(a)) + span.button > a"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()


    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select(".about-summary").first()
        val manga = SManga.create()
        manga.author = infoElement.select(".about-summary > p:contains(Автор)").text().split(":")[1]
        manga.genre = infoElement.select("a.button").joinToString { it.text() }
        manga.description = infoElement.ownText()
        return manga
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        var res = mutableListOf<SChapter>()
        val count = response.asJsoup()
                .select(".about-summary > p:contains(Количество выпусков:)")
                .text()
                .split("Количество выпусков: ")[1].toInt()

        for (index in count downTo 1) {
            val chapter = SChapter.create()
            chapter.chapter_number = index.toFloat()
            chapter.name = index.toString()
            val url = response.request().url().toString().split("/about")[0].split(baseUrl)[1]
            chapter.url = "$url/$index"
            res.add(chapter)
        }
        return res
    }

    override fun chapterListSelector(): Nothing = throw Exception("Not used")

    override fun chapterFromElement(element: Element): SChapter = throw Exception("Not used")

    override fun pageListParse(document: Document): List<Page> {
        val imageElement = document.select("img#mainImage").first()
        return listOf(Page(0, imageUrl = baseUrl + imageElement.attr("src")))

    }

    override fun imageUrlParse(document: Document) = ""

    private class GenreList(genres: List<Storage>) : Filter.Group<Storage>("Категории", genres)
    private class Storage(name: String, val id: Int) : Filter.CheckBox(name)
    private class Status : Filter.Select<String>("Статус", arrayOf("Все", "Завершенный", "Продолжающийся"))

    private class Rating : Filter.Group<Storage>("Возрастная категория", listOf(
            Storage("???", 1),
            Storage("0+", 2),
            Storage("6+", 3),
            Storage("12+", 4),
            Storage("16+", 5),
            Storage("18+", 6)
    ))


    override fun getFilterList() = FilterList(
            Status(),
            Rating(),
            GenreList(getGenreList())
    )

    private fun getGenreList() = listOf(
            Storage("Животные", 1),
            Storage("Драма", 2),
            Storage("Фэнтези", 3),
            Storage("Игры", 4),
            Storage("Юмор", 5),
            Storage("Журнал", 6),
            Storage("Паранормальное", 7),
            Storage("Конец света", 8),
            Storage("Романтика", 9),
            Storage("Фантастика", 10),
            Storage("Бытовое", 11),
            Storage("Стимпанк", 12),
            Storage("Супергерои", 13)
    )

}
