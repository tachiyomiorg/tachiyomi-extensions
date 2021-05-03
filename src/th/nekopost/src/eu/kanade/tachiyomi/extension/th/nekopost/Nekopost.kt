package eu.kanade.tachiyomi.extension.th.nekopost

import com.google.gson.Gson
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class Nekopost : ParsedHttpSource() {
    override val baseUrl: String = "https://www.nekopost.net/manga/"

    private val mangaListUrl: String = "https://tuner.nekopost.net/ApiTest/getLatestChapterOffset/m/"
    private val projectDataUrl: String = "https://tuner.nekopost.net/ApiTest/getProjectDetailFull/"
    private val fileUrl: String = "https://fs.nekopost.net/"

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder {
        return super.headersBuilder().add("Referer", baseUrl)
    }

    override val lang: String = "th"
    override val name: String = "Nekopost"

    override val supportsLatest: Boolean = true

    private data class MangaListTracker(
        var offset: Int = 0,
        val list: HashSet<String> = HashSet()
    )

    private var latestMangaTracker = MangaListTracker()
    private var popularMangaTracker = MangaListTracker()

    data class ProjectRecord(
        val project: SManga,
        val project_id: String,
        val chapter_list: HashSet<String> = HashSet(),
    )

    data class ChapterRecord(
        val chapter: SChapter,
        val chapter_id: String,
        val project: ProjectRecord,
        val pages_data: String,
    )

    private var projectUrlMap = HashMap<String, ProjectRecord>()
    private var chapterList = HashMap<String, ChapterRecord>()

    private fun getStatus(status: String) = when (status) {
        "1" -> SManga.ONGOING
        "2" -> SManga.COMPLETED
        "3" -> SManga.LICENSED
        else -> SManga.UNKNOWN
    }

    private fun fetchMangas(page: Int, tracker: MangaListTracker): Observable<MangasPage> {
        if (page == 1) {
            tracker.list.clear()
            tracker.offset = 0
        }

        return client.newCall(latestUpdatesRequest(page + tracker.offset))
            .asObservableSuccess()
            .concatMap { response ->
                latestUpdatesParse(response).let {
                    if (it.mangas.isEmpty() && it.hasNextPage) {
                        tracker.offset++
                        fetchLatestUpdates(page)
                    } else {
                        Observable.just(it)
                    }
                }
            }
    }

    private fun mangasRequest(page: Int): Request = GET("$mangaListUrl${page - 1}")

    private fun mangasParse(response: Response, tracker: MangaListTracker): MangasPage {
        val mangaData = Gson().fromJson(response.body!!.string(), RawMangaDataList::class.java)

        return if (mangaData.listItem != null) {
            val mangas: List<SManga> = mangaData.listItem.filter {
                !tracker.list.contains(it.np_project_id)
            }.map {
                tracker.list.add(it.np_project_id)
                SManga.create().apply {
                    url = it.np_project_id
                    title = it.np_name
                    thumbnail_url = "${fileUrl}collectManga/${it.np_project_id}/${it.np_project_id}_cover.jpg"
                    initialized = false

                    projectUrlMap[it.np_project_id] = ProjectRecord(
                        project = this,
                        project_id = it.np_project_id
                    )
                }
            }

            MangasPage(mangas, true)
        } else {
            MangasPage(emptyList(), true)
        }
    }

    override fun chapterListSelector(): String = throw NotImplementedError("Unused")

    override fun chapterFromElement(element: Element): SChapter = throw NotImplementedError("Unused")

    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl)

    override fun imageUrlParse(document: Document): String = throw NotImplementedError("Unused")

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> = fetchMangas(page, latestMangaTracker)

    override fun latestUpdatesParse(response: Response): MangasPage = mangasParse(response, latestMangaTracker)

    override fun latestUpdatesFromElement(element: Element): SManga = throw Exception("Unused")

    override fun latestUpdatesNextPageSelector(): String = throw Exception("Unused")

    override fun latestUpdatesRequest(page: Int): Request = mangasRequest(page)

    override fun latestUpdatesSelector(): String = throw Exception("Unused")

    override fun mangaDetailsParse(document: Document): SManga = throw NotImplementedError("Unused")

    override fun fetchMangaDetails(sManga: SManga): Observable<SManga> {
        val manga = projectUrlMap[sManga.url]!!

        return client.newCall(GET("$projectDataUrl${manga.project_id}"))
            .asObservableSuccess()
            .concatMap {
                val mangaData = Gson().fromJson(it.body!!.string(), RawMangaDetailedData::class.java)

                Observable.just(
                    manga.project.apply {
                        mangaData.projectInfo.also { projectData ->
                            artist = projectData.artist_name
                            author = projectData.author_name
                            description = projectData.np_info
                            status = getStatus(projectData.np_status)
                            initialized = true
                        }
                        genre = mangaData.projectCategoryUsed?.joinToString(", ") { cat -> cat.npc_name }
                            ?: ""
                    }
                )
            }
    }

    override fun fetchChapterList(sManga: SManga): Observable<List<SChapter>> {
        val manga = projectUrlMap[sManga.url]!!

        return if (manga.project.status != SManga.LICENSED) {
            client.newCall(GET("$projectDataUrl${manga.project_id}"))
                .asObservableSuccess()
                .map {
                    val mangaData = Gson().fromJson(it.body!!.string(), RawMangaDetailedData::class.java)

                    mangaData.projectChapterList.map { chapter ->
                        val chapterUrl = "$baseUrl${manga.project_id}/${chapter.nc_chapter_no}"

                        manga.chapter_list.add(chapterUrl)

                        val createdChapter = SChapter.create().apply {
                            url = chapterUrl
                            name = chapter.nc_chapter_name
                            date_upload = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale("th")).parse(chapter.nc_created_date)?.time
                                ?: 0L
                            chapter_number = chapter.nc_chapter_no.toFloat()
                            scanlator = chapter.cu_displayname
                        }

                        chapterList[chapterUrl] = ChapterRecord(
                            chapter = createdChapter,
                            project = manga,
                            chapter_id = chapter.nc_chapter_id,
                            pages_data = chapter.nc_data_file,
                        )

                        createdChapter
                    }
                }
        } else {
            Observable.error(Exception("Licensed - No chapter to show"))
        }
    }

    override fun fetchPageList(sChapter: SChapter): Observable<List<Page>> {
        val chapter = chapterList[sChapter.url]!!

        return client.newCall(GET("${fileUrl}collectManga/${chapter.project.project_id}/${chapter.chapter_id}/${chapter.pages_data}"))
            .asObservableSuccess()
            .map {
                val chapterData = Gson().fromJson(it.body!!.string(), RawChapterDetailedData::class.java)

                chapterData.pageItem.map { pageData ->
                    Page(
                        index = pageData.pageNo,
                        imageUrl = "${fileUrl}collectManga/${chapter.project.project_id}/${chapter.chapter_id}/${pageData.fileName}",
                    )
                }
            }
    }

    override fun pageListParse(document: Document): List<Page> = throw NotImplementedError("Unused")

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = fetchMangas(page, popularMangaTracker)

    override fun popularMangaParse(response: Response): MangasPage = mangasParse(response, popularMangaTracker)

    override fun popularMangaFromElement(element: Element): SManga = throw NotImplementedError("Unused")

    override fun popularMangaNextPageSelector(): String = throw Exception("Unused")

    override fun popularMangaRequest(page: Int): Request = mangasRequest(page)

    override fun popularMangaSelector(): String = throw Exception("Unused")

    override fun searchMangaFromElement(element: Element): SManga = throw Exception("Unused")

    override fun searchMangaNextPageSelector(): String = throw Exception("Unused")

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(GET("${fileUrl}dataJson/dataProjectName.json"))
            .asObservableSuccess()
            .map {
                val nameData = Gson().fromJson(it.body!!.string(), Array<MangaNameList>::class.java)

                val mangas: List<SManga> = nameData.filter { d -> Regex(query, setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)).find(d.np_name) != null }
                    .map { matchedManga ->
                        if (!projectUrlMap.containsKey(matchedManga.np_project_id)) {
                            SManga.create().apply {
                                url = matchedManga.np_project_id
                                title = matchedManga.np_name
                                thumbnail_url = "${fileUrl}collectManga/${matchedManga.np_project_id}/${matchedManga.np_project_id}_cover.jpg"
                                initialized = false

                                projectUrlMap[matchedManga.np_project_id] = ProjectRecord(
                                    project = this,
                                    project_id = matchedManga.np_project_id
                                )
                            }
                        } else {
                            projectUrlMap[matchedManga.np_project_id]!!.project
                        }
                    }

                MangasPage(mangas, true)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw Exception("Unused")

    override fun searchMangaParse(response: Response): MangasPage = throw Exception("Unused")

    override fun searchMangaSelector(): String = throw Exception("Unused")
}
