package eu.kanade.tachiyomi.extension.en.mangafox
import android.util.Log
import com.squareup.duktape.Duktape
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class Mangafox : ParsedHttpSource() {

    override val id: Long = 3

    override val name = "Mangafox"

    override val baseUrl = "http://fanfox.net"

    override val lang = "en"

    override val supportsLatest = true

    override fun popularMangaSelector() = ".manga-list-1 > ul > li"

    override fun popularMangaRequest(page: Int): Request {
        val pageStr = if (page != 1) "$page.htm" else ""
        return GET("$baseUrl/directory/$pageStr", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int): Request {
        val pageStr = if (page != 1) "$page.htm" else ""
        return GET("$baseUrl/directory/$pageStr?latest")
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title")
            manga.thumbnail_url = it.select("img").first().attr("src")
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = ".pager-list-left a.active + a + a"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    //    http://fanfox.net/search?title=&genres=&st=0&sort=&stype=1&name_method=cw&name=&author_method=cw&author=&artist_method=cw&artist=&type=&rating_method=eq&rating=&released_method=eq&released=
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/search")!!.newBuilder()

        // Basic title search
        url.addQueryParameter("title", query)

        // More search filters
//        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
//            when (filter) {
//                is Status -> url.addQueryParameter(filter.id, filter.state.toString())
//                is GenreList -> filter.state.forEach { genre -> url.addQueryParameter(genre.id, genre.state.toString()) }
//                is TextField -> url.addQueryParameter(filter.key, filter.state)
//                is Type -> url.addQueryParameter("type", if (filter.state == 0) "" else filter.state.toString())
//                is OrderBy -> {
//                    url.addQueryParameter("sort", arrayOf("name", "rating", "views", "total_chapters", "last_chapter_time")[filter.state!!.index])
//                    url.addQueryParameter("order", if (filter.state?.ascending == true) "az" else "za")
//                }
//            }
//        }

        // Search results page
        url.addQueryParameter("page", page.toString())

        return GET(url.build().url().toString(), headers)
    }

    override fun searchMangaSelector() = ".manga-list-4 > ul > li"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select(".detail-info-right").first()

        return SManga.create().apply {
            author = infoElement.select(".detail-info-right-say a").joinToString(", ") { it.text() }
            genre = infoElement.select(".detail-info-right-tag-list a").joinToString(", ") { it.text() }
            description = infoElement.select("p.fullcontent").first()?.text()
            status = infoElement.select(".detail-info-right-title-tip").first()?.text().orEmpty().let { parseStatus(it) }
            thumbnail_url = document.select(".detail-info-cover-img").first()?.attr("src")
        }
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "ul.detail-main-list li a"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            name = element.select(".detail-main-list-main p").first()?.text().orEmpty()
            date_upload = element.select(".detail-main-list-main p").last()?.text()?.let { parseChapterDate(it) } ?: 0
        }
    }

    private fun parseChapterDate(date: String): Long {
        return if ("Today" in date || " ago" in date) {
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
                SimpleDateFormat("MMM d,yyyy", Locale.ENGLISH).parse(date).time
            } catch (e: ParseException) {
                0L
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val url = document.baseUri().substringBeforeLast('/')

        val totalPages = document.select(".pager-list-left > span > a:nth-last-child(2)").first().text().toInt()

        val pages = mutableListOf<Page>()
        for (i in 1..totalPages) {
            pages.add(Page(i, "$url/$i.html"))
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String {
        // Eval the JS code, which either returns an array of image URLs, or the mkey
        val packedJS = document.getElementsByTag("script").map {
            it.data().trim()
        }.find {
            it.startsWith(PACKED_JS_BEGIN_MARKER) && it.endsWith(PACKED_JS_END_MARKER)
        }

        val unpackedJS = unpackJS(packedJS)

        var mkey = ""

        // Case where JS returns something like:
        //
        //   var guidkey=''+'8'+'f'+'4'+'1'+'d'+'1'+'f'+'1'+'a'+'d'+'9'+'3'+'4'+'7'+'e'+'e';
        //   $("#dm5_key").val(guidkey);
        if (unpackedJS?.contains("guidkey")!!) {
            mkey = Duktape.create().use {
                // Remove jQuery line that we don't need and just return the evaluated guidkey
                val script = unpackedJS.replace("\$(\"#dm5_key\").val(guidkey);", "") +
                        GUIDKEY_JS

                it.evaluate(script) as String
            }
        }

        // TODO: handle case where unpackedJS contains an array of img URLs directly

        val docHtml = document.body().html()

        var chapterid = ""
        var m = chapteridPattern.matcher(docHtml)
        while (m.find()) {
            chapterid = m.group(1)
        }

        var imagepage = ""
        m = imagepagePattern.matcher(docHtml)
        while (m.find()) {
            imagepage = m.group(1)
        }

        // TODO: below is still a WIP

        // Evaluate JS code returned from GET call, which returns an array of image URLs
        val res = getImage(document, chapterid, imagepage, mkey)

        Log.d("Mangafox", "res: $res")

        if (res.contains(PACKED_JS_BEGIN_MARKER)) {
            val stuff = unpackJS(res)

//            val urlString = result.split(", ")[1]

            Log.d("Mangafox", "stuff: $stuff")
        }

        return ""
    }

    private fun getImage(document: Document, chapterId: String, imagePage: String, mkey: String): String {
        val uri = "${document.location().replaceAfterLast("/", "")}chapterfun.ashx"

        val url = HttpUrl.parse(uri)!!.newBuilder()
        url.addQueryParameter("cid", chapterId)
        url.addQueryParameter("page", imagePage)
        url.addQueryParameter("key", mkey)

        Log.d("Mangafox", "url: $url")

        return client.newCall(GET(url.build().url().toString(), headers)).execute().body()!!.string()
    }

    private fun unpackJS(code: String?): String? {
        if (code == null) {
            return null
        }

        return Duktape.create().use {
            // We need to preserve backslashes and quotes when the string is parsed
            val escapedJS = code
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")

            val script = """var PACKED_JS = "$escapedJS";""" +
                    UNPACKER_JS_SRC +
                    UNPACKER_JS

            it.evaluate(script) as String
        }
    }

    private class Status(val id: String = "is_completed") : Filter.TriState("Completed")
    private class Genre(val id: String, name: String) : Filter.TriState(name)
    private class TextField(name: String, val key: String) : Filter.Text(name)
    private class Type : Filter.Select<String>("Type", arrayOf("Any", "Japanese Manga", "Korean Manhwa", "Chinese Manhua"))
    private class OrderBy : Filter.Sort("Order by",
            arrayOf("Series name", "Rating", "Views", "Total chapters", "Last chapter"),
            Filter.Sort.Selection(2, false))

    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    override fun getFilterList() = FilterList(
            TextField("Author", "author"),
            TextField("Artist", "artist"),
            Type(),
            Status(),
            OrderBy(),
            GenreList(getGenreList())
    )

    // [...document.querySelectorAll('.tagbt a')].sort((a, b) => a.innerText.localeCompare(b.innerText)).map(n => `Genre("${n.dataset.val}", "${n.innerText}")`).join(',\n')
    // on http://fanfox.net/search
    private fun getGenreList() = listOf(
            Genre("1", "Action"),
            Genre("33", "Adult"),
            Genre("2", "Adventure"),
            Genre("3", "Comedy"),
            Genre("25", "Doujinshi"),
            Genre("4", "Drama"),
            Genre("19", "Ecchi"),
            Genre("5", "Fantasy"),
            Genre("32", "Gender Bender"),
            Genre("10", "Harem"),
            Genre("30", "Historical"),
            Genre("8", "Horror"),
            Genre("29", "Josei"),
            Genre("6", "Martial Arts"),
            Genre("22", "Mature"),
            Genre("35", "Mecha"),
            Genre("15", "Mystery"),
            Genre("26", "One Shot"),
            Genre("11", "Psychological"),
            Genre("12", "Romance"),
            Genre("13", "School Life"),
            Genre("16", "Sci-fi"),
            Genre("17", "Seinen"),
            Genre("14", "Shoujo"),
            Genre("23", "Shoujo Ai"),
            Genre("7", "Shounen"),
            Genre("31", "Shounen Ai"),
            Genre("21", "Slice of Life"),
            Genre("27", "Smut"),
            Genre("20", "Sports"),
            Genre("9", "Supernatural"),
            Genre("18", "Tragedy"),
            Genre("24", "Webtoons"),
            Genre("28", "Yaoi"),
            Genre("34", "Yuri")
    )

    companion object {
        val chapteridPattern by lazy {
            Pattern.compile("var chapterid =(\\d+);")!!
        }

        val imagepagePattern by lazy {
            Pattern.compile("var imagepage=(\\d+);")!!
        }

        const val PACKED_JS_BEGIN_MARKER = "eval(function(p,a,c,k,e,d){"
        const val PACKED_JS_END_MARKER = "}))"

        const val UNPACKER_JS_SRC = """
            //////////////////////////////////////////
            //  Un pack the code from the /packer/  //
            //  By matthew@matthewfl.com            //
            //  http://matthewfl.com/unPacker.html  //
            //////////////////////////////////////////
            // version 1.2


            function unPack (code) {
                function indent (code) {
                    try {
                    var tabs = 0, old=-1, add='';
                    for(var i=0;i<code.length;i++) {
                        if(code[i].indexOf("{") != -1) tabs++;
                        if(code[i].indexOf("}") != -1) tabs--;

                        if(old != tabs) {
                            old = tabs;
                            add = "";
                            while (old > 0) {
                                add += "\t";
                                old--;
                            }
                            old = tabs;
                        }

                        code[i] = add + code[i];
                    }
                    } finally {
                        tabs = null;
                        old = null;
                        add = null;
                    }
                    return code;
                }

                var env = {
                    eval: function (c) {
                        code = c;
                    },
                    window: {},
                    document: {}
                };

                eval("with(env) {" + code + "}");

                code = (code+"").replace(/;/g, ";\n").replace(/{/g, "\n{\n").replace(/}/g, "\n}\n").replace(/\n;\n/g, ";\n").replace(/\n\n/g, "\n");

                code = code.split("\n");
                code = indent(code);

                code = code.join("\n");
                return code;
            }
        """

        const val UNPACKER_JS = """
            (function() {
                return unPack(PACKED_JS);
            })();
        """

        const val GUIDKEY_JS = """
            (function() {
                return guidkey;
            })();
        """
    }

}
