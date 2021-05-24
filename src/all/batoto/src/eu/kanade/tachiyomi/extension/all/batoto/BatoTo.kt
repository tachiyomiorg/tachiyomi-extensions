package eu.kanade.tachiyomi.extension.all.batoto

import com.squareup.duktape.Duktape
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar
import java.util.concurrent.TimeUnit
import okhttp3.Response
import rx.Observable

open class BatoTo(
    override val lang: String,
    private val siteLang: String
) : ParsedHttpSource() {

    override val name: String = "Bato.to"
    override val baseUrl: String = "https://bato.to"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/browse?langs=$siteLang&sort=update&page=$page")
    }

    override fun latestUpdatesSelector(): String {
        return when (siteLang) {
            "" -> "div#series-list div.col"
            "en" -> "div#series-list div.col.no-flag"
            else -> "div#series-list div.col:has([data-lang=\"$siteLang\"])"
        }
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        val item = element.select("a.item-cover")
        val imgurl = item.select("img").attr("abs:src")
        manga.setUrlWithoutDomain(item.attr("href"))
        manga.title = element.select("a.item-title").text()
        manga.thumbnail_url = imgurl
        return manga
    }

    override fun latestUpdatesNextPageSelector() = "div#mainer nav.d-none .pagination .page-item:last-of-type:not(.disabled)"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/browse?langs=$siteLang&sort=views_w&page=$page")
    }

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return when {
            query.startsWith("ID:") -> {
                val id = query.substringAfter("ID:")
                client.newCall(GET("$baseUrl/series/$id", headers)).asObservableSuccess()
                    .map { response ->
                        queryIDParse(response, id)
                    }
            }
            query.isNotBlank() -> {
                val url = "$baseUrl/search?word=$query&page=$page"
                client.newCall(GET(url, headers)).asObservableSuccess()
                    .map { response ->
                        queryParse(response)
                    }
            }
            else -> {
                val sortFilter = filters.findInstance<SortFilter>()!!
                val reverseSortFilter = filters.findInstance<ReverseSortFilter>()!!
                val statusFilter = filters.findInstance<StatusFilter>()!!
                val langFilter = filters.findInstance<LangGroupFilter>()!!
                val originFilter = filters.findInstance<OriginGroupFilter>()!!
                val genreFilter = filters.findInstance<GenreGroupFilter>()!!
                val minChapterFilter = filters.findInstance<MinChapterTextFilter>()!!
                val maxChapterFilter = filters.findInstance<MaxChapterTextFilter>()!!
                val url = "$baseUrl/browse".toHttpUrlOrNull()!!.newBuilder()
                url.addQueryParameter("page", page.toString())

                with (langFilter) {
                    if (this.selected.isEmpty()) {
                        url.addQueryParameter("langs", siteLang)
                    } else {
                        val selection = "${this.selected.joinToString(",")},$siteLang"
                        url.addQueryParameter("langs", selection)
                    }
                }

                with (genreFilter) {
                    url.addQueryParameter("genres", included.joinToString(",") + "|" + excluded.joinToString(",")
                    )
                }

                with (statusFilter) {
                    url.addQueryParameter("release", this.selected)
                }

                with (sortFilter) {
                    if (reverseSortFilter.state) {
                        url.addQueryParameter("sort","${this.selected}.az")
                    } else {
                        url.addQueryParameter("sort","${this.selected}.za")
                    }
                }

                if (originFilter.selected.isNotEmpty()) {
                    url.addQueryParameter("origs", originFilter.selected.joinToString(","))
                }

                if (maxChapterFilter.state.isNotEmpty() or minChapterFilter.state.isNotEmpty()) {
                    url.addQueryParameter("chapters", minChapterFilter.state + "-" + maxChapterFilter.state)
                }

                client.newCall(GET(url.build().toString(), headers)).asObservableSuccess()
                    .map { response ->
                        queryParse(response)
                    }
            }
        }
    }

    private fun queryIDParse(response: Response, id: String): MangasPage {
        val document = response.asJsoup()
        val infoElement = document.select("div#mainer div.container-fluid")
        val manga = SManga.create()
        manga.title = infoElement.select("h3").text()
        manga.thumbnail_url = document.select("div.attr-cover img")
            .attr("abs:src")
        manga.url = infoElement.select("h3 a").attr("abs:href")
        return MangasPage(listOf(manga), false)
    }

    private fun queryParse(response: Response): MangasPage {
        val mangas = mutableListOf<SManga>()
        val document = response.asJsoup()
        document.select(latestUpdatesSelector()).forEach { element ->
            mangas.add(latestUpdatesFromElement(element))
        }
        val nextPage = document.select(latestUpdatesNextPageSelector()).first() != null
        return MangasPage(mangas, nextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException("Not used")
    override fun searchMangaSelector() = throw UnsupportedOperationException("Not used")
    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException("Not used")
    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("Not used")

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.mangaDetailsRequest(manga)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div#mainer div.container-fluid")
        val manga = SManga.create()
        val genres = mutableListOf<String>()
        val status = infoElement.select("div.attr-item:contains(status) span").text()
        infoElement.select("div.attr-item:contains(genres) span").text().split(
            " / "
                .toRegex()
        ).forEach { element ->
            genres.add(element)
        }
        manga.title = infoElement.select("h3").text()
        manga.author = infoElement.select("div.attr-item:contains(author) a:first-child").text()
        manga.artist = infoElement.select("div.attr-item:contains(author) a:last-child").text()
        manga.status = parseStatus(status)
        manga.genre = infoElement.select(".attr-item b:contains(genres) + span ").joinToString { it.text() }
        manga.description = infoElement.select("h5:contains(summary) + pre").text()
        manga.thumbnail_url = document.select("div.attr-cover img")
            .attr("abs:src")
        return manga
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.chapterListRequest(manga)
    }

    override fun chapterListSelector() = "div.main div.p-2"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val urlElement = element.select("a.chapt")
        val group = element.select("div.extra > a:not(.ps-3)").text()
        val time = element.select("div.extra > i.ps-3").text()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        if (group != "") {
            chapter.scanlator = group
        }
        if (time != "") {
            chapter.date_upload = parseChapterDate(time)
        }
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        val value = date.split(' ')[0].toInt()

        return when {
            "secs" in date -> Calendar.getInstance().apply {
                add(Calendar.SECOND, value * -1)
            }.timeInMillis
            "mins" in date -> Calendar.getInstance().apply {
                add(Calendar.MINUTE, value * -1)
            }.timeInMillis
            "hours" in date -> Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, value * -1)
            }.timeInMillis
            "days" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * -1)
            }.timeInMillis
            "weeks" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * 7 * -1)
            }.timeInMillis
            "months" in date -> Calendar.getInstance().apply {
                add(Calendar.MONTH, value * -1)
            }.timeInMillis
            "years" in date -> Calendar.getInstance().apply {
                add(Calendar.YEAR, value * -1)
            }.timeInMillis
            "sec" in date -> Calendar.getInstance().apply {
                add(Calendar.SECOND, value * -1)
            }.timeInMillis
            "min" in date -> Calendar.getInstance().apply {
                add(Calendar.MINUTE, value * -1)
            }.timeInMillis
            "hour" in date -> Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, value * -1)
            }.timeInMillis
            "day" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * -1)
            }.timeInMillis
            "week" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * 7 * -1)
            }.timeInMillis
            "month" in date -> Calendar.getInstance().apply {
                add(Calendar.MONTH, value * -1)
            }.timeInMillis
            "year" in date -> Calendar.getInstance().apply {
                add(Calendar.YEAR, value * -1)
            }.timeInMillis
            else -> {
                return 0
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("http")) {
            return GET(chapter.url, headers)
        }
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        val script = document.select("script").html()

        if (script.contains("var images =")) {
            val imgJson = JSONObject(script.substringAfter("var images = ").substringBefore(";"))
            val imgNames = imgJson.names()

            if (imgNames != null) {
                for (i in 0 until imgNames.length()) {
                    val imgKey = imgNames.getString(i)
                    val imgUrl = imgJson.getString(imgKey)
                    pages.add(Page(i, "", imgUrl))
                }
            }
        } else if (script.contains("const server =")) { // bato.to
            val duktape = Duktape.create()
            val encryptedServer = script.substringAfter("const server = ").substringBefore(";")
            val batojs = duktape.evaluate(script.substringAfter("const batojs = ").substringBefore(";")).toString()
            val decryptScript = cryptoJS + "CryptoJS.AES.decrypt($encryptedServer, \"$batojs\").toString(CryptoJS.enc.Utf8);"
            val server = duktape.evaluate(decryptScript).toString().replace("\"", "")
            duktape.close()

            val imgArray = JSONArray(script.substringAfter("const images = ").substringBefore(";"))
            if (imgArray != null) {
                if (script.contains("bato.to/images")) {
                    for (i in 0 until imgArray.length()) {
                        val imgUrl = imgArray.get(i)
                        pages.add(Page(i, "", "$imgUrl"))
                    }
                } else {
                    for (i in 0 until imgArray.length()) {
                        val imgUrl = imgArray.get(i)
                        if (server.startsWith("http"))
                            pages.add(Page(i, "", "${server}$imgUrl"))
                        else
                            pages.add(Page(i, "", "https:${server}$imgUrl"))
                    }
                }
            }
        }

        return pages
    }

    private val cryptoJS by lazy {
        client.newCall(
            GET(
                "https://cdnjs.cloudflare.com/ajax/libs/crypto-js/4.0.0/crypto-js.min.js",
                headers
            )
        ).execute().body!!.string()
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList(
        //LetterFilter(),
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        SortFilter(getSortFilter(), 5),
        StatusFilter(getStatusFilter(), 0),
        GenreGroupFilter(getGenreFilter()),
        OriginGroupFilter(getOrginFilter()),
        LangGroupFilter(getLangFilter()),
        MinChapterTextFilter(),
        MaxChapterTextFilter(),
        ReverseSortFilter(),
    )

    class SelectFilterOption(val name: String, val value: String)
    class CheckboxFilterOption(val value: String, name: String, default: Boolean = false) : Filter.CheckBox(name, default)
    class TriStateFilterOption(val value: String, name: String, default: Int = 0) : Filter.TriState(name, default)

    abstract class SelectFilter(name: String, private val options: List<SelectFilterOption>, default: Int = 0) : Filter.Select<String>(name, options.map { it.name }.toTypedArray(), default) {
        val selected: String
            get() = options[state].value
    }
    abstract class CheckboxGroupFilter(name: String, options: List<CheckboxFilterOption>) : Filter.Group<CheckboxFilterOption>(name, options) {
        val selected: List<String>
            get() = state.filter { it.state }.map { it.value }
    }
    abstract class TriStateGroupFilter(name: String, options: List<TriStateFilterOption>) : Filter.Group<TriStateFilterOption>(name, options) {
        val included: List<String>
            get() = state.filter { it.isIncluded() }.map { it.value }

        val excluded: List<String>
            get() = state.filter { it.isExcluded() }.map { it.value }
    }
    abstract class TextFilter(name: String) : Filter.Text(name)

    class SortFilter(options: List<SelectFilterOption>, default: Int) : SelectFilter("Sort By", options, default)
    class ReverseSortFilter(default: Boolean = false) : Filter.CheckBox("Revers Sort", default)
    class StatusFilter(options: List<SelectFilterOption>, default: Int) : SelectFilter("Status", options, default)
    class OriginGroupFilter(options: List<CheckboxFilterOption>) : CheckboxGroupFilter("Origin", options)
    class GenreGroupFilter(options: List<TriStateFilterOption>) : TriStateGroupFilter("Genre", options)
    class MinChapterTextFilter : TextFilter("Min. Chapters")
    class MaxChapterTextFilter : TextFilter("Max. Chapters")
    class LangGroupFilter(options: List<CheckboxFilterOption>) : CheckboxGroupFilter("Languages", options)
    class LetterFilter(default: Boolean = false) : Filter.CheckBox("Letter matching mode (Slow)", default)

    private fun getSortFilter() = listOf(
        SelectFilterOption("Z-A", "title"),
        SelectFilterOption("Last Updated", "update"),
        SelectFilterOption("Newest Added", "create"),
        SelectFilterOption("Most Views Totally", "views_a"),
        SelectFilterOption("Most Views 365 days", "views_y"),
        SelectFilterOption("Most Views 30 days", "views_m"),
        SelectFilterOption("Most Views 7 days", "views_w"),
        SelectFilterOption("Most Views 24 hours", "views_d"),
        SelectFilterOption("Most Views 60 minutes", "views_h"),
    )

    private fun getStatusFilter() = listOf(
        SelectFilterOption("All", ""),
        SelectFilterOption("Pending", "pending"),
        SelectFilterOption("Ongoing", "ongoing"),
        SelectFilterOption("Completed", "completed"),
        SelectFilterOption("Hiatus", "hiatus"),
        SelectFilterOption("Cancelled", "cancelled"),
    )

    private fun getOrginFilter() = listOf(
        // Values exported from publish.bato.to
        CheckboxFilterOption("zh","Chinese"),
        CheckboxFilterOption("en","English"),
        CheckboxFilterOption("ja","Japanese"),
        CheckboxFilterOption("ko","Korean"),
        CheckboxFilterOption("af","Afrikaans"),
        CheckboxFilterOption("sq","Albanian"),
        CheckboxFilterOption("am","Amharic"),
        CheckboxFilterOption("ar","Arabic"),
        CheckboxFilterOption("hy","Armenian"),
        CheckboxFilterOption("az","Azerbaijani"),
        CheckboxFilterOption("be","Belarusian"),
        CheckboxFilterOption("bn","Bengali"),
        CheckboxFilterOption("bs","Bosnian"),
        CheckboxFilterOption("bg","Bulgarian"),
        CheckboxFilterOption("my","Burmese"),
        CheckboxFilterOption("km","Cambodian"),
        CheckboxFilterOption("ca","Catalan"),
        CheckboxFilterOption("ceb","Cebuano"),
        CheckboxFilterOption("zh_hk","Chinese (Cantonese)"),
        CheckboxFilterOption("zh_tw","Chinese (Traditional)"),
        CheckboxFilterOption("hr","Croatian"),
        CheckboxFilterOption("cs","Czech"),
        CheckboxFilterOption("da","Danish"),
        CheckboxFilterOption("nl","Dutch"),
        CheckboxFilterOption("en_us","English (United States)"),
        CheckboxFilterOption("eo","Esperanto"),
        CheckboxFilterOption("et","Estonian"),
        CheckboxFilterOption("fo","Faroese"),
        CheckboxFilterOption("fil","Filipino"),
        CheckboxFilterOption("fi","Finnish"),
        CheckboxFilterOption("fr","French"),
        CheckboxFilterOption("ka","Georgian"),
        CheckboxFilterOption("de","German"),
        CheckboxFilterOption("el","Greek"),
        CheckboxFilterOption("gn","Guarani"),
        CheckboxFilterOption("gu","Gujarati"),
        CheckboxFilterOption("ht","Haitian Creole"),
        CheckboxFilterOption("ha","Hausa"),
        CheckboxFilterOption("he","Hebrew"),
        CheckboxFilterOption("hi","Hindi"),
        CheckboxFilterOption("hu","Hungarian"),
        CheckboxFilterOption("is","Icelandic"),
        CheckboxFilterOption("ig","Igbo"),
        CheckboxFilterOption("id","Indonesian"),
        CheckboxFilterOption("ga","Irish"),
        CheckboxFilterOption("it","Italian"),
        CheckboxFilterOption("jv","Javanese"),
        CheckboxFilterOption("kn","Kannada"),
        CheckboxFilterOption("kk","Kazakh"),
        CheckboxFilterOption("ku","Kurdish"),
        CheckboxFilterOption("ky","Kyrgyz"),
        CheckboxFilterOption("lo","Laothian"),
        CheckboxFilterOption("lv","Latvian"),
        CheckboxFilterOption("lt","Lithuanian"),
        CheckboxFilterOption("lb","Luxembourgish"),
        CheckboxFilterOption("mk","Macedonian"),
        CheckboxFilterOption("mg","Malagasy"),
        CheckboxFilterOption("ms","Malay"),
        CheckboxFilterOption("ml","Malayalam"),
        CheckboxFilterOption("mt","Maltese"),
        CheckboxFilterOption("mi","Maori"),
        CheckboxFilterOption("mr","Marathi"),
        CheckboxFilterOption("mo","Moldavian"),
        CheckboxFilterOption("mn","Mongolian"),
        CheckboxFilterOption("ne","Nepali"),
        CheckboxFilterOption("no","Norwegian"),
        CheckboxFilterOption("ny","Nyanja"),
        CheckboxFilterOption("ps","Pashto"),
        CheckboxFilterOption("fa","Persian"),
        CheckboxFilterOption("pl","Polish"),
        CheckboxFilterOption("pt","Portuguese"),
        CheckboxFilterOption("pt_br","Portuguese (Brazil)"),
        CheckboxFilterOption("ro","Romanian"),
        CheckboxFilterOption("rm","Romansh"),
        CheckboxFilterOption("ru","Russian"),
        CheckboxFilterOption("sm","Samoan"),
        CheckboxFilterOption("sr","Serbian"),
        CheckboxFilterOption("sh","Serbo-Croatian"),
        CheckboxFilterOption("st","Sesotho"),
        CheckboxFilterOption("sn","Shona"),
        CheckboxFilterOption("sd","Sindhi"),
        CheckboxFilterOption("si","Sinhalese"),
        CheckboxFilterOption("sk","Slovak"),
        CheckboxFilterOption("sl","Slovenian"),
        CheckboxFilterOption("so","Somali"),
        CheckboxFilterOption("es","Spanish"),
        CheckboxFilterOption("es_419","Spanish (Latin America)"),
        CheckboxFilterOption("sw","Swahili"),
        CheckboxFilterOption("sv","Swedish"),
        CheckboxFilterOption("tg","Tajik"),
        CheckboxFilterOption("ta","Tamil"),
        CheckboxFilterOption("th","Thai"),
        CheckboxFilterOption("ti","Tigrinya"),
        CheckboxFilterOption("to","Tonga"),
        CheckboxFilterOption("tr","Turkish"),
        CheckboxFilterOption("tk","Turkmen"),
        CheckboxFilterOption("uk","Ukrainian"),
        CheckboxFilterOption("ur","Urdu"),
        CheckboxFilterOption("uz","Uzbek"),
        CheckboxFilterOption("vi","Vietnamese"),
        CheckboxFilterOption("yo","Yoruba"),
        CheckboxFilterOption("zu","Zulu"),
        CheckboxFilterOption("_t","Other"),
    )

    private fun getGenreFilter() = listOf(
        TriStateFilterOption("artbook","Artbook"),
        TriStateFilterOption("cartoon","Cartoon"),
        TriStateFilterOption("comic","Comic"),
        TriStateFilterOption("doujinshi","Doujinshi"),
        TriStateFilterOption("imageset","Imageset"),
        TriStateFilterOption("manga","Manga"),
        TriStateFilterOption("manhua","Manhua"),
        TriStateFilterOption("manhwa","Manhwa"),
        TriStateFilterOption("webtoon","Webtoon"),
        TriStateFilterOption("western","Western"),
        TriStateFilterOption("josei","Josei"),
        TriStateFilterOption("seinen","Seinen"),
        TriStateFilterOption("shoujo","Shoujo"),
        TriStateFilterOption("shoujo_ai","Shoujo ai"),
        TriStateFilterOption("shounen","Shounen"),
        TriStateFilterOption("shounen_ai","Shounen ai"),
        TriStateFilterOption("yaoi","Yaoi"),
        TriStateFilterOption("yuri","Yuri"),
        TriStateFilterOption("ecchi","Ecchi"),
        TriStateFilterOption("mature","Mature"),
        TriStateFilterOption("adult","Adult"),
        TriStateFilterOption("gore","Gore"),
        TriStateFilterOption("violence","Violence"),
        TriStateFilterOption("smut","Smut"),
        TriStateFilterOption("hentai","Hentai"),
        TriStateFilterOption("_4_koma","4-Koma"),
        TriStateFilterOption("action","Action"),
        TriStateFilterOption("adaptation","Adaptation"),
        TriStateFilterOption("adventure","Adventure"),
        TriStateFilterOption("aliens","Aliens"),
        TriStateFilterOption("animals","Animals"),
        TriStateFilterOption("anthology","Anthology"),
        TriStateFilterOption("cars","cars"),
        TriStateFilterOption("comedy","Comedy"),
        TriStateFilterOption("cooking","Cooking"),
        TriStateFilterOption("crime","crime"),
        TriStateFilterOption("crossdressing","Crossdressing"),
        TriStateFilterOption("delinquents","Delinquents"),
        TriStateFilterOption("dementia","Dementia"),
        TriStateFilterOption("demons","Demons"),
        TriStateFilterOption("drama","Drama"),
        TriStateFilterOption("fantasy","Fantasy"),
        TriStateFilterOption("fan_colored","Fan-Colored"),
        TriStateFilterOption("full_color","Full Color"),
        TriStateFilterOption("game","Game"),
        TriStateFilterOption("gender_bender","Gender Bender"),
        TriStateFilterOption("genderswap","Genderswap"),
        TriStateFilterOption("ghosts","Ghosts"),
        TriStateFilterOption("gyaru","Gyaru"),
        TriStateFilterOption("harem","Harem"),
        TriStateFilterOption("harlequin","Harlequin"),
        TriStateFilterOption("historical","Historical"),
        TriStateFilterOption("horror","Horror"),
        TriStateFilterOption("incest","Incest"),
        TriStateFilterOption("isekai","Isekai"),
        TriStateFilterOption("kids","Kids"),
        TriStateFilterOption("loli","Loli"),
        TriStateFilterOption("lolicon","lolicon"),
        TriStateFilterOption("magic","Magic"),
        TriStateFilterOption("magical_girls","Magical Girls"),
        TriStateFilterOption("martial_arts","Martial Arts"),
        TriStateFilterOption("mecha","Mecha"),
        TriStateFilterOption("medical","Medical"),
        TriStateFilterOption("military","Military"),
        TriStateFilterOption("monster_girls","Monster Girls"),
        TriStateFilterOption("monsters","Monsters"),
        TriStateFilterOption("music","Music"),
        TriStateFilterOption("mystery","Mystery"),
        TriStateFilterOption("netorare","Netorare/NTR"),
        TriStateFilterOption("ninja","Ninja"),
        TriStateFilterOption("office_workers","Office Workers"),
        TriStateFilterOption("oneshot","Oneshot"),
        TriStateFilterOption("parody","parody"),
        TriStateFilterOption("philosophical","Philosophical"),
        TriStateFilterOption("police","Police"),
        TriStateFilterOption("post_apocalyptic","Post-Apocalyptic"),
        TriStateFilterOption("psychological","Psychological"),
        TriStateFilterOption("reincarnation","Reincarnation"),
        TriStateFilterOption("reverse_harem","Reverse Harem"),
        TriStateFilterOption("romance","Romance"),
        TriStateFilterOption("samurai","Samurai"),
        TriStateFilterOption("school_life","School Life"),
        TriStateFilterOption("sci_fi","Sci-Fi"),
        TriStateFilterOption("shota","Shota"),
        TriStateFilterOption("shotacon","shotacon"),
        TriStateFilterOption("slice_of_life","Slice of Life"),
        TriStateFilterOption("sm_bdsm","SM/BDSM"),
        TriStateFilterOption("space","Space"),
        TriStateFilterOption("sports","Sports"),
        TriStateFilterOption("super_power","Super Power"),
        TriStateFilterOption("superhero","Superhero"),
        TriStateFilterOption("supernatural","Supernatural"),
        TriStateFilterOption("survival","Survival"),
        TriStateFilterOption("thriller","Thriller"),
        TriStateFilterOption("time_travel","Time Travel"),
        TriStateFilterOption("traditional_games","Traditional Games"),
        TriStateFilterOption("tragedy","Tragedy"),
        TriStateFilterOption("vampires","Vampires"),
        TriStateFilterOption("video_games","Video Games"),
        TriStateFilterOption("virtual_reality","Virtual Reality"),
        TriStateFilterOption("wuxia","Wuxia"),
        TriStateFilterOption("xianxia","Xianxia"),
        TriStateFilterOption("xuanhuan","Xuanhuan"),
        TriStateFilterOption("zombies","Zombies"),
        // Hidden Genres
        TriStateFilterOption("award_winning", "Award Winning"),
        TriStateFilterOption("youkai", "Youkai"),
        TriStateFilterOption("uncategorized", "Uncategorized")
    )

    private fun getLangFilter() = listOf(
        // Values exported from publish.bato.to
        CheckboxFilterOption("en","English"),
        CheckboxFilterOption("ar","Arabic"),
        CheckboxFilterOption("bg","Bulgarian"),
        CheckboxFilterOption("zh","Chinese"),
        CheckboxFilterOption("cs","Czech"),
        CheckboxFilterOption("da","Danish"),
        CheckboxFilterOption("nl","Dutch"),
        CheckboxFilterOption("fil","Filipino"),
        CheckboxFilterOption("fi","Finnish"),
        CheckboxFilterOption("fr","French"),
        CheckboxFilterOption("de","German"),
        CheckboxFilterOption("el","Greek"),
        CheckboxFilterOption("he","Hebrew"),
        CheckboxFilterOption("hi","Hindi"),
        CheckboxFilterOption("hu","Hungarian"),
        CheckboxFilterOption("id","Indonesian"),
        CheckboxFilterOption("it","Italian"),
        CheckboxFilterOption("ja","Japanese"),
        CheckboxFilterOption("ko","Korean"),
        CheckboxFilterOption("ms","Malay"),
        CheckboxFilterOption("pl","Polish"),
        CheckboxFilterOption("pt","Portuguese"),
        CheckboxFilterOption("pt_br","Portuguese (Brazil)"),
        CheckboxFilterOption("ro","Romanian"),
        CheckboxFilterOption("ru","Russian"),
        CheckboxFilterOption("es","Spanish"),
        CheckboxFilterOption("es_419","Spanish (Latin America)"),
        CheckboxFilterOption("sv","Swedish"),
        CheckboxFilterOption("th","Thai"),
        CheckboxFilterOption("tr","Turkish"),
        CheckboxFilterOption("uk","Ukrainian"),
        CheckboxFilterOption("vi","Vietnamese"),
        CheckboxFilterOption("af","Afrikaans"),
        CheckboxFilterOption("sq","Albanian"),
        CheckboxFilterOption("am","Amharic"),
        CheckboxFilterOption("hy","Armenian"),
        CheckboxFilterOption("az","Azerbaijani"),
        CheckboxFilterOption("be","Belarusian"),
        CheckboxFilterOption("bn","Bengali"),
        CheckboxFilterOption("bs","Bosnian"),
        CheckboxFilterOption("my","Burmese"),
        CheckboxFilterOption("km","Cambodian"),
        CheckboxFilterOption("ca","Catalan"),
        CheckboxFilterOption("ceb","Cebuano"),
        CheckboxFilterOption("zh_hk","Chinese (Cantonese)"),
        CheckboxFilterOption("zh_tw","Chinese (Traditional)"),
        CheckboxFilterOption("hr","Croatian"),
        CheckboxFilterOption("en_us","English (United States)"),
        CheckboxFilterOption("eo","Esperanto"),
        CheckboxFilterOption("et","Estonian"),
        CheckboxFilterOption("fo","Faroese"),
        CheckboxFilterOption("ka","Georgian"),
        CheckboxFilterOption("gn","Guarani"),
        CheckboxFilterOption("gu","Gujarati"),
        CheckboxFilterOption("ht","Haitian Creole"),
        CheckboxFilterOption("ha","Hausa"),
        CheckboxFilterOption("is","Icelandic"),
        CheckboxFilterOption("ig","Igbo"),
        CheckboxFilterOption("ga","Irish"),
        CheckboxFilterOption("jv","Javanese"),
        CheckboxFilterOption("kn","Kannada"),
        CheckboxFilterOption("kk","Kazakh"),
        CheckboxFilterOption("ku","Kurdish"),
        CheckboxFilterOption("ky","Kyrgyz"),
        CheckboxFilterOption("lo","Laothian"),
        CheckboxFilterOption("lv","Latvian"),
        CheckboxFilterOption("lt","Lithuanian"),
        CheckboxFilterOption("lb","Luxembourgish"),
        CheckboxFilterOption("mk","Macedonian"),
        CheckboxFilterOption("mg","Malagasy"),
        CheckboxFilterOption("ml","Malayalam"),
        CheckboxFilterOption("mt","Maltese"),
        CheckboxFilterOption("mi","Maori"),
        CheckboxFilterOption("mr","Marathi"),
        CheckboxFilterOption("mo","Moldavian"),
        CheckboxFilterOption("mn","Mongolian"),
        CheckboxFilterOption("ne","Nepali"),
        CheckboxFilterOption("no","Norwegian"),
        CheckboxFilterOption("ny","Nyanja"),
        CheckboxFilterOption("ps","Pashto"),
        CheckboxFilterOption("fa","Persian"),
        CheckboxFilterOption("rm","Romansh"),
        CheckboxFilterOption("sm","Samoan"),
        CheckboxFilterOption("sr","Serbian"),
        CheckboxFilterOption("sh","Serbo-Croatian"),
        CheckboxFilterOption("st","Sesotho"),
        CheckboxFilterOption("sn","Shona"),
        CheckboxFilterOption("sd","Sindhi"),
        CheckboxFilterOption("si","Sinhalese"),
        CheckboxFilterOption("sk","Slovak"),
        CheckboxFilterOption("sl","Slovenian"),
        CheckboxFilterOption("so","Somali"),
        CheckboxFilterOption("sw","Swahili"),
        CheckboxFilterOption("tg","Tajik"),
        CheckboxFilterOption("ta","Tamil"),
        CheckboxFilterOption("ti","Tigrinya"),
        CheckboxFilterOption("to","Tonga"),
        CheckboxFilterOption("tk","Turkmen"),
        CheckboxFilterOption("ur","Urdu"),
        CheckboxFilterOption("uz","Uzbek"),
        CheckboxFilterOption("yo","Yoruba"),
        CheckboxFilterOption("zu","Zulu"),
        CheckboxFilterOption("_t","Other"),
        // Lang options from bato.to brows not in publish.bato.to
        CheckboxFilterOption("eu", "Basque"),
        CheckboxFilterOption("pt-PT","Portuguese (Portugal)"),
    ).filterNot { it.value == siteLang }

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T
}
