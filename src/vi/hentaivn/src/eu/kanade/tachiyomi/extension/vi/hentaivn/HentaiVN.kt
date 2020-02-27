package eu.kanade.tachiyomi.extension.vi.hentaivn

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale


class HentaiVN : ParsedHttpSource() {

    override val baseUrl = "https://hentaivn.net"
    override val lang = "vi"
    override val name = "HentaiVN"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        element.select("a").first().let {
            chapter.name = it.select("h2").text()
            chapter.setUrlWithoutDomain(it.attr("href"))
        }
        chapter.date_upload = parseDate(element.select("td:nth-child(2)").text().trim())
        return chapter
    }

    private fun parseDate(dateString: String): Long {
        return try {
            dateFormat.parse(dateString).time
        } catch (e: ParseException) {
            return 0L
        }
    }

    override fun chapterListSelector() = ".page-info > table.listing > tbody > tr"

    override fun imageUrlParse(document: Document) = ""

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select(".box-description a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text().trim()
        }
        manga.thumbnail_url = element.select("img.img-list").attr("abs:src")
        return manga
    }

    override fun latestUpdatesNextPageSelector() = "ul.pagination > li:contains(Next)"

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/chap-moi.html?page=$page", headers)
    }

    override fun latestUpdatesSelector() = ".main > .block-left > .block-item > ul > li.item"

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select(".main > .page-left > .left-info > .page-info")
        val manga = SManga.create()
        manga.author = infoElement.select("p:contains(Tác giả:) a").text()
        manga.description = infoElement.select(":root > p:contains(Nội dung:) + p").text()
        manga.genre = infoElement.select("p:contains(Thể loại:) a").joinToString { it.text() }
        manga.thumbnail_url = document.select(".main > .page-right > .right-info > .page-ava > img").attr("abs:src")
        manga.status = parseStatus(infoElement.select("p:contains(Tình Trạng:) a").firstOrNull()?.text())
        return manga
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Đang tiến hành") -> SManga.ONGOING
        status.contains("Đã hoàn thành") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        val pageUrl = document.select("link[rel=canonical]").attr("href")
        document.select("#image > img").forEachIndexed { i, e ->
            pages.add(Page(i, pageUrl, e.attr("abs:src")))
        }
        return pages
    }

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().add("Referer", page.url).build()
        return GET(page.imageUrl!!, imgHeaders)
    }

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/tieu-diem.html?page=$page", headers)
    }

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select(".search-des > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text().trim()
        }
        manga.thumbnail_url = element.select("div.search-img img").attr("abs:src")
        return manga
    }

    override fun searchMangaNextPageSelector() = "ul.pagination > li:contains(Cuối)"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/forum/search-plus.php?name=$query&page=$page&dou=&char=&group=0&search=")!!.newBuilder()
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is TextField -> url.addQueryParameter(filter.key, filter.state)
                is GenreList -> filter.state
                        .filter { it.state }
                        .map { it.id }
                        .forEach { url.addQueryParameter("tag[]", it) }
                is GroupList -> {
                    val group = getGroupList()[filter.state]
                    url.addQueryParameter("group", group.id)
                }
            }
        }

        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = "#container .search-li"

    private class TextField(name: String, val key: String) : Filter.Text(name)
    private class Genre(name: String, val id: String) : Filter.CheckBox(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)
    private class TransGroup(name: String, val id: String) : Filter.CheckBox(name) {
        override fun toString(): String {
            return name
        }
    }
    private class GroupList(groups: Array<TransGroup>) : Filter.Select<TransGroup>("Nhóm dịch", groups)

    override fun getFilterList() = FilterList(
            TextField("Doujinshi", "dou"),
            TextField("Nhân vật", "char"),
            GenreList(getGenreList()),
            GroupList(getGroupList())
    )

    // jQuery.makeArray($('#container > div > div > div.box-box.textbox > form > ul:nth-child(7) > li').map((i, e) => `Genre("${e.textContent}", "${e.children[0].value}")`)).join(',\n')
    // https://hentaivn.net/forum/search-plus.php
    private fun getGenreList() = listOf(
            Genre("3D Hentai", "3"),
            Genre("Action", "5"),
            Genre("Adult", "116"),
            Genre("Adventure", "203"),
            Genre("Ahegao", "20"),
            Genre("Anal", "21"),
            Genre("Angel", "249"),
            Genre("Ảnh động", "131"),
            Genre("Animal", "127"),
            Genre("Animal girl", "22"),
            Genre("Artist", "115"),
            Genre("BBW", "251"),
            Genre("BDSM", "24"),
            Genre("Bestiality", "25"),
            Genre("Big Ass", "133"),
            Genre("Big Boobs", "23"),
            Genre("Big Penis", "32"),
            Genre("Bloomers", "27"),
            Genre("BlowJobs", "28"),
            Genre("Body Swap", "29"),
            Genre("Bodysuit", "30"),
            Genre("Bondage", "254"),
            Genre("Breast Sucking", "33"),
            Genre("BreastJobs", "248"),
            Genre("Brocon", "31"),
            Genre("Brother", "242"),
            Genre("Business Suit", "241"),
            Genre("Catgirls", "39"),
            Genre("Che ít", "101"),
            Genre("Che nhiều", "129"),
            Genre("Cheating", "34"),
            Genre("Chikan", "35"),
            Genre("Có che", "100"),
            Genre("Comedy", "36"),
            Genre("Comic", "120"),
            Genre("Condom", "210"),
            Genre("Cosplay", "38"),
            Genre("Cousin", "2"),
            Genre("Dark Skin", "40"),
            Genre("Demon", "132"),
            Genre("DemonGirl", "212"),
            Genre("Devil", "104"),
            Genre("DevilGirl", "105"),
            Genre("Dirty", "253"),
            Genre("Dirty Old Man", "41"),
            Genre("Double Penetration", "42"),
            Genre("Doujinshi", "44"),
            Genre("Drama", "4"),
            Genre("Drug", "43"),
            Genre("Ecchi", "45"),
            Genre("Elder Sister", "245"),
            Genre("Elf", "125"),
            Genre("Exhibitionism", "46"),
            Genre("Fantasy", "123"),
            Genre("Father", "243"),
            Genre("Femdom", "47"),
            Genre("Fingering", "48"),
            Genre("Footjob", "108"),
            Genre("Full Color", "37"),
            Genre("Furry", "202"),
            Genre("Futanari", "50"),
            Genre("Game", "130"),
            Genre("GangBang", "51"),
            Genre("Garter Belts", "206"),
            Genre("Gender Bender", "52"),
            Genre("Ghost", "106"),
            Genre("Glasses", "56"),
            Genre("Group", "53"),
            Genre("Guro", "55"),
            Genre("Hairy", "247"),
            Genre("Handjob", "57"),
            Genre("Harem", "58"),
            Genre("HentaiVN", "102"),
            Genre("Historical", "80"),
            Genre("Horror", "122"),
            Genre("Housewife", "59"),
            Genre("Humiliation", "60"),
            Genre("Idol", "61"),
            Genre("Imouto", "244"),
            Genre("Incest", "62"),
            Genre("Insect (Côn Trùng)", "26"),
            Genre("Không che", "99"),
            Genre("Kimono", "110"),
            Genre("Loli", "63"),
            Genre("Maids", "64"),
            Genre("Manhwa", "114"),
            Genre("Mature", "119"),
            Genre("Miko", "124"),
            Genre("Milf", "126"),
            Genre("Mind Break", "121"),
            Genre("Mind Control", "113"),
            Genre("Monster", "66"),
            Genre("Monstergirl", "67"),
            Genre("Mother", "103"),
            Genre("Nakadashi", "205"),
            Genre("Netori", "1"),
            Genre("Non-hen", "201"),
            Genre("NTR", "68"),
            Genre("Nurse", "69"),
            Genre("Old Man", "211"),
            Genre("Oneshot", "71"),
            Genre("Oral", "70"),
            Genre("Osananajimi", "209"),
            Genre("Paizuri", "72"),
            Genre("Pantyhose", "204"),
            Genre("Pregnant", "73"),
            Genre("Rape", "98"),
            Genre("Romance", "117"),
            Genre("Ryona", "207"),
            Genre("Scat", "134"),
            Genre("School Uniform", "74"),
            Genre("SchoolGirl", "75"),
            Genre("Series", "87"),
            Genre("Sex Toys", "88"),
            Genre("Shimapan", "246"),
            Genre("Short Hentai", "118"),
            Genre("Shota", "77"),
            Genre("Shoujo", "76"),
            Genre("Siscon", "79"),
            Genre("Sister", "78"),
            Genre("Slave", "82"),
            Genre("Sleeping", "213"),
            Genre("Small Boobs", "84"),
            Genre("Sports", "83"),
            Genre("Stockings", "81"),
            Genre("Supernatural", "85"),
            Genre("Sweating", "250"),
            Genre("Swimsuit", "86"),
            Genre("Teacher", "91"),
            Genre("Tentacles", "89"),
            Genre("Time Stop", "109"),
            Genre("Tomboy", "90"),
            Genre("Tracksuit", "252"),
            Genre("Transformation", "256"),
            Genre("Trap", "92"),
            Genre("Tsundere", "111"),
            Genre("Tự sướng", "65"),
            Genre("Twins", "93"),
            Genre("Vampire", "107"),
            Genre("Vanilla", "208"),
            Genre("Virgin", "95"),
            Genre("X-ray", "94"),
            Genre("Yandere", "112"),
            Genre("Yaoi", "96"),
            Genre("Yuri", "97"),
            Genre("Zombie", "128")
    )

    // jQuery.makeArray($('#container > div > div > div.box-box.textbox > form > ul:nth-child(8) > li').map((i, e) => `TransGroup("${e.textContent}", "${e.children[0].value}")`)).join(',\n')
    // https://hentaivn.net/forum/search-plus.php
    private fun getGroupList() = arrayOf(
            TransGroup("Tất cả", "0"),
            TransGroup("Đang cập nhật", "1"),
            TransGroup("Góc Hentai", "3"),
            TransGroup("Hakihome", "4"),
            TransGroup("LXERS", "5"),
            TransGroup("Hentai-Homies", "6"),
            TransGroup("BUZPLANET", "7"),
            TransGroup("Trang Sally", "8"),
            TransGroup("Loli Rules The World", "9"),
            TransGroup("XXX Inc", "10"),
            TransGroup("Kobato9x", "11"),
            TransGroup("Blazing Soul", "12"),
            TransGroup("TAYXUONG", "13"),
            TransGroup("[S]ky [G]arden [G]roup", "14"),
            TransGroup("Bloomer-kun", "15"),
            TransGroup("DHT", "16"),
            TransGroup("TruyenHen18", "17"),
            TransGroup("iHentaiManga", "18"),
            TransGroup("Quân cảng Kancolle X", "19"),
            TransGroup("LHMANGA", "20"),
            TransGroup("Ship of The Dream", "21"),
            TransGroup("Fallen Angels", "22"),
            TransGroup("TruyenHentai2H", "23"),
            TransGroup("Lạc Thiên", "24"),
            TransGroup("69HENTAIXXX", "25"),
            TransGroup("DHL", "26"),
            TransGroup("Hentai-AdutsManga", "27"),
            TransGroup("Hatsu Kaze Desu Translator Team", "28"),
            TransGroup("IHentai69", "29"),
            TransGroup("Zest", "30"),
            TransGroup("Demon Victory Team", "31"),
            TransGroup("NTR Victory Team", "32"),
            TransGroup("Rori Saikou", "33"),
            TransGroup("Bullet Burn Team", "34"),
            TransGroup("RE Team", "35"),
            TransGroup("Rebelliones", "36"),
            TransGroup("Shinto", "37"),
            TransGroup("Sexual Paradise", "38"),
            TransGroup("FA Dislike Team", "39"),
            TransGroup("Triggered Team", "41"),
            TransGroup("T.K Translation Team", "42"),
            TransGroup("Mabu MG", "43"),
            TransGroup("Team Zentsu", "44"),
            TransGroup("Sweeter Than Salt", "46"),
            TransGroup("Cà rà cà rà Cặt", "47"),
            TransGroup("Paradise Of The Happiness", "48"),
            TransGroup("Furry Break the 4th Wall", "49"),
            TransGroup("The Ignite Team", "50"),
            TransGroup("Cuồng Loli", "51"),
            TransGroup("Depressed Lolicons Squad - DLS", "52"),
            TransGroup("Heaven Of The Fuck", "53")
    )

}
