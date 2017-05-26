package eu.kanade.tachiyomi.extension.en.hentai2read

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar
import java.util.regex.Pattern

class Hentai2Read : ParsedHttpSource() {

    override val id: Long = 20

    override val name = "Hentai2Read"

    override val baseUrl = "http://hentai2read.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "div.img-container div.img-overlay a"

    override fun latestUpdatesSelector() = "ul.nav-users li.ribbon"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/hentai-list/all/any/most-popular/$page/", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest/$page/", headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.attr("href"))
        element.select("h2.mangaPopover").first().let {
            manga.title = it.attr("data-title").trim().split(" [").first().trim()
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a.mangaPopover").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("data-title").trim().split(" [").first().trim()
        }
        return manga
    }

    override fun popularMangaNextPageSelector() = "a#js-linkNext"

    override fun latestUpdatesNextPageSelector() = "a#js-linkNext"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val form = FormBody.Builder().apply {
            add("cmd_wpm_wgt_sch_sbm", "Search")
            add("txt_wpm_wgt_mng_sch_nme", "")
            add("cmd_wpm_pag_mng_sch_sbm", "")
            add("txt_wpm_pag_mng_sch_nme", query)
            var schFltIn = mutableListOf<String>()
            var schFltOut = mutableListOf<String>()

            for (filter in if (filters.isEmpty()) getFilterList() else filters) {
                when (filter) {
                    is MangaNameSelect -> add("cbo_wpm_pag_mng_sch_nme", filter.state.toString())
                    is ArtistName -> add("txt_wpm_pag_mng_sch_ats", filter.state)
                    is ArtistNameSelect -> add("cbo_wpm_pag_mng_sch_ats", filter.state.toString())
                    is ReleaseYear -> add("txt_wpm_pag_mng_sch_rls_yer", filter.state)
                    is ReleaseYearSelect -> add("cbo_wpm_pag_mng_sch_rls_yer", filter.state.toString())
                    is Status -> add("rad_wpm_pag_mng_sch_sts", filter.state.toString())
                    is TagList -> filter.state.forEach { tag -> 
                        when (tag.state) {
                            Filter.TriState.STATE_INCLUDE -> schFltIn.add(tag.id.toString())
                            Filter.TriState.STATE_EXCLUDE -> schFltOut.add(tag.id.toString())
                        }
                    }
                }
            }
            add("sch_flt_in", "[" + schFltIn.joinToString(",") + "]")
            add("sch_flt_out", "[" + schFltOut.joinToString(",") + "]")
        }
        return POST("$baseUrl/hentai-list/advanced-search/", headers, form.build())
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("ul.list-simple-mini").first()

        val manga = SManga.create()
        manga.author = infoElement.select("li:contains(Author) > a").first()?.text()
        manga.artist = infoElement.select("li:contains(Artist) > a").first()?.text()
        var tags = mutableListOf<String>()
        infoElement.select("li:contains(Category) > a, li:contains(Content) > a").forEach {
            tags.add(it.text())
        }
        manga.genre = tags.joinToString(", ")
        manga.description = infoElement.select("li:contains(Storyline) > p").first()?.text()
        manga.status = infoElement.select("li:contains(Status) > a").first()?.text().orEmpty().let {parseStatus(it)}
        manga.thumbnail_url = document.select("a#js-linkNext > img").first()?.attr("src")
        return manga
    }

    fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "ul.nav-chapters li a.link-effect"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.attr("href"))
        chapter.name = element.text().split(" by ").first().trim()
        chapter.date_upload = element.text()?.trim()?.split(" on ")?.last()?.let {
            parseChapterDate(it)
        } ?: 0L
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        val dateWords = date.split(" ")
        if (dateWords.size == 3) {
            val timeAgo = Integer.parseInt(dateWords[0])
            return Calendar.getInstance().apply {
                when (dateWords[1]) {
                    "minute", "minutes" -> add(Calendar.MINUTE, -timeAgo)
                    "hour", "hours" -> add(Calendar.HOUR, -timeAgo)
                    "day", "days" -> add(Calendar.DAY_OF_YEAR, -timeAgo)
                    "week", "weeks" -> add(Calendar.WEEK_OF_YEAR, -timeAgo)
                    "month", "months" -> add(Calendar.MONTH, -timeAgo)
                    "year", "years" -> add(Calendar.YEAR, -timeAgo)
                }
            }.timeInMillis
        }
        return 0L
    }

    override fun pageListRequest(chapter: SChapter) = POST(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val pages = mutableListOf<Page>()
        val imageBaseUrl = "https://static.hentaicdn.com/hentai"
        //language=RegExp
        val p = Pattern.compile("""'images' : \[\"(.*?)[,]?\"\]""")
        val m = p.matcher(response.body().string())
        var i = 0
        while (m.find()) {
            m.group(1).split(",").forEach {
                pages.add(Page(i++, "", imageBaseUrl + it.trim('"').replace("""\/""", "/")))
            }
        }
        return pages
    }
    override fun pageListParse(document: Document): List<Page> {
        throw Exception("Not used")
    }
    override fun imageUrlRequest(page: Page) = GET(page.url)
    override fun imageUrlParse(document: Document) = ""
    private class MangaNameSelect : Filter.Select<String>("Manga Name", arrayOf("Contains", "Starts With", "Ends With"))
    private class ArtistName : Filter.Text("Artist")
    private class ArtistNameSelect : Filter.Select<String>("Artist Name", arrayOf("Contains", "Starts With", "Ends With"))
    private class ReleaseYear : Filter.Text("Release Year")
    private class ReleaseYearSelect : Filter.Select<String>("Release Year", arrayOf("In", "Before", "After"))
    private class Status : Filter.Select<String>("Status", arrayOf("Any", "Completed", "Ongoing"))
    private class Tag(name: String, val id: Int) : Filter.TriState(name)
    private class TagList(tags: List<Tag>) : Filter.Group<Tag>("Tags", tags)
    override fun getFilterList() = FilterList(
            MangaNameSelect(),
            Filter.Separator(),
            ArtistName(),
            ArtistNameSelect(),
            Filter.Separator(),
            ReleaseYear(),
            ReleaseYearSelect(),
            Filter.Separator(),
            Status(),
            Filter.Separator(),
            TagList(getTagList())
    )

    // on https://hentai2read.com/hentai-search/"
    private fun getTagList() = listOf(
            Tag("Abortion", 529),
            Tag("Absent Parents", 1423),
            Tag("Abusive", 1587),
            Tag("Abusive Lover", 878),
            Tag("Adapted to H-Anime", 416),
            Tag("Addiction", 1438),
            Tag("Adopted Sister", 1634),
            Tag("Adoption", 522),
            Tag("Adoptive Siblings", 1451),
            Tag("Adult", 34),
            Tag("Adultery", 661),
            Tag("Affair", 1459),
            Tag("Aggressive Lover", 1599),
            Tag("Ahegao", 1702),
            Tag("Airheads", 1807),
            Tag("All-Girls School", 347),
            Tag("Alternative Ending", 666),
            Tag("Anal", 7),
            Tag("Anal Play", 1365),
            Tag("Analingus (Rimjob)", 2298),
            Tag("Angels", 1093),
            Tag("Animal Girls", 1904),
            Tag("Animal Transformation", 710),
            Tag("Anthology", 589),
            Tag("Anthropomorphism", 913),
            Tag("Apron", 975),
            Tag("Armpit Sex", 1843),
            Tag("Arranged Marriage", 846),
            Tag("Artificial Intelligence", 1719),
            Tag("Assjob", 2190),
            Tag("Aunt-Nephew Relationship", 327),
            Tag("Aunts", 1661),
            Tag("Authority Figures", 1821),
            Tag("Bad Grammar", 2188),
            Tag("Bastard!!", 1593),
            Tag("Bathroom Intercourse", 350),
            Tag("BBW", 1867),
            Tag("BDSM", 831),
            Tag("Beach", 403),
            Tag("Best Friends", 1227),
            Tag("Bestiality", 372),
            Tag("Betrayal", 610),
            Tag("Big Ass", 1591),
            Tag("Big Breasts", 20),
            Tag("Bikini", 1514),
            Tag("Bishoujo", 645),
            Tag("Bittersweet Ending", 1439),
            Tag("Blackmail", 391),
            Tag("Blind Characters", 1875),
            Tag("Blindfold", 1177),
            Tag("Bloomers", 1444),
            Tag("Blow job", 952),
            Tag("Body Modification", 1760),
            Tag("Body Swap", 444),
            Tag("Body Writing", 2183),
            Tag("Bondage", 317),
            Tag("Borderline H", 395),
            Tag("Brainwash", 1321),
            Tag("Breast Expansion", 2191),
            Tag("Brides", 1351),
            Tag("Brother and Sister", 1305),
            Tag("Brother Complex", 574),
            Tag("Brother-in-law", 1782),
            Tag("BSDM", 1263),
            Tag("Bukkake", 551),
            Tag("Bullying", 600),
            Tag("Bunny Girls", 1226),
            Tag("Cat Ears", 1633),
            Tag("Cat Girls", 957),
            Tag("Caught in the Act", 339),
            Tag("Censored", 1196),
            Tag("Cervix Penetration", 2184),
            Tag("CGs", 1512),
            Tag("Character Who Bullies the One They Love", 549),
            Tag("Cheating", 351),
            Tag("Cheerleaders", 1339),
            Tag("Child Abuse", 1852),
            Tag("Child Born From Incest", 575),
            Tag("Child Prostitute", 1663),
            Tag("Childhood Friends", 309),
            Tag("Childhood Love", 310),
            Tag("Chubby", 1819),
            Tag("Club President", 1586),
            Tag("Clumsy Character", 1808),
            Tag("Co-workers", 1739),
            Tag("Collection of Inter-Linked Stories", 415),
            Tag("Collection of Short Stories/Oneshots", 352),
            Tag("Comedy", 43),
            Tag("Compilation", 46),
            Tag("Confession", 834),
            Tag("Corruption", 755),
            Tag("Cosplay", 379),
            Tag("Cousins", 1340),
            Tag("Cow Girls", 1371),
            Tag("Creampie", 1037),
            Tag("Crossdressing", 343),
            Tag("Cunnilingus", 1754),
            Tag("Dark Skin", 1277),
            Tag("Debt-Motivated Prostitution", 1364),
            Tag("Debts", 1154),
            Tag("Deception", 516),
            Tag("Deep Throat", 1436),
            Tag("Defloration", 1246),
            Tag("Delinquents", 680),
            Tag("Demon Girls", 1453),
            Tag("Demon Hunters", 1666),
            Tag("Demons", 1152),
            Tag("Doctor-Patient Relationship", 900),
            Tag("Dog Girls", 353),
            Tag("Double Penetration", 427),
            Tag("Doujinshi", 42),
            Tag("Drugs", 446),
            Tag("Drunk", 864),
            Tag("Drunk Intercourse", 438),
            Tag("Ecchi", 40),
            Tag("Elf-Elves", 1370),
            Tag("Enema Play", 1740),
            Tag("Enemies Become Lovers", 803),
            Tag("Ero-Guro", 302),
            Tag("Exhibitionism", 404),
            Tag("Facesitting", 1831),
            Tag("Fairy-Fairies", 1368),
            Tag("Family Love", 1616),
            Tag("Family Secrets", 1623),
            Tag("Father and Daughter", 1021),
            Tag("Father-in-Law", 1922),
            Tag("Female Dominance", 320),
            Tag("Fetish", 1030),
            Tag("Fight Between Lovers", 1745),
            Tag("Fingering", 1389),
            Tag("First Love", 670),
            Tag("Fisting", 428),
            Tag("Foot job", 1058),
            Tag("Forced into a Relationship", 1746),
            Tag("Forced Marriage", 1617),
            Tag("Forced Sex", 1320),
            Tag("Foursome", 330),
            Tag("Fox Girls", 1268),
            Tag("Friends Become Lovers", 1769),
            Tag("Full Color", 468),
            Tag("Futa on Male", 1850),
            Tag("Futanari", 14),
            Tag("Gang Rape", 429),
            Tag("Gangbang", 462),
            Tag("Ganguro", 696),
            Tag("Gender Bender", 26),
            Tag("Giantess", 2278),
            Tag("Girls Only", 1960),
            Tag("Glasses", 465),
            Tag("God-Human Relationship", 720),
            Tag("Goddess", 1309),
            Tag("Group Intercourse", 311),
            Tag("Gyaru", 2185),
            Tag("Hand Job", 534),
            Tag("Happy Sex", 491),
            Tag("Hardcore", 1397),
            Tag("Harem", 31),
            Tag("Harem-seeking Male Lead", 1487),
            Tag("Hot Springs", 1744),
            Tag("Housewife-Housewives", 555),
            Tag("Human Pet", 1853),
            Tag("Human Toilets", 1724),
            Tag("Human-Nonhuman Relationship", 459),
            Tag("Humiliation", 552),
            Tag("Hypnotism", 1732),
            Tag("Idols", 1211),
            Tag("Impregnation", 1358),
            Tag("Incest", 15),
            Tag("Incest as a Subplot", 753),
            Tag("Infidelity", 423),
            Tag("Inflation", 2186),
            Tag("Inverted Nipples", 431),
            Tag("Jealous Lover", 441),
            Tag("Jealousy", 331),
            Tag("Kidnapping", 408),
            Tag("Kimono", 1283),
            Tag("Kono Naka ni Hitori", 1726),
            Tag("Korean Comic", 2158),
            Tag("Kunoichi (Ninja Girls)", 1759),
            Tag("Lactation", 16),
            Tag("Large Dicks", 990),
            Tag("Leotard", 2187),
            Tag("Licensed", 50),
            Tag("Lingerie", 332),
            Tag("Little Sisters", 1580),
            Tag("Live-in Lover", 1771),
            Tag("Lolicon", 17),
            Tag("Love At First Sight", 1326),
            Tag("Love Rivals", 1490),
            Tag("Love Triangles", 1167),
            Tag("Magic", 539),
            Tag("Magical Girls", 565),
            Tag("Maids", 912),
            Tag("Male Dominance", 681),
            Tag("Mangaka", 659),
            Tag("Masochists", 662),
            Tag("Master-Pet Relationship", 880),
            Tag("Master-Servant Relationship", 460),
            Tag("Master-Slave Relationship", 389),
            Tag("Masturbation", 18),
            Tag("Mermaids", 1714),
            Tag("MILFs", 354),
            Tag("Mind Break", 303),
            Tag("Mind Control", 304),
            Tag("Molesters", 1114),
            Tag("Monster Girls", 1409),
            Tag("Monster Sex", 1243),
            Tag("Monsters", 1175),
            Tag("Mother and Daughter", 784),
            Tag("Mother and Son", 393),
            Tag("Mother Complex", 342),
            Tag("Mother-in-Law", 1716),
            Tag("Multiple Penetration", 1826),
            Tag("Neighbors", 1741),
            Tag("Netorare", 370),
            Tag("Netori", 2243),
            Tag("Newlyweds", 451),
            Tag("Nipple Intercourse", 1717),
            Tag("Nipple Play", 944),
            Tag("Non-Human Pregnancy", 461),
            Tag("Nuns", 1185),
            Tag("Nurses", 1046),
            Tag("Obsessive Love", 605),
            Tag("Office Ladies", 1738),
            Tag("Older Brother", 1366),
            Tag("Older Female Young Boy", 1648),
            Tag("Older Female Younger Male", 355),
            Tag("Older Male Younger Female", 1229),
            Tag("Older Sister", 1241),
            Tag("Onegai Teacher dj", 1918),
            Tag("Oneshot", 33),
            Tag("Outdoor Intercourse", 504),
            Tag("Paizuri", 360),
            Tag("Pantyhose", 1275),
            Tag("Partially Colored", 665),
            Tag("Pegging", 1736),
            Tag("Personality Change", 576),
            Tag("Perverted Boss", 387),
            Tag("Perverted Characters", 300),
            Tag("Perverted Teachers", 366),
            Tag("Perverts", 1526),
            Tag("Pets", 1328),
            Tag("Piercings", 1465),
            Tag("Plain Girls", 1657),
            Tag("Plastic Surgery", 688),
            Tag("Polygamy", 312),
            Tag("Poor Characters", 1317),
            Tag("Poor Grammar", 2282),
            Tag("Porn Industry", 1440),
            Tag("Porn Stars", 367),
            Tag("Porn with Plot", 530),
            Tag("Possessed", 1656),
            Tag("Possession", 677),
            Tag("Possessive Lover", 1602),
            Tag("Pregnancy", 305),
            Tag("Pretend Rape", 1543),
            Tag("Priestesses", 1662),
            Tag("Priests", 1140),
            Tag("Princes", 1160),
            Tag("Princesses", 1161),
            Tag("Prisoners", 618),
            Tag("Proactive Protagonist", 1608),
            Tag("Producers", 1528),
            Tag("Prostitution", 394),
            Tag("Public Intercourse", 507),
            Tag("Public Nudity", 406),
            Tag("Punishment", 1418),
            Tag("Punishment Sex", 787),
            Tag("Queens", 1434),
            Tag("Rabbit Girls", 470),
            Tag("Rape", 23),
            Tag("Reverse Harem", 567),
            Tag("Reverse Rape", 376),
            Tag("Rewrite", 2281),
            Tag("Rich Boy", 726),
            Tag("Rich Family", 727),
            Tag("Rich Girl", 368),
            Tag("Robotics", 30),
            Tag("Romance", 41),
            Tag("Rushed Ending/Axed", 505),
            Tag("Sadist", 663),
            Tag("Sadomasochism", 499),
            Tag("Scat", 432),
            Tag("School Girls", 995),
            Tag("School Intercourse", 313),
            Tag("School Life", 48),
            Tag("School Nurse-Student Relationship", 478),
            Tag("Secret Crush", 1491),
            Tag("Secret Relationship", 399),
            Tag("Seduction", 1556),
            Tag("Senpai-Kouhai Relationship", 443),
            Tag("Serialized", 32),
            Tag("Sex Addicts", 301),
            Tag("Sex Friends", 629),
            Tag("Sex Friends Become Lovers", 400),
            Tag("Sex Industry", 450),
            Tag("Sex Slaves", 1273),
            Tag("Sex Toys", 837),
            Tag("Sexual Abuse", 695),
            Tag("Sexual Assault", 476),
            Tag("Sexual Frustration", 1611),
            Tag("Shotacon", 44),
            Tag("Shy Characters", 652),
            Tag("Sibling Love", 655),
            Tag("Sister and Brother", 1403),
            Tag("Sister Complex", 440),
            Tag("Sisters", 1330),
            Tag("Sketchy Art Style", 811),
            Tag("Sleep Intercourse", 583),
            Tag("Sluts", 1377),
            Tag("Small Breasts", 434),
            Tag("Son Complex", 909),
            Tag("Spirits", 1387),
            Tag("Star-Crossed Lover/s", 1346),
            Tag("Step-Daughter", 531),
            Tag("Step-Father", 532),
            Tag("Step-Father/Step-Daughter Relationship", 533),
            Tag("Step-Mother", 770),
            Tag("Step-Mother/Step-Son Relationship", 334),
            Tag("Step-Sibling Love", 371),
            Tag("Step-Siblings", 521),
            Tag("Step-Son", 833),
            Tag("Stockings", 1327),
            Tag("Student Council", 641),
            Tag("Student-Tutor Relationship", 315),
            Tag("Succubus", 378),
            Tag("Sudden Appearance", 609),
            Tag("Sudden Confession", 425),
            Tag("Swimsuit/s", 1179),
            Tag("Tanned", 1479),
            Tag("Teacher-Student Relationship", 1126),
            Tag("Teacher-Teacher Relationship", 369),
            Tag("Teachers", 1388),
            Tag("Tentacles", 24),
            Tag("Threesome (MFF)", 1686),
            Tag("Threesome (MMF)", 1688),
            Tag("Threesome (Other)", 335),
            Tag("Tomboy", 650),
            Tag("Torture", 2189),
            Tag("Tragedy", 49),
            Tag("Transgender", 518),
            Tag("Trap", 344),
            Tag("Tribadism", 553),
            Tag("Tsundere", 653),
            Tag("Tutors", 591),
            Tag("Twincest", 578),
            Tag("Twins", 336),
            Tag("Un-censored", 47),
            Tag("Uncle and Niece", 1923),
            Tag("Unlucky Character/s", 414),
            Tag("Unrequited Love", 1284),
            Tag("Urethral Intercourse", 1157),
            Tag("Urethral Play", 817),
            Tag("Urination", 435),
            Tag("Vampires", 1136),
            Tag("Virgins", 1137),
            Tag("Virtual Reality", 588),
            Tag("Vore", 1841),
            Tag("Voyeurism", 361),
            Tag("Waitresses", 1334),
            Tag("Werewolf", 877),
            Tag("Widow", 544),
            Tag("Wife Corruption", 613),
            Tag("Wife Depravity", 545),
            Tag("Wife-Wives", 1385),
            Tag("Witches", 1485),
            Tag("Wolf Girls", 1412),
            Tag("X-Ray", 1071),
            Tag("Yandere", 873),
            Tag("Yaoi", 27),
            Tag("Youkai", 1029),
            Tag("Young Master", 500),
            Tag("Yuri", 28)
    )
}