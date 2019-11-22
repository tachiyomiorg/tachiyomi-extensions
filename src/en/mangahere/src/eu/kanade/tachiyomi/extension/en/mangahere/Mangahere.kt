package eu.kanade.tachiyomi.extension.en.mangahere

import com.squareup.duktape.Duktape
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class Mangahere : ParsedHttpSource() {

    override val id: Long = 2

    override val name = "Mangahere"

    override val baseUrl = "http://www.mangahere.cc"

    override val lang = "en"

    override val supportsLatest = true

    override val client = super.client.newBuilder()
            .cookieJar(object : CookieJar{
                override fun saveFromResponse(url: HttpUrl, cookies: MutableList<Cookie>) {}
                override fun loadForRequest(url: HttpUrl): MutableList<Cookie> {
                    return ArrayList<Cookie>().apply {
                        add(Cookie.Builder()
                                .domain("www.mangahere.cc")
                                .path("/")
                                .name("isAdult")
                                .value("1")
                                .build()) }
                }

            })
            .build()

    override fun popularMangaSelector() = ".manga-list-1-list li"

    override fun latestUpdatesSelector() = ".manga-list-1-list li"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/directory/$page.htm", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/directory/$page.htm?latest", headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        val titleElement = element.select("a").first()
        manga.title = titleElement.attr("title")
        manga.setUrlWithoutDomain(titleElement.attr("href"))
        manga.thumbnail_url = element.select("img.manga-list-1-cover")
                ?.first()?.attr("src")

        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun popularMangaNextPageSelector() = "div.pager-list-left a:last-child"

    override fun latestUpdatesNextPageSelector() = "div.pager-list-left a:last-child"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/search")!!.newBuilder()

        filters.forEach {
            when(it) {

                is TypeList -> {
                    url.addEncodedQueryParameter("type", types[it.values[it.state]].toString())
                }
                is CompletionList -> url.addEncodedQueryParameter("st", it.state.toString())
                is GenreList -> {

                    val genreFilter = filters.find { it is GenreList } as GenreList?
                    val includeGenres = ArrayList<Int>()
                    val excludeGenres = ArrayList<Int>()
                    genreFilter?.state?.forEach { genre ->
                        if (genre.isIncluded())
                            includeGenres.add(genre.id)
                        else if (genre.isExcluded())
                            excludeGenres.add(genre.id)
                    }

                    url.addEncodedQueryParameter("genres", includeGenres.joinToString(","))
                            .addEncodedQueryParameter("nogenres", excludeGenres.joinToString(","))
                }

            }
        }

        url.addEncodedQueryParameter("page", page.toString())
                .addEncodedQueryParameter("title", query)
                .addEncodedQueryParameter("sort", null)
                .addEncodedQueryParameter("stype", 1.toString())
                .addEncodedQueryParameter("name", null)
                .addEncodedQueryParameter("author_method","cw")
                .addEncodedQueryParameter("author", null)
                .addEncodedQueryParameter("artist_method", "cw")
                .addEncodedQueryParameter("artist", null)
                .addEncodedQueryParameter("rating_method","eq")
                .addEncodedQueryParameter("rating",null)
                .addEncodedQueryParameter("released_method","eq")
                .addEncodedQueryParameter("released", null)

        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = ".manga-list-4-list > li"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val titleEl = element.select(".manga-list-4-item-title > a").first()
        manga.setUrlWithoutDomain(titleEl?.attr("href") ?: "")
        manga.title = titleEl?.attr("title") ?: ""
        return manga
    }

    override fun searchMangaNextPageSelector() = "div.pager-list-left a:last-child"

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.author = document.select(".detail-info-right-say > a")?.first()?.text()
        manga.artist = ""
        manga.genre = document.select(".detail-info-right-tag-list > a")?.joinToString { it.text() }
        manga.description = document.select(".fullcontent")?.first()?.text()
        manga.thumbnail_url = document.select("img.detail-info-cover-img")?.first()
                ?.attr("src")

        document.select("span.detail-info-right-title-tip")?.first()?.text()?.also { statusText ->
            when {
                statusText.contains("ongoing", true) -> manga.status = SManga.ONGOING
                statusText.contains("completed", true) -> manga.status = SManga.COMPLETED
                else -> manga.status = SManga.UNKNOWN
            }
        }

        // Get a chapter, check if the manga is licensed.
        val aChapterURL = chapterFromElement(document.select(chapterListSelector()).first()).url
        val aChapterDocument = client.newCall(GET("$baseUrl$aChapterURL", headers)).execute().asJsoup()
        if (aChapterDocument.select("p.detail-block-content").hasText()) manga.status = SManga.LICENSED

        return manga
    }

    override fun chapterListSelector() = "ul.detail-main-list > li"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.select("a").first().attr("href"))
        chapter.name = element.select("a p.title3").first().text()
        chapter.date_upload = element.select("a p.title2").first()?.text()?.let { parseChapterDate(it) } ?: 0
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        return if ("Today" in date || " ago" in date){
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else if ("Yesterday" in date) {
            Calendar.getInstance().apply {
                add(Calendar.DATE, -1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else {
            try {
                SimpleDateFormat("MMM dd,yyyy", Locale.ENGLISH).parse(date).time
            } catch (e: ParseException) {
                0L
            }
			}
    }

    override fun pageListParse(document: Document): List<Page> {
        val bar = document.select("script[src*=chapter_bar]")
        if (!bar.isNullOrEmpty()){
            val script = document.select("script:containsData(function(p,a,c,k,e,d))").html().removePrefix("eval")
            val duktape = Duktape.create()
            val DeobfuscatedScript = duktape.evaluate(script).toString()
            val urls = DeobfuscatedScript.substringAfter("newImgs=['").substringBefore("'];").split("','")
            duktape.close()
            val pages = mutableListOf<Page>()
            urls.forEachIndexed { index, s ->
                pages.add(Page(index, "", "http:$s"))
            }
            return pages
        } else {

        val html = document.html()
        val link = document.location()

        val pages = mutableListOf<Page>()

        val duktape = Duktape.create()

        var secretKey = extractSecretKey(html, duktape)

        val chapterIdStartLoc =  html.indexOf("chapterid")
        val chapterId = html.substring(
                chapterIdStartLoc + 11,
                html.indexOf(";", chapterIdStartLoc)).trim()

        val chapterPagesElement = document.select(".pager-list-left > span").first()
        val pagesLinksElements = chapterPagesElement.select("a")
        val pagesNumber = pagesLinksElements.get(pagesLinksElements.size - 2).attr("data-page").toInt()

        val pageBase = link.substring(0, link.lastIndexOf("/"))

        for (i in 1..pagesNumber){

            val pageLink = "${pageBase}/chapterfun.ashx?cid=$chapterId&page=$i&key=$secretKey"

            var responseText = ""

            for (tr in 1..3){

                val request = Request.Builder()
                        .url(pageLink)
                        .addHeader("Referer",link)
                        .addHeader("Accept","*/*")
                        .addHeader("Accept-Language","en-US,en;q=0.9")
                        .addHeader("Connection","keep-alive")
                        .addHeader("Host","www.mangahere.cc")
                        .addHeader("User-Agent", System.getProperty("http.agent") ?: "")
                        .addHeader("X-Requested-With","XMLHttpRequest")
                        .build()

                val response = client.newCall(request).execute()
                responseText = response.body()!!.string()

                if (responseText.isNotEmpty())
                    break
                else
                    secretKey = ""

            }

            val deobfuscatedScript = duktape.evaluate(responseText.removePrefix("eval")).toString()

            val baseLinkStartPos = deobfuscatedScript.indexOf("pix=") + 5
            val baseLinkEndPos = deobfuscatedScript.indexOf(";", baseLinkStartPos) - 1
            val baseLink = deobfuscatedScript.substring(baseLinkStartPos, baseLinkEndPos)

            val imageLinkStartPos = deobfuscatedScript.indexOf("pvalue=") + 9
            val imageLinkEndPos = deobfuscatedScript.indexOf("\"", imageLinkStartPos)
            val imageLink = deobfuscatedScript.substring(imageLinkStartPos, imageLinkEndPos)

            pages.add(Page(i, "", "http:$baseLink$imageLink"))

        }

        duktape.close()

        return pages
        }
    }

    private fun extractSecretKey(html: String, duktape: Duktape): String {

        val secretKeyScriptLocation = html.indexOf("eval(function(p,a,c,k,e,d)")
        val secretKeyScriptEndLocation = html.indexOf("</script>", secretKeyScriptLocation)
        val secretKeyScript = html.substring(secretKeyScriptLocation, secretKeyScriptEndLocation).removePrefix("eval")

        val secretKeyDeobfuscatedScript = duktape.evaluate(secretKeyScript).toString()

        val secretKeyStartLoc = secretKeyDeobfuscatedScript.indexOf("'")
        val secretKeyEndLoc = secretKeyDeobfuscatedScript.indexOf(";")

        val secretKeyResultScript = secretKeyDeobfuscatedScript.substring(
                secretKeyStartLoc, secretKeyEndLoc)

        return duktape.evaluate(secretKeyResultScript).toString()

    }

    override fun imageUrlParse(document: Document) = document.getElementById("image").attr("src")

    private class Genre(title: String, val id: Int) : Filter.TriState(title)

    private class TypeList(types: Array<String>) : Filter.Select<String>("Type", types,0)
    private class CompletionList(completions: Array<String>) : Filter.Select<String>("Completed series", completions,0)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    override fun getFilterList() = FilterList(
            TypeList(types.keys.toList().sorted().toTypedArray()),
            CompletionList(completions),
            GenreList(genres)
    )

    private val types = hashMapOf(
            "Japanese Manga" to 1,
            "Korean Manhwa" to 2,
            "Other Manga" to 4,
            "Any" to 0
    )

    private val completions = arrayOf("Either","No","Yes")

    private val genres = arrayListOf(
            Genre("Action", 1),
            Genre("Adventure", 2),
            Genre("Comedy", 3),
            Genre("Fantasy", 4),
            Genre("Historical", 5),
            Genre("Horror", 6),
            Genre("Martial Arts", 7),
            Genre("Mystery", 8),
            Genre("Romance", 9),
            Genre("Shounen Ai", 10),
            Genre("Supernatural", 11),
            Genre("Drama", 12),
            Genre("Shounen", 13),
            Genre("School Life", 14),
            Genre("Shoujo", 15),
            Genre("Gender Bender", 16),
            Genre("Josei", 17),
            Genre("Psychological", 18),
            Genre("Seinen", 19),
            Genre("Slice of Life", 20),
            Genre("Sci-fi", 21),
            Genre("Ecchi", 22),
            Genre("Harem", 23),
            Genre("Shoujo Ai", 24),
            Genre("Yuri", 25),
            Genre("Mature", 26),
            Genre("Tragedy", 27),
            Genre("Yaoi", 28),
            Genre("Doujinshi", 29),
            Genre("Sports", 30),
            Genre("Adult", 31),
            Genre("One Shot", 32),
            Genre("Smut", 33),
            Genre("Mecha", 34),
            Genre("Shotacon", 35),
            Genre("Lolicon", 36)
    )

}
