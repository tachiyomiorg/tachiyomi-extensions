package eu.kanade.tachiyomi.extension.all.foolslide

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.net.URLEncoder

@Nsfw
class HentaiCafe : FoolSlide("Hentai Cafe", "https://hentai.cafe", "en", "/manga") {
    // We have custom latest updates logic so do not dedupe latest updates
    override val dedupeLatestUpdates = false

    // Does not support popular manga
    override fun fetchPopularManga(page: Int) = fetchLatestUpdates(page)

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        val urlElement = element.select(".entry-thumb").first()
        if (urlElement != null) {
            setUrlWithoutDomain(urlElement.attr("href"))
            thumbnail_url = urlElement.child(0).attr("src")
        } else {
            setUrlWithoutDomain(element.select(".entry-title a").attr("href"))
        }
        title = element.select(".entry-title").text().trim()
    }

    override fun latestUpdatesNextPageSelector() = ".x-pagination li:last-child a"

    override fun latestUpdatesRequest(page: Int) = pagedRequest("$baseUrl/", page)

    override fun latestUpdatesSelector() = "article:not(#post-0)"

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select(".entry-title").text()
        val contentElement = document.select(".entry-content").first()
        thumbnail_url = contentElement.child(0).child(0).attr("src")
        val genres = mutableListOf<String>()
        document.select(".content a[rel=tag]").forEach { element ->
            if (!element.attr("href").contains("artist"))
                genres.add(element.text())
            else {
                artist = element.text()
                author = element.text()
            }
        }
        status = SManga.COMPLETED
        genre = genres.joinToString(", ")
    }

    // Note that the reader URL cannot be deduced from the manga URL all the time which is why
    //   we still need to parse the manga info page
    // Example: https://hentai.cafe/aiya-youngest-daughters-circumstances/
    override fun chapterListParse(response: Response) = listOf(
        SChapter.create().apply {
            // Some URLs wrongly end with "<br />\n" and need to be removed
            // Example: https://hentai.cafe/hc.fyi/12106
            setUrlWithoutDomain(response.asJsoup().select("[title=Read]").attr("href").replace("<br />\\s*".toRegex(), ""))
            name = "Chapter"
            chapter_number = 1f
        }
    )

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url: String? = null
        var queryString: String? = null
        fun requireNoUrl() = require(url == null && queryString == null) {
            "You cannot combine filters or use text search with filters!"
        }

        filters.findInstance<ArtistFilter>()?.let { f ->
            if (f.state.isNotBlank()) {
                requireNoUrl()
                url = "/hc.fyi/artist/${f.state
                    .trim()
                    .toLowerCase()
                    .replace(ARTIST_INVALID_CHAR_REGEX, "-")}/"
            }
        }
        filters.findInstance<BookFilter>()?.let { f ->
            if (f.state) {
                requireNoUrl()
                url = "/hc.fyi/category/book/"
            }
        }
        filters.findInstance<TagFilter>()?.let { f ->
            if (f.state != 0) {
                requireNoUrl()
                url = "/hc.fyi/tag/${f.values[f.state].name}/"
            }
        }

        if (query.isNotBlank()) {
            requireNoUrl()
            url = "/"
            queryString = "s=" + URLEncoder.encode(query, "UTF-8")
        }

        return url?.let {
            pagedRequest("$baseUrl$url", page, queryString)
        } ?: latestUpdatesRequest(page)
    }

    private fun pagedRequest(url: String, page: Int, queryString: String? = null): Request {
        // The site redirects page 1 -> url-without-page so we do this redirect early for optimization
        val builtUrl = if (page == 1) url else "${url}page/$page/"
        return GET(if (queryString != null) "$builtUrl?$queryString" else builtUrl)
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservable().doOnNext { response ->
                if (!response.isSuccessful) {
                    response.close()
                    // Better error message for invalid artist
                    if (response.code() == 404 &&
                        !filters.findInstance<ArtistFilter>()?.state.isNullOrBlank()
                    )
                        error("Invalid artist!")
                    else throw Exception("HTTP error ${response.code()}")
                }
            }
            .map { response ->
                searchMangaParse(response)
            }
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Filters cannot be used while searching."),
        Filter.Header("Only one filter may be used at a time."),
        Filter.Separator(),
        ArtistFilter(),
        BookFilter(),
        TagFilter()
    )

    class ArtistFilter : Filter.Text("Artist (must be exact match)")
    class BookFilter : Filter.CheckBox("Show books only", false)
    class TagFilter : Filter.Select<Tag>(
        "Tag",
        arrayOf(
            Tag("", "<select>"),
            Tag("ahegao", "Ahegao"),
            Tag("anal", "Anal"),
            Tag("apron", "Apron"),
            Tag("ashioki", "Ashioki"),
            Tag("bakunyuu", "Bakunyuu"),
            Tag("bathroom-sex", "Bathroom sex"),
            Tag("beauty-mark", "Beauty mark"),
            Tag("big-ass", "Big ass"),
            Tag("big-breast", "Big breast"),
            Tag("big-dick", "Big dick"),
            Tag("biting", "Biting"),
            Tag("black-mail", "Blackmail"),
            Tag("blindfold", "Blindfold"),
            Tag("blowjob", "Blowjob"),
            Tag("body-swap", "Body swap"),
            Tag("bondage", "Bondage"),
            Tag("booty", "Booty"),
            Tag("bride", "Bride"),
            Tag("bukkake", "Bukkake"),
            Tag("bunny-girl", "Bunny girl"),
            Tag("busty", "Busty"),
            Tag("cat", "Cat"),
            Tag("cat-girl", "Cat girl"),
            Tag("catgirl", "Catgirl"),
            Tag("cheating", "Cheating"),
            Tag("cheerleader", "Cheerleader"),
            Tag("chikan", "Chikan"),
            Tag("christmas", "Christmas"),
            Tag("chubby", "Chubby"),
            Tag("color", "Color"),
            Tag("comedy", "Comedy"),
            Tag("condom", "Condom"),
            Tag("cosplay", "Cosplay"),
            Tag("creampie", "Creampie"),
            Tag("crossdressing", "Crossdressing"),
            Tag("crotch-tattoo", "Crotch tattoo"),
            Tag("cunnilingus", "Cunnilingus"),
            Tag("dark-skin", "Dark skin"),
            Tag("deepthroat", "Deepthroat"),
            Tag("defloration", "Defloration"),
            Tag("devil", "Devil"),
            Tag("double-penetration", "Double penetration"),
            Tag("doujin", "Doujin"),
            Tag("doujinshi", "Doujinshi"),
            Tag("drama", "Drama"),
            Tag("drug", "Drug"),
            Tag("drunk", "Drunk"),
            Tag("elf", "Elf"),
            Tag("exhibitionism", "Exhibitionism"),
            Tag("eyebrows", "Eyebrows"),
            Tag("eyepatch", "Eyepatch"),
            Tag("facesitting", "Facesitting"),
            Tag("fangs", "Fangs"),
            Tag("fantasy", "Fantasy"),
            Tag("fellatio", "Fellatio"),
            Tag("femboy", "Femboy"),
            Tag("femdom", "Femdom"),
            Tag("filming", "Filming"),
            Tag("flat-chest", "Flat chest"),
            Tag("footjob", "Footjob"),
            Tag("freckles", "Freckles"),
            Tag("full-color", "Full color"),
            Tag("furry", "Furry"),
            Tag("futanari", "Futanari"),
            Tag("gangbang", "Gangbang"),
            Tag("gender-bender", "Gender bender"),
            Tag("genderbend", "Genderbend"),
            Tag("girls4m", "Girls4m"),
            Tag("glasses", "Glasses"),
            Tag("group", "Group"),
            Tag("gyaru", "Gyaru"),
            Tag("hairy", "Hairy"),
            Tag("hairy-armpit", "Hairy armpit"),
            Tag("handjob", "Handjob"),
            Tag("harem", "Harem"),
            Tag("headphones", "Headphones"),
            Tag("heart-pupils", "Heart pupils"),
            Tag("hentai", "Hentai"),
            Tag("historical", "Historical"),
            Tag("horns", "Horns"),
            Tag("horror", "Horror"),
            Tag("housewife", "Housewife"),
            Tag("huge-boobs", "Huge-boobs"),
            Tag("humiliation", "Humiliation"),
            Tag("idol", "Idol"),
            Tag("imouto", "Imouto"),
            Tag("impregnation", "Impregnation"),
            Tag("incest", "Incest"),
            Tag("inseki", "Inseki"),
            Tag("inverted-nipples", "Inverted nipples"),
            Tag("irrumatio", "Irrumatio"),
            Tag("isekai", "Isekai"),
            Tag("kemono-mimi", "Kemono mimi"),
            Tag("kimono", "Kimono"),
            Tag("kogal", "Kogal"),
            Tag("lactation", "Lactation"),
            Tag("large-breast", "Large breast"),
            Tag("lingerie", "Lingerie"),
            Tag("loli", "Loli"),
            Tag("love-hotel", "Love hotel"),
            Tag("magical-girl", "Magical girl"),
            Tag("maid", "Maid"),
            Tag("masturbation", "Masturbation"),
            Tag("miko", "Miko"),
            Tag("milf", "Milf"),
            Tag("mind-break", "Mind break"),
            Tag("mind-control", "Mind control"),
            Tag("monster-girl", "Monster girl"),
            Tag("muscles", "Muscles"),
            Tag("nakadashi", "Nakadashi"),
            Tag("naked-apron", "Naked apron"),
            Tag("netorare", "Netorare"),
            Tag("netorase", "Netorase"),
            Tag("netori", "Netori"),
            Tag("ninja", "Ninja"),
            Tag("nun", "Nun"),
            Tag("nurse", "Nurse"),
            Tag("office-lady", "Office lady"),
            Tag("ojousama", "Ojousama"),
            Tag("old-man", "Old man"),
            Tag("onani", "Onani"),
            Tag("oni", "Oni"),
            Tag("orgasm-denial", "Orgasm denial"),
            Tag("osananajimi", "Osananajimi"),
            Tag("pailoli", "Pailoli"),
            Tag("paizuri", "Paizuri"),
            Tag("pegging", "Pegging"),
            Tag("petite", "Petite"),
            Tag("pettanko", "Pettanko"),
            Tag("ponytail", "Ponytail"),
            Tag("pregnant", "Pregnant"),
            Tag("prositution", "Prositution"),
            Tag("pubic-hair", "Pubic Hair"),
            Tag("qipao", "Qipao"),
            Tag("rape", "Rape"),
            Tag("reverse-rape", "Reverse rape"),
            Tag("rimjob", "Rimjob"),
            Tag("schoolgirl", "Schoolgirl"),
            Tag("schoolgirl-outfit", "Schoolgirl outfit"),
            Tag("sci-fi", "Sci-fi"),
            Tag("senpai", "Senpai"),
            Tag("sex", "Sex"),
            Tag("sex-toys", "Sex toys"),
            Tag("shimapan", "Shimapan"),
            Tag("shota", "Shota"),
            Tag("shouta", "Shouta"),
            Tag("sister", "Sister"),
            Tag("sleeping", "Sleeping"),
            Tag("small-breast", "Small breast"),
            Tag("socks", "Socks"),
            Tag("spats", "Spats"),
            Tag("spread", "Spread"),
            Tag("squirting", "Squirting"),
            Tag("stocking", "Stocking"),
            Tag("stockings", "Stockings"),
            Tag("succubus", "Succubus"),
            Tag("swimsuit", "Swimsuit"),
            Tag("swinging", "Swinging"),
            Tag("tall-girl", "Tall-girl"),
            Tag("tanlines", "Tanlines"),
            Tag("teacher", "Teacher"),
            Tag("tentacles", "Tentacles"),
            Tag("threesome", "Threesome"),
            Tag("time-stop", "Time stop"),
            Tag("tomboy", "Tomboy"),
            Tag("toys", "Toys"),
            Tag("trans", "Trans"),
            Tag("tsundere", "Tsundere"),
            Tag("twin", "Twin"),
            Tag("twintails", "Twintails"),
            Tag("ugly-bastard", "Ugly bastard"),
            Tag("uncensored", "Uncensored"),
            Tag("unlimited", "Unlimited"),
            Tag("urination", "Urination"),
            Tag("vanilla", "Vanilla"),
            Tag("virgin", "Virgin"),
            Tag("vomit", "Vomit"),
            Tag("voyeurism", "Voyeurism"),
            Tag("waitress", "Waitress"),
            Tag("x-ray", "X-Ray"),
            Tag("yandere", "Yandere"),
            Tag("yukata", "Yukata"),
            Tag("yuri", "Yuri")
        )
    )

    class Tag(val name: String, private val displayName: String) {
        override fun toString() = displayName
    }

    companion object {
        // Do not include dashes in this regex, this way we can deduplicate dashes
        private val ARTIST_INVALID_CHAR_REGEX = Regex("[^a-zA-Z0-9]+")
    }
}

private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T
