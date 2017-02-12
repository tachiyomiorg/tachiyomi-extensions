package eu.kanade.tachiyomi.extension.it.perveden

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

class Perveden : ParsedHttpSource() {

    override val name = "PervEden"

    override val baseUrl = "http://www.perveden.com"

    override val lang = "it"

    override val supportsLatest = true

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/it/it-directory/?order=3&page=$page", headers)

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/it/it-directory/?page=$page", headers)

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/it/it-directory/").newBuilder().addQueryParameter("title", query)
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is StatusList -> filter.state
                        .filter { it.state }
                        .map { it.id.toString() }
                        .forEach { url.addQueryParameter("status", it) }
                is Types -> filter.state
                        .filter { it.state }
                        .map { it.id.toString() }
                        .forEach { url.addQueryParameter("type", it) }
                is TextField -> url.addQueryParameter(filter.key, filter.state)
                is OrderBy -> filter.state?.let {
                    val sortId = it.index
                    url.addQueryParameter("order", if (it.ascending) "-$sortId" else "$sortId")
                }
                is GenreField -> filter.state.toLowerCase(Locale.ENGLISH).split(',', ';').forEach {
                    val id = genres[it.trim()]
                    if (id != null) url.addQueryParameter(filter.key, id)
                }
            }
        }
        url.addQueryParameter("page", page.toString())
        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = "table#mangaList > tbody > tr:has(td:gt(1))"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        element.select("td > a").first()?.let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.text()
        }
    }

    override fun searchMangaNextPageSelector() = "a:has(span.next)"

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val infos = document.select("div.rightbox")

        author = infos.select("a[href^=/it/it-directory/?author]").first()?.text()
        artist = infos.select("a[href^=/it/it-directory/?artist]").first()?.text()
        genre = infos.select("a[href^=/it/it-directory/?categoriesInc]").map { it.text() }.joinToString()
        description = document.select("h2#mangaDescription").text()
        status = parseStatus(infos.select("h4:containsOwn(Stato)").first()?.nextSibling().toString())
        val img = infos.select("div.mangaImage2 > img").first()?.attr("src")
        if (!img.isNullOrBlank()) thumbnail_url = img.let { "http:$it" }
    }

    private fun parseStatus(status: String) = when {
        status.contains("In Corso", true) -> SManga.ONGOING
        status.contains("Completato", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div#leftContent > table > tbody > tr"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val a = element.select("a[href^=/it/it-manga/]").first()

        setUrlWithoutDomain(a?.attr("href").orEmpty())
        name = a?.select("b")?.first()?.text().orEmpty()
        date_upload = element.select("td.chapterDate").first()?.text()?.let { parseChapterDate(it.trim()) } ?: 0L
    }

    private fun parseChapterDate(date: String): Long =
            if ("Oggi" in date) {
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            } else if ("Ieri" in date) {
                Calendar.getInstance().apply {
                    add(Calendar.DATE, -1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            } else try {
                SimpleDateFormat("d MMM yyyy", Locale.ITALIAN).parse(date).time
            } catch (e: ParseException) {
                0L
            }

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        document.select("option[value^=/it/it-manga/]").forEach {
            add(Page(size, "$baseUrl${it.attr("value")}"))
        }
    }

    override fun imageUrlParse(document: Document): String = document.select("a#nextA.next > img").first()?.attr("src").let { "http$it" }

    private class NamedId(name: String, val id: Int) : Filter.CheckBox(name)
    private class TextField(name: String, val key: String) : Filter.Text(name)
    private class GenreField(name: String, val key: String) : Filter.Text(name)
    private class OrderBy : Filter.Sort("Ordina per", arrayOf("Titolo manga", "Visite", "Capitoli", "Ultimo capitolo"),
            Filter.Sort.Selection(1, false))

    private class StatusList(statuses: List<NamedId>) : Filter.Group<NamedId>("Stato", statuses)
    private class Types(types: List<NamedId>) : Filter.Group<NamedId>("Tipo", types)

    override fun getFilterList() = FilterList(
            TextField("Autore", "author"),
            TextField("Artista", "artist"),
            GenreField("Generi inclusi", "categoriesInc"),
            GenreField("Generi esclusi", "categoriesExcl"),
            OrderBy(),
            Types(types()),
            StatusList(statuses())
    )

    private fun types() = listOf(
            NamedId("Japanese Manga", 0),
            NamedId("Korean Manhwa", 1),
            NamedId("Chinese Manhua", 2),
            NamedId("Comic", 3),
            NamedId("Doujinshi", 4)
    )

    private fun statuses() = listOf(
            NamedId("In corso", 1),
            NamedId("Completato", 2),
            NamedId("Sospeso", 0)
    )

    private val genres = mapOf(
            Pair("commedia", "4e70ea9ac092255ef70075d8"),
            Pair("ecchi", "4e70ea9ac092255ef70075d9"),
            Pair("age progression", "5782b043719a16947390104a"),
            Pair("ahegao", "577e6f90719a168e7d256a3f"),
            Pair("anal", "577e6f90719a168e7d256a3b"),
            Pair("angel", "577e724a719a168ef96a74d6"),
            Pair("apron", "577e720a719a166f4719a7be"),
            Pair("armpit licking", "577e71db719a166f4719a3e7"),
            Pair("assjob", "58474a08719a1668eeeea29b"),
            Pair("aunt", "577e6f8d719a168e7d256a20"),
            Pair("bbw", "5782ae42719a1675f68a6e29"),
            Pair("bdsm", "577e723d719a168ef96a7416"),
            Pair("bestiality", "57ad8919719a1629a0a327cf"),
            Pair("big areolae", "577e7226719a166f4719a9d0"),
            Pair("big ass", "577e6f8d719a168e7d256a21"),
            Pair("big balls", "577e7267719a168ef96a76ee"),
            Pair("big breasts", "577e6f8d719a168e7d256a1c"),
            Pair("big clit", "57ef0396719a163dffb8fdff"),
            Pair("big nipples", "5782ae42719a1675f68a6e2a"),
            Pair("big penis", "577e7267719a168ef96a76ef"),
            Pair("bike shorts", "577e7210719a166f4719a820"),
            Pair("bikini", "577e6f91719a168e7d256a77"),
            Pair("birth", "577e7273719a168ef96a77cf"),
            Pair("blackmail", "577e6f91719a168e7d256a78"),
            Pair("blindfold", "577e7208719a166f4719a78d"),
            Pair("blood", "577e7295719a168ef96a79e6"),
            Pair("bloomers", "5782b051719a1694739010ee"),
            Pair("blowjob", "577e6f8d719a168e7d256a22"),
            Pair("blowjob face", "577e71eb719a166f4719a544"),
            Pair("body modification", "577e6f93719a168e7d256a8e"),
            Pair("bodystocking", "5782b05c719a169473901151"),
            Pair("bodysuit", "577e6f90719a168e7d256a42"),
            Pair("bondage", "577e6f90719a168e7d256a45"),
            Pair("breast expansion", "577e71c3719a166f4719a235"),
            Pair("bukkake", "577e7210719a166f4719a821"),
            Pair("bunny girl", "577e7224719a166f4719a9b9"),
            Pair("business suit", "577e71e5719a166f4719a4b2"),
            Pair("catgirl", "577e71d5719a166f4719a366"),
            Pair("centaur", "577e7297719a168ef96a7a06"),
            Pair("cervix penetration", "577e7273719a168ef96a77d0"),
            Pair("cheating", "577e71b5719a166f4719a13b"),
            Pair("cheerleader", "57c0a6de719a1641240e9257"),
            Pair("chikan", "5782b0c6719a1679528762ac"),
            Pair("chinese dress", "5782b059719a169473901131"),
            Pair("chloroform", "577e6f92719a168e7d256a7f"),
            Pair("christmas", "5782af2b719a169473900752"),
            Pair("clit growth", "57ef0396719a163dffb8fe00"),
            Pair("collar", "577e6f93719a168e7d256a8f"),
            Pair("condom", "577e71d5719a166f4719a36c"),
            Pair("corruption", "577e6f90719a168e7d256a41"),
            Pair("cosplaying", "5782b185719a167952876944"),
            Pair("cousin", "577e7283719a168ef96a78c3"),
            Pair("cow", "5865d767719a162cce299571"),
            Pair("cunnilingus", "577e6f8d719a168e7d256a23"),
            Pair("dark skin", "577e6f90719a168e7d256a55"),
            Pair("daughter", "577e7250719a168ef96a7539"),
            Pair("deepthroat", "577e6f90719a168e7d256a3c"),
            Pair("defloration", "577e6f92719a168e7d256a82"),
            Pair("demon girl", "577e7218719a166f4719a8c8"),
            Pair("dick growth", "577e6f93719a168e7d256a90"),
            Pair("dickgirl on dickgirl", "5782af0e719a16947390067a"),
            Pair("dog girl", "577e7218719a166f4719a8c9"),
            Pair("double penetration", "577e6f90719a168e7d256a3d"),
            Pair("double vaginal", "577e7226719a166f4719a9d1"),
            Pair("drugs", "577e71da719a166f4719a3cb"),
            Pair("drunk", "577e7199719a16697b9853ea"),
            Pair("elf", "577e6f93719a168e7d256a91"),
            Pair("enema", "5782aff7719a169473900d8a"),
            Pair("exhibitionism", "577e72a7719a168ef96a7b26"),
            Pair("eyemask", "577e7208719a166f4719a78e"),
            Pair("facesitting", "577e7230719a166f4719aa8c"),
            Pair("females only", "577e6f90719a168e7d256a44"),
            Pair("femdom", "577e6f8c719a168e7d256a13"),
            Pair("filming", "577e7242719a168ef96a7465"),
            Pair("fingering", "577e6f90719a168e7d256a5d"),
            Pair("fisting", "57c349e1719a1625b42603f4"),
            Pair("foot licking", "5782b152719a16795287677d"),
            Pair("footjob", "577e6f8d719a168e7d256a17"),
            Pair("freckles", "5782ae42719a1675f68a6e2b"),
            Pair("fundoshi", "577e71d9719a166f4719a3bf"),
            Pair("furry", "5782ae45719a1675f68a6e49"),
            Pair("futanari", "577e6f92719a168e7d256a80"),
            Pair("gag", "577e6f90719a168e7d256a56"),
            Pair("gaping", "577e7210719a166f4719a822"),
            Pair("garter belt", "577e7201719a166f4719a704"),
            Pair("glasses", "577e6f90719a168e7d256a5e"),
            Pair("gothic lolita", "577e7201719a166f4719a705"),
            Pair("group", "577e726e719a168ef96a7764"),
            Pair("gyaru", "577e6f91719a168e7d256a79"),
            Pair("hairjob", "57bcea9f719a1687ea2bc092"),
            Pair("hairy", "577e7250719a168ef96a753a"),
            Pair("hairy armpits", "5782b13c719a16795287669c"),
            Pair("handjob", "577e71c8719a166f4719a29b"),
            Pair("harem", "577e71c3719a166f4719a239"),
            Pair("heterochromia", "577e7201719a166f4719a706"),
            Pair("hotpants", "585b302d719a1648da4f0389"),
            Pair("huge breasts", "577e71d9719a166f4719a3c0"),
            Pair("huge penis", "585b302d719a1648da4f038a"),
            Pair("human on furry", "577e7203719a166f4719a722"),
            Pair("human pet", "577e6f90719a168e7d256a57"),
            Pair("humiliation", "577e7210719a166f4719a823"),
            Pair("impregnation", "577e6f90719a168e7d256a47"),
            Pair("incest", "577e6f93719a168e7d256a92"),
            Pair("inflation", "577e7273719a168ef96a77d1"),
            Pair("insect girl", "577e71fc719a166f4719a692"),
            Pair("inverted nipples", "5813993a719a165f236ddacd"),
            Pair("kimono", "577e723d719a168ef96a7417"),
            Pair("kissing", "5782ae4f719a1675f68a6ece"),
            Pair("lactation", "577e6f93719a168e7d256a93"),
            Pair("latex", "577e6f90719a168e7d256a58"),
            Pair("layer cake", "577e7230719a166f4719aa8d"),
            Pair("leg lock", "57b7c0c2719a169265b768bd"),
            Pair("leotard", "579b141e719a16881d14ccfe"),
            Pair("lingerie", "577e71fc719a166f4719a693"),
            Pair("living clothes", "577e6f90719a168e7d256a49"),
            Pair("lizard girl", "5782b127719a1679528765e9"),
            Pair("lolicon", "5782af84719a1694739009b5"),
            Pair("long tongue", "5782b158719a1679528767d5"),
            Pair("machine", "57ef0396719a163dffb8fe01"),
            Pair("magical girl", "577e71c3719a166f4719a236"),
            Pair("maid", "5782ae3f719a1675f68a6e19"),
            Pair("male on dickgirl", "577e7267719a168ef96a76f0"),
            Pair("masked face", "57c349e1719a1625b42603f5"),
            Pair("masturbation", "577e71b5719a166f4719a13c"),
            Pair("mermaid", "578d3c5b719a164fa798c09e"),
            Pair("metal armor", "5782b158719a1679528767d6"),
            Pair("miko", "577e726e719a168ef96a7765"),
            Pair("milf", "577e6f8d719a168e7d256a24"),
            Pair("military", "577e6f8d719a168e7d256a18"),
            Pair("milking", "577e6f93719a168e7d256a94"),
            Pair("mind break", "577e6f90719a168e7d256a4b"),
            Pair("mind control", "577e6f90719a168e7d256a4d"),
            Pair("monster girl", "577e6f90719a168e7d256a4f"),
            Pair("monster girl", "577e6f90719a168e7d256a46"),
            Pair("moral degeneration", "577e71da719a166f4719a3cc"),
            Pair("mother", "577e71c7719a166f4719a293"),
            Pair("mouse girl", "5782ae45719a1675f68a6e4a"),
            Pair("multiple breasts", "5782ae45719a1675f68a6e4b"),
            Pair("multiple penises", "577e722a719a166f4719aa29"),
            Pair("muscle", "577e7250719a168ef96a753c"),
            Pair("nakadashi", "577e6f8e719a168e7d256a26"),
            Pair("netorare", "577e71c7719a166f4719a294"),
            Pair("niece", "5782b10a719a1679528764b5"),
            Pair("nurse", "577e6f8d719a168e7d256a1d"),
            Pair("oil", "5782af5e719a1694739008b1"),
            Pair("onahole", "582324e5719a1674f99b3444"),
            Pair("orgasm denial", "577e725d719a168ef96a762f"),
            Pair("paizuri", "577e6f90719a168e7d256a3e"),
            Pair("pantyhose", "577e6f8d719a168e7d256a19"),
            Pair("pantyjob", "577e7276719a168ef96a77f9"),
            Pair("parasite", "577e6f90719a168e7d256a50"),
            Pair("pasties", "5782b029719a169473900f3b"),
            Pair("piercing", "577e6f90719a168e7d256a59"),
            Pair("plant girl", "577e71f4719a166f4719a5fa"),
            Pair("policewoman", "57af673b719a1655a6ca8b58"),
            Pair("ponygirl", "577e6f90719a168e7d256a5a"),
            Pair("possession", "5782aff7719a169473900d8b"),
            Pair("pregnant", "577e71da719a166f4719a3cd"),
            Pair("prolapse", "5782cc79719a165f600844e0"),
            Pair("prostitution", "577e7242719a168ef96a7466"),
            Pair("pubic stubble", "577e71da719a166f4719a3ce"),
            Pair("public use", "5782cc79719a165f600844e1"),
            Pair("rape", "577e6f90719a168e7d256a51"),
            Pair("rimjob", "577e725f719a168ef96a765e"),
            Pair("robot", "5782b144719a1679528766f3"),
            Pair("ryona", "577e723e719a168ef96a7424"),
            Pair("saliva", "5884ed6f719a1678dfbb2258"),
            Pair("scar", "5782b081719a167952876168"),
            Pair("school swimsuit", "5782b05f719a169473901177"),
            Pair("schoolgirl uniform", "577e7199719a16697b9853e6"),
            Pair("selfcest", "5782b152719a16795287677e"),
            Pair("sex toys", "577e6f90719a168e7d256a5b"),
            Pair("sheep girl", "5782affa719a169473900da2"),
            Pair("shemale", "577e7267719a168ef96a76f1"),
            Pair("shibari", "577e72a6719a168ef96a7b18"),
            Pair("shimapan", "5782aebd719a1694739004c5"),
            Pair("sister", "577e6f8c719a168e7d256a14"),
            Pair("slave", "577e71b4719a166f4719a138"),
            Pair("sleeping", "577e71e5719a166f4719a4b3"),
            Pair("slime", "577e6f93719a168e7d256a95"),
            Pair("slime girl", "577e6f90719a168e7d256a48"),
            Pair("small breasts", "577e6f90719a168e7d256a5f"),
            Pair("smell", "577e7210719a166f4719a824"),
            Pair("snake girl", "577e721e719a166f4719a94b"),
            Pair("sole dickgirl", "582324e5719a1674f99b3445"),
            Pair("sole female", "577e6f91719a168e7d256a7a"),
            Pair("solo action", "5782afbf719a169473900ba2"),
            Pair("spanking", "577e7199719a16697b9853e7"),
            Pair("squirting", "577e7250719a168ef96a753d"),
            Pair("stockings", "577e6f8d719a168e7d256a1a"),
            Pair("stomach deformation", "5782aef2719a169473900606"),
            Pair("strap-on", "577e71d5719a166f4719a367"),
            Pair("stuck in wall", "5782aecf719a16947390055b"),
            Pair("sundress", "577e7216719a166f4719a8a2"),
            Pair("sweating", "577e71b5719a166f4719a13d"),
            Pair("swimsuit", "577e71d3719a166f4719a342"),
            Pair("swinging", "577e7203719a166f4719a723"),
            Pair("syringe", "577e71da719a166f4719a3cf"),
            Pair("tall girl", "577e71d9719a166f4719a3c1"),
            Pair("tanlines", "577e6f91719a168e7d256a7b"),
            Pair("teacher", "577e7199719a16697b9853e8"),
            Pair("tentacles", "577e6f90719a168e7d256a52"),
            Pair("thigh high boots", "577e6f93719a168e7d256a96"),
            Pair("tiara", "5782cc74719a165f600844d3"),
            Pair("tights", "5782b059719a169473901132"),
            Pair("tomboy", "577e7201719a166f4719a6fb"),
            Pair("torture", "577e725d719a168ef96a7630"),
            Pair("tracksuit", "5782b146719a167952876708"),
            Pair("transformation", "577e6f90719a168e7d256a4a"),
            Pair("tribadism", "577e6f90719a168e7d256a60"),
            Pair("tube", "577e7208719a166f4719a78f"),
            Pair("tutor", "5782af34719a1694739007a3"),
            Pair("twins", "577e726a719a168ef96a7729"),
            Pair("unusual pupils", "577e6f90719a168e7d256a53"),
            Pair("urethra insertion", "5877c07f719a163627a2ceb0"),
            Pair("urination", "577e7210719a166f4719a825"),
            Pair("vaginal sticker", "577e721c719a166f4719a930"),
            Pair("vomit", "5782ae45719a1675f68a6e4c"),
            Pair("vore", "577e6f8c719a168e7d256a15"),
            Pair("voyeurism", "583ca1ef719a161795a60847"),
            Pair("waitress", "5782ae3f719a1675f68a6e1a"),
            Pair("widow", "5782b13c719a16795287669d"),
            Pair("wings", "5782b158719a1679528767d7"),
            Pair("witch", "577e6f93719a168e7d256a97"),
            Pair("wolf girl", "577e724c719a168ef96a74fd"),
            Pair("wrestling", "577e7230719a166f4719aa8e"),
            Pair("x-ray", "577e6f90719a168e7d256a40"),
            Pair("yandere", "577e7295719a168ef96a79e7"),
            Pair("yuri", "577e6f90719a168e7d256a4c")
    )
}