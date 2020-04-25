package eu.kanade.tachiyomi.extension.es.vcpvmp

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

open class VCPVMP(override val name: String, override val baseUrl: String) : ParsedHttpSource() {

    override val lang = "es"

    override val supportsLatest: Boolean = false

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesSelector() = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("Not used")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/page/$page", headers)

    override fun popularMangaSelector() = "div#posts div.gallery"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.select("a.cover").first().let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.select("div.caption").text()
            thumbnail_url = getCover(it.select("img").attr("data-src"))
        }
    }

    private fun getCover(imgURL: String): String {
        return if (imgURL == "") "" else imgURL.substringBefore("?")
    }

    override fun popularMangaNextPageSelector() = "ul.pagination > li.active + li"

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        document.select("div#catag").let {
            genre = document.select("div#tagsin > a[rel=tag]").joinToString(", ") {
                it.text()
            }
            artist = ""
            description = ""
            status = SManga.UNKNOWN
        }
    }

    override fun chapterListSelector() = "div#posts"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.select("h1").text()
        setUrlWithoutDomain(element.baseUri())
    }

    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url)

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        document.select("div#posts img[data-src]").forEach {
            add(Page(size, document.baseUri(), it.attr("data-src")))
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = HttpUrl.parse(baseUrl)!!.newBuilder()
        val isOnVCP = (baseUrl == "https://vercomicsporno.com")

        url.addPathSegments("page")
        url.addPathSegments(page.toString())
        url.addQueryParameter("s", query)

        filters.forEach { filter ->
            when (filter) {
                is Genre -> {
                    when (filter.toUriPart().isNotEmpty()) {
                        true -> {
                            url = HttpUrl.parse(baseUrl)!!.newBuilder()

                            url.addPathSegments(if (isOnVCP) "tags" else "genero")
                            url.addPathSegments(filter.toUriPart())

                            url.addPathSegments("page")
                            url.addPathSegments(page.toString())
                        }
                    }
                }
                is Category -> {
                    when (filter.toUriPart().isNotEmpty()) {
                        true -> {
                            url.addQueryParameter("cat", filter.toUriPart())
                        }
                    }
                }
            }
        }

        return GET(url.build().toString(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun getFilterList() = FilterList(
        Genre(),
        Filter.Separator(),
        Category()
    )

    // Array.from(document.querySelectorAll('div.tagcloud a.tag-cloud-link')).map(a => `Pair("${a.innerText}", "${a.href.replace('https://vercomicsporno.com/etiqueta/', '')}")`).join(',\n')
    // from https://vercomicsporno.com/
    private class Genre : UriPartFilter("Filtrar por categoría", arrayOf(
        Pair("Ver todos", ""),
        Pair("Anales", "anales"),
        Pair("Comics Porno", "comics-porno"),
        Pair("Culonas", "culonas"),
        Pair("Doujins", "doujins"),
        Pair("Furry", "furry"),
        Pair("Incesto", "incesto"),
        Pair("Lesbianas", "lesbianas"),
        Pair("Madre Hijo", "madre-hijo"),
        Pair("Mamadas", "mamadas"),
        Pair("Manga Hentai", "manga-hentai"),
        Pair("Milfs", "milfs"),
        Pair("Milftoon", "milftoon-comics"),
        Pair("Orgias", "orgias"),
        Pair("Parodias Porno", "parodias-porno"),
        Pair("Rubias", "rubias"),
        Pair("Series De Tv", "series-de-tv"),
        Pair("Tetonas", "tetonas"),
        Pair("Trios", "trios"),
        Pair("Videojuegos", "videojuegos"),
        Pair("Yuri", "yuri-2")
    ))

    // Array.from(document.querySelectorAll('form select#cat option.level-0')).map(a => `Pair("${a.innerText}", "${a.value}")`).join(',\n')
    // from https://vercomicsporno.com/
    private class Category : UriPartFilter("Filtrar por categoría", arrayOf(
        Pair("Ver todos", ""),
        Pair("5ish", "2853"),
        Pair("69", "1905"),
        Pair("8muses", "856"),
        Pair("Aarokira", "2668"),
        Pair("ABBB", "3058"),
        Pair("Absurd Stories", "2846"),
        Pair("Adam 00", "1698"),
        Pair("Aeolus", "2831"),
        Pair("Afrobull", "3032"),
        Pair("Alcor", "2837"),
        Pair("angstrom", "2996"),
        Pair("Anonymouse", "2851"),
        Pair("Aoino Broom", "3086"),
        Pair("Aquarina", "2727"),
        Pair("Arabatos", "1780"),
        Pair("Aroma Sensei", "2663"),
        Pair("Art of jaguar", "167"),
        Pair("Atreyu Studio", "3040"),
        Pair("Awaerr", "2921"),
        Pair("Bakuhaku", "2866"),
        Pair("Bashfulbeckon", "2841"),
        Pair("Bear123", "2814"),
        Pair("Black and White", "361"),
        Pair("Black House", "3044"),
        Pair("Blackadder", "83"),
        Pair("Blacky Chan", "2901"),
        Pair("Blargsnarf", "2728"),
        Pair("BlueVersusRed", "2963"),
        Pair("Bnouait", "2706"),
        Pair("Born to Die", "2982"),
        Pair("Buena trama", "2579"),
        Pair("Buru", "2736"),
        Pair("Cagri", "2751"),
        Pair("CallMePlisskin", "2960"),
        Pair("Catfightcentral", "2691"),
        Pair("cecyartbytenshi", "2799"),
        Pair("Cheka.art", "2999"),
        Pair("Cherry Mouse Street", "2891"),
        Pair("cherry-gig", "2679"),
        Pair("Chochi", "3085"),
        Pair("ClaraLaine", "2697"),
        Pair("Clasicos", "2553"),
        Pair("Cobatsart", "2729"),
        Pair("Comics porno", "6"),
        Pair("Comics Porno 3D", "1910"),
        Pair("Comics porno mexicano", "511"),
        Pair("Comics XXX", "119"),
        Pair("CrazyDad3d", "2657"),
        Pair("Creeeen", "2922"),
        Pair("Croc", "1684"),
        Pair("Crock", "3004"),
        Pair("Cyberunique", "2801"),
        Pair("Danaelus", "3080"),
        Pair("DankoDeadZone", "3055"),
        Pair("Darkhatboy", "2856"),
        Pair("DarkShadow", "2845"),
        Pair("DarkToons Cave", "2893"),
        Pair("Dasan", "2692"),
        Pair("David Willis", "2816"),
        Pair("Dboy", "3094"),
        Pair("Dconthedancefloor", "2905"),
        Pair("Degenerate", "2923"),
        Pair("Diathorn", "2894"),
        Pair("Dicasty", "2983"),
        Pair("Dimedrolly", "3017"),
        Pair("Dirtycomics", "2957"),
        Pair("DMAYaichi", "2924"),
        Pair("Dony", "2769"),
        Pair("Doxy", "2698"),
        Pair("Drawnsex", "9"),
        Pair("DrCockula", "2708"),
        Pair("Dude-doodle-do", "2984"),
        Pair("ebluberry", "2842"),
        Pair("Ecchi Kimochiii", "1948"),
        Pair("EcchiFactor 2.0", "1911"),
        Pair("Eirhjien", "2817"),
        Pair("Eliana Asato", "2878"),
        Pair("Ender Selya", "2774"),
        Pair("Enessef-UU", "3124"),
        Pair("ERN", "3010"),
        Pair("Erotibot", "2711"),
        Pair("Escoria", "2945"),
        Pair("Evil Rick", "2946"),
        Pair("FearingFun", "3057"),
        Pair("Felsala", "2138"),
        Pair("Fetishhand", "2932"),
        Pair("Fikomi", "2887"),
        Pair("Fixxxer", "2737"),
        Pair("FLBL", "3050"),
        Pair("Folo", "2762"),
        Pair("Forked Tail", "2830"),
        Pair("Fotonovelas XXX", "320"),
        Pair("Freckles", "3095"),
        Pair("Fred Perry", "2832"),
        Pair("Freehand", "400"),
        Pair("FrozenParody", "1766"),
        Pair("Fuckit", "2883"),
        Pair("Funsexydragonball", "2786"),
        Pair("Futanari", "1732"),
        Pair("Futanari Fan", "2787"),
        Pair("Garabatoz", "2877"),
        Pair("Gerph", "2889"),
        Pair("GFI", "3123"),
        Pair("Ghettoyouth", "2730"),
        Pair("Gilftoon", "2619"),
        Pair("Glassfish", "84"),
        Pair("GNAW", "3084"),
        Pair("Goat-Head", "3011"),
        Pair("Greivs", "3136"),
        Pair("Grigori", "2775"),
        Pair("Grose", "2876"),
        Pair("Gundam888", "2681"),
        Pair("Hagfish", "2599"),
        Pair("Hary Draws", "2752"),
        Pair("Hioshiru", "2673"),
        Pair("Hmage", "2822"),
        Pair("Horny-Oni", "2947"),
        Pair("Hoteggs102", "2925"),
        Pair("InCase", "1927"),
        Pair("Incest Candy", "3126"),
        Pair("Incesto 3d", "310"),
        Pair("Incognitymous", "2693"),
        Pair("Inker Shike", "2895"),
        Pair("Interracial", "364"),
        Pair("Inusen", "2854"),
        Pair("Inuyuru", "2699"),
        Pair("isakishi", "2721"),
        Pair("Jadenkaiba", "2064"),
        Pair("javisuzumiya", "2823"),
        Pair("Jay Marvel", "2135"),
        Pair("Jay Naylor", "174"),
        Pair("Jellcaps", "2818"),
        Pair("Jhon Person", "135"),
        Pair("Jitsch", "2835"),
        Pair("Jkr", "718"),
        Pair("JLullaby", "2680"),
        Pair("John North", "2927"),
        Pair("JohnJoseco", "2906"),
        Pair("JooJoo", "3026"),
        Pair("Joru", "2798"),
        Pair("JZerosk", "2757"),
        Pair("K/DA", "2667"),
        Pair("Ka-iN", "2874"),
        Pair("Kadath", "2700"),
        Pair("Kannel", "2836"),
        Pair("Kaos", "1994"),
        Pair("Karmagik", "2943"),
        Pair("Karmakaze", "2968"),
        Pair("Katoto Chan", "2916"),
        Pair("Kimmundo", "2669"),
        Pair("Kinkamashe", "2873"),
        Pair("Kinkymation", "2733"),
        Pair("Kirtu", "107"),
        Pair("Kiselrok", "3075"),
        Pair("Kogeikun", "2738"),
        Pair("KrasH", "2958"),
        Pair("Krazy Krow", "2848"),
        Pair("Kumi Pumi", "2771"),
        Pair("l", "1"),
        Pair("Lady Astaroth", "2722"),
        Pair("LaundryMom", "2926"),
        Pair("LawyBunne", "2744"),
        Pair("Laz", "2933"),
        Pair("Lemon Font", "2750"),
        Pair("Lewdua", "2734"),
        Pair("LilithN", "2991"),
        Pair("Locofuria", "2578"),
        Pair("Loonyjams", "2935"),
        Pair("Los Simpsons XXX", "94"),
        Pair("Lumo", "2858"),
        Pair("MAD-Project", "2890"),
        Pair("Magnificent Sexy Gals", "2942"),
        Pair("Manaworld", "85"),
        Pair("Manaworldcomics", "164"),
        Pair("Manga hentai", "152"),
        Pair("Maoukouichi", "2910"),
        Pair("Marcos Crot", "3025"),
        Pair("Matemi", "2741"),
        Pair("Mavruda", "2865"),
        Pair("MCC", "2843"),
        Pair("Meesh", "2740"),
        Pair("Meinfischer", "3063"),
        Pair("Melkor Mancin", "169"),
        Pair("Meowwithme", "2936"),
        Pair("Metal Owl", "2694"),
        Pair("Miles-DF", "2864"),
        Pair("Milffur", "140"),
        Pair("Milftoon", "13"),
        Pair("Milftoonbeach", "1712"),
        Pair("Milky Bunny", "3066"),
        Pair("MissBehaviour", "2997"),
        Pair("Mojarte", "1417"),
        Pair("Moose", "2939"),
        Pair("morganagod", "2917"),
        Pair("Moval-X", "2785"),
        Pair("Mr. E Comics", "2562"),
        Pair("Mr. Estella", "3068"),
        Pair("MrPotatoParty", "2712"),
        Pair("My Bad Bunny", "2989"),
        Pair("Myster Box", "2670"),
        Pair("Nastee34", "2930"),
        Pair("Neal D Anderson", "2725"),
        Pair("nearphotison", "3039"),
        Pair("nicekotatsu", "2749"),
        Pair("nihaotomita", "2998"),
        Pair("Nikipostat", "2824"),
        Pair("NiniiDawns", "2937"),
        Pair("Nisego", "2768"),
        Pair("Norasuko", "2800"),
        Pair("Noticias", "1664"),
        Pair("nsfyosu", "2859"),
        Pair("Nyoronyan", "2758"),
        Pair("NyuroraXBigdon", "3134"),
        Pair("O-tako Studios", "2723"),
        Pair("Oh!Nice", "2896"),
        Pair("OldFlameShotgun", "2884"),
        Pair("Otomo-San", "2788"),
        Pair("Pack Imagenes", "654"),
        Pair("Pak009", "2819"),
        Pair("Palcomix", "48"),
        Pair("Pandora Box", "155"),
        Pair("peculiart", "3000"),
        Pair("Pegasus Smith", "2682"),
        Pair("Personalami", "2789"),
        Pair("PeterAndWhitney", "2860"),
        Pair("Pia-Sama", "2797"),
        Pair("PinkPawg", "2861"),
        Pair("Pinktoon", "2868"),
        Pair("Pixelboy", "2840"),
        Pair("pleasure castle", "3081"),
        Pair("Pokeporn", "1914"),
        Pair("Polyle", "2952"),
        Pair("Poonet", "648"),
        Pair("Prism Girls", "1926"),
        Pair("Privados", "858"),
        Pair("PTDMCA", "2949"),
        Pair("QTsunade", "2770"),
        Pair("quad", "3051"),
        Pair("Quarko-Muon", "2872"),
        Pair("Queenchikki", "3062"),
        Pair("QueenComplex", "2951"),
        Pair("QueenTsunade", "2811"),
        Pair("Queervanire", "2871"),
        Pair("r_ex", "2898"),
        Pair("Raidon-san", "2962"),
        Pair("RanmaBooks", "1974"),
        Pair("Razter", "2689"),
        Pair("recreator 2099", "2671"),
        Pair("Redboard", "2803"),
        Pair("reddanmanic", "2867"),
        Pair("Reinbach", "2888"),
        Pair("Relatedguy", "2829"),
        Pair("ReloadHB", "3012"),
        Pair("Revolverwing", "2790"),
        Pair("RickFoxxx", "1411"),
        Pair("Rino99", "2934"),
        Pair("Ripperelite", "2820"),
        Pair("RobCiveCat", "2739"),
        Pair("RogueArtLove", "2812"),
        Pair("Rousfairly", "2776"),
        Pair("Rukasu", "2778"),
        Pair("Rupalulu", "3135"),
        Pair("SakuSaku Panic", "2907"),
        Pair("SaMelodii", "2701"),
        Pair("SanePerson", "2683"),
        Pair("SatyQ", "3024"),
        Pair("Saurian", "2950"),
        Pair("Selrock", "2886"),
        Pair("Shadako26", "2780"),
        Pair("Shadbase", "1713"),
        Pair("Shadow2007x", "2781"),
        Pair("ShadowFenrir", "3132"),
        Pair("Sheela", "2690"),
        Pair("Sillygirl", "2129"),
        Pair("Sin Porno", "2266"),
        Pair("Sinner", "2897"),
        Pair("Sinope", "2985"),
        Pair("Sirkowski", "2802"),
        Pair("Skulltitti", "2918"),
        Pair("SleepyGimp", "2911"),
        Pair("Slipshine", "2791"),
        Pair("Slypon", "2912"),
        Pair("Smutichi", "2821"),
        Pair("Snaketrap", "2940"),
        Pair("Sorje", "2961"),
        Pair("Spirale", "2870"),
        Pair("Stereoscope Comics", "3054"),
        Pair("Stormfeder", "2759"),
        Pair("Sun1Sol", "2782"),
        Pair("SunsetRiders7", "1705"),
        Pair("Super Melons", "2850"),
        Pair("Taboolicious", "88"),
        Pair("Tease Comix", "2948"),
        Pair("Tekuho", "2601"),
        Pair("Tentabat", "2862"),
        Pair("the dark mangaka", "2783"),
        Pair("The Pit", "2792"),
        Pair("thegoodbadart", "2684"),
        Pair("TheKite", "2825"),
        Pair("Theminus", "2828"),
        Pair("TheNewGuy", "3018"),
        Pair("TheOtherHalf", "2666"),
        Pair("Tim Fischer", "2763"),
        Pair("Totempole", "2746"),
        Pair("TotesFleisch8", "2764"),
        Pair("Tovio Rogers", "3056"),
        Pair("Tracy Scops", "2648"),
        Pair("Transmorpher DDS", "2672"),
        Pair("Turtlechan", "2796"),
        Pair("TvMx", "2793"),
        Pair("Urakan", "3043"),
        Pair("Uzonegro", "2695"),
        Pair("V3rnon", "2973"),
        Pair("Vaiderman", "3031"),
        Pair("VentZX", "2575"),
        Pair("Vercomicsporno", "1376"),
        Pair("Watsup", "2863"),
        Pair("Whargleblargle", "2844"),
        Pair("Wherewolf", "2685"),
        Pair("Witchking00", "1815"),
        Pair("Wulfsaga", "2931"),
        Pair("Xamrock", "2686"),
        Pair("Xierra099", "2702"),
        Pair("Xkit", "2703"),
        Pair("Y3df", "86"),
        Pair("Yamamoto", "3019"),
        Pair("Yusioka", "3082"),
        Pair("Zillionaire", "2807"),
        Pair("Zzomp", "252"),
        Pair("ZZZ Comics", "2839")
    ))
}
