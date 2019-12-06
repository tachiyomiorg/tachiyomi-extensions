package eu.kanade.tachiyomi.extension.es.mangamx

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response

class MangaMxFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        MangaMx(),
        DoujinYang()
    )
}

class DoujinYang: MangaMx() {
    override val baseUrl = "https://doujin-yang.es"

    override val name = "Doujin-Yang"

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/reciente/doujin?p=$page", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesNextPageSelector() = "nav#paginacion a:contains(Última)"

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun chapterListRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.asJsoup().select("div#c_list a").map { element ->
            SChapter.create().apply {
                name = element.select("h3").text()
                setUrlWithoutDomain(element.attr("abs:href"))
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return POST(baseUrl + chapter.url,
            headersBuilder().add("Content-Type", "application/x-www-form-urlencoded").build(),
            RequestBody.create(null, "info")
        )
    }

    override fun pageListParse(response: Response): List<Page> {
        return response.body()!!.string().substringAfter(",[").substringBefore("]")
            .replace(Regex("""[\\"]"""), "").split(",").let { list ->
                val path = "http:" + list[0]
                list.drop(1).mapIndexed { i, img -> Page(i, "", path + img) }
            }
    }

    override fun getFilterList() = FilterList(
        Filter.Header("NOTA: ¡La búsqueda de títulos no funciona!"), // "Title search not working"
        Filter.Separator(),
        GenreFilter(),
        LetterFilter(),
        StatusFilter(),
        SortFilter()
    )

    class GenreFilter : UriPartFilter("Género", "genero", arrayOf(
        Pair("all","All"),
        Pair("1","Ahegao"),
        Pair("379","Alien"),
        Pair("2","Anal"),
        Pair("490","Android18"),
        Pair("717","Angel"),
        Pair("633","Asphyxiation"),
        Pair("237","Bandages"),
        Pair("77","Bbw"),
        Pair("143","Bdsm"),
        Pair("23","Blackmail"),
        Pair("113","Blindfold"),
        Pair("24","Blowjob"),
        Pair("166","Blowjobface"),
        Pair("25","Body Writing"),
        Pair("314","Bodymodification"),
        Pair("806","Bodystocking"),
        Pair("366","Bodysuit"),
        Pair("419","Bodyswap"),
        Pair("325","Bodywriting"),
        Pair("5","Bondage"),
        Pair("51","Bukkake"),
        Pair("410","Catgirl"),
        Pair("61","Chastitybelt"),
        Pair("78","Cheating"),
        Pair("293","Cheerleader"),
        Pair("62","Collar"),
        Pair("120","Compilation"),
        Pair("74","Condom"),
        Pair("63","Corruption"),
        Pair("191","Corset"),
        Pair("234","Cosplaying"),
        Pair("389","Cowgirl"),
        Pair("256","Crossdressing"),
        Pair("179","Crotchtattoo"),
        Pair("689","Crown"),
        Pair("733","Cumflation"),
        Pair("385","Cumswap"),
        Pair("251","Cunnilingus"),
        Pair("75","Darkskin"),
        Pair("180","Daughter"),
        Pair("52","Deepthroat"),
        Pair("28","Defloration"),
        Pair("198","Demon"),
        Pair("145","Demongirl"),
        Pair("64","Drugs"),
        Pair("95","Drunk"),
        Pair("462","Femalesonly"),
        Pair("82","Femdom"),
        Pair("139","Ffmthreesome"),
        Pair("823","Fftthreesome"),
        Pair("55","Full Color"),
        Pair("181","Fullbodytattoo"),
        Pair("203","Fullcensorship"),
        Pair("111","Fullcolor"),
        Pair("114","Gag"),
        Pair("3","Glasses"),
        Pair("515","Gloryhole"),
        Pair("116","Humanpet"),
        Pair("32","Humiliation"),
        Pair("147","Latex"),
        Pair("12","Maid"),
        Pair("4","Milf"),
        Pair("245","Military"),
        Pair("414","Milking"),
        Pair("34","Mind Control"),
        Pair("68","Mindbreak"),
        Pair("124","Mindcontrol"),
        Pair("645","Nun"),
        Pair("312","Nurse"),
        Pair("272","Robot"),
        Pair("7","Romance"),
        Pair("761","Sundress"),
        Pair("412","Tailplug"),
        Pair("253","Tutor"),
        Pair("259","Twins"),
        Pair("207","Twintails"),
        Pair("840","Valkyrie"),
        Pair("530","Vampire"),
        Pair("16","Yuri"),
        Pair("273","Zombie")
        ))
}



