package eu.kanade.tachiyomi.extension.all.fumo

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class fumo : HttpSource() {

    override val name = "fumo"
    override val supportsLatest = false

    override val lang = "all"
    override val baseUrl = "https://cdn.discordapp.com"

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val fumo1 = SManga.create()
        fumo1.title = ""
        fumo1.thumbnail_url = baseUrl + "/attachments/753756008572256338/842161251466739712/unknown.png"
        fumo1.setUrlWithoutDomain("/attachments/753756008572256338/842161251466739712/unknown.png")

        val fumo2 = SManga.create()
        fumo2.title = ""
        fumo2.thumbnail_url = baseUrl + "/attachments/753756008572256338/842161263765094450/unknown.png"
        fumo2.setUrlWithoutDomain("/attachments/753756008572256338/842161263765094450/unknown.png")

        val fumo3 = SManga.create()
        fumo3.title = ""
        fumo3.thumbnail_url = baseUrl + "/attachments/753756008572256338/842161278956077097/unknown.png"
        fumo3.setUrlWithoutDomain("/attachments/753756008572256338/842161278956077097/unknown.png")

        val fumo4 = SManga.create()
        fumo4.title = ""
        fumo4.thumbnail_url = baseUrl + "/attachments/753756008572256338/842161294995881984/unknown.png"
        fumo4.setUrlWithoutDomain("/attachments/753756008572256338/842161294995881984/unknown.png")

        return Observable.just(MangasPage(arrayListOf(fumo1, fumo2, fumo3, fumo4), false))
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = fetchPopularManga(1)
        .map { it.mangas.find({ it == manga })!!.apply { initialized = true } }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.just(emptyList())
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return Observable.just(emptyList())
    }

    // unused
    override fun chapterListParse(response: Response): List<SChapter> = throw Exception("Not used")

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = throw Exception("Not used")

    override fun imageUrlParse(response: Response): String = throw Exception("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage = throw Exception("Not used")

    override fun mangaDetailsParse(response: Response): SManga = throw Exception("Not used")

    override fun pageListParse(response: Response): List<Page> = throw Exception("Not used")

    override fun popularMangaParse(response: Response): MangasPage = throw Exception("Not used")

    override fun searchMangaParse(response: Response): MangasPage = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw Exception("Not used")

    override fun popularMangaRequest(page: Int): Request = throw Exception("Not used")
}
