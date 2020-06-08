package eu.kanade.tachiyomi.extension.th.nekopost

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
import java.net.URL
import java.util.Calendar
import java.util.Locale
import kotlin.collections.set
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class Nekopost : ParsedHttpSource() {
    override val baseUrl: String = "https://www.nekopost.net/manga/"

    private val mangaListUrl: String = "https://www.nekopost.net/project/ajax_load_update/m/"
    private val baseFileUrl: String = "https://www.nekopost.net/file_server/"
    private val legacyChapterDataUrl: String = "https://www.nekopost.net/reader/loadChapterContent/"
    private val searchUrl: String = "https://www.nekopost.net/search/"

    override val lang: String = "th"
    override val name: String = "Nekopost"

    override val supportsLatest: Boolean = true

    private var latestMangaList: HashSet<String> = HashSet()
    private var popularMangaList: HashSet<String> = HashSet()

    private val projectList: HashMap<Int, ProjectParser.ProjectData> = HashMap()
    private val projectParser: ProjectParser = ProjectParser()

    object NP {
        class Chapter : SChapter {
            override lateinit var url: String

            override lateinit var name: String

            override var date_upload: Long = 0

            override var chapter_number: Float = -1f

            override var scanlator: String? = null

            lateinit var chapterData: ProjectParser.ProjectData.ChapterInfo
            lateinit var projectData: ProjectParser.ProjectData
        }

        class Manga : SManga {
            override lateinit var url: String

            override lateinit var title: String

            override var artist: String? = null

            override var author: String? = null

            override var description: String? = null

            override var genre: String? = null

            override var status: Int = 0

            override var thumbnail_url: String? = null

            override var initialized: Boolean = false

            lateinit var projectData: ProjectParser.ProjectData
        }
    }

    inner class ProjectParser {

        inner class ProjectData {
            inner class ProjectInfo {
                var np_project_id: Int = 0
                var np_name: String = ""
                var np_info: String? = null
                var np_view: Int = 0
                var np_no_chapter: Int = 0
                var np_created_date: Long? = null
                var np_updated_date: Long? = null
                var np_status: Int = 0
                var np_author: String? = null
                var np_artist: String? = null
            }

            inner class ChapterInfo {
                var nc_chapter_id: Int = 0
                var nc_chapter_no: Float = 0f
                var nc_chapter_name: String = ""
                var nc_provider: String? = null
                var nc_created_date: Long? = null
                var nc_owner_id: Int? = null
                var nc_data_file: String = ""
                var legacy_data_file: Boolean = false

                fun getChapterJsonFolder(): String = "${baseFileUrl}collectManga/${info.np_project_id}/$nc_chapter_id/"

                val sChapter: NP.Chapter
                    get() = NP.Chapter().apply {
                        if (nc_chapter_no - nc_chapter_no.toInt() == 0f) setUrlWithoutDomain("${info.np_project_id}/${nc_chapter_no.toInt()}")
                        else setUrlWithoutDomain("${info.np_project_id}/$nc_chapter_no")
                        name = nc_chapter_name
                        if (nc_created_date != null) date_upload = nc_created_date!!
                        chapter_number = nc_chapter_no
                        scanlator = nc_provider
                        chapterData = this@ChapterInfo
                        projectData = this@ProjectData
                    }.also { chapterListMap[nc_chapter_no] = this }
            }

            inner class ProjectCate {
                var npc_id: Int = 0
                var npc_name: String = ""
                var npc_name_link: String = ""
            }

            val sManga: NP.Manga
                get() = NP.Manga().apply {
                    setUrlWithoutDomain("${info.np_project_id}")
                    title = info.np_name
                    artist = info.np_artist
                    author = info.np_author
                    description = info.np_info
                    genre = projectCate.joinToString(", ") { it.npc_name }
                    status = info.np_status
                    thumbnail_url = getCoverUrl(this@ProjectData)
                    projectData = this@ProjectData
                }

            fun getChapterData(chapterNo: Float): ChapterInfo? =
                if (chapterListMap.contains(chapterNo)) chapterListMap[chapterNo]
                else chapterList.find { it.nc_chapter_no == chapterNo }

            var info: ProjectInfo = ProjectInfo()
            var chapterList: List<ChapterInfo> = emptyList()
            private val chapterListMap: HashMap<Float, ProjectParser.ProjectData.ChapterInfo> = HashMap()
            var projectCate: List<ProjectCate> = emptyList()
        }

        private fun getProjectJsonFolder(projectID: Int): String = projectID.toDouble().let {
            (it / 1000.0 - (it % 1000.0) / 1000.0).let { _tmp ->
                var tmp = _tmp

                if (projectID % 1000 != 0) tmp += 1
                tmp *= 1000

                tmp.toInt().toString().padStart(6, '0')
            }
        }

        private fun getProjectDataUrl(projectID: Int): String = "${baseFileUrl}collectJson/${getProjectJsonFolder(projectID)}/$projectID/${projectID}dtl.json"

        private fun getStatus(status: String) = when (status) {
            "1" -> SManga.ONGOING
            "2" -> SManga.COMPLETED
            "3" -> SManga.LICENSED
            else -> SManga.UNKNOWN
        }

        fun getCoverUrl(projectData: ProjectData): String = "${baseFileUrl}collectManga/${projectData.info.np_project_id}/${projectData.info.np_project_id}_cover.jpg"

        fun getProjectData(projectID: Int): ProjectData {
            return if (projectList.contains(projectID)) projectList[projectID]!!
            else JSONObject(URL(getProjectDataUrl(projectID)).readText())
                .let {
                    ProjectData().apply {
                        info = it.getJSONObject("info").let { pInfo ->
                            ProjectInfo().apply {
                                np_project_id = pInfo.getString("np_project_id").toInt()
                                np_name = pInfo.getString("np_name")
                                np_info = pInfo.getString("np_info")
                                np_view = pInfo.getString("np_view").toInt()
                                np_no_chapter = pInfo.getString("np_no_chapter").toInt()
                                np_created_date = NPUtils.convertDateStringToEpoch(pInfo.getString("np_created_date"), "yyyy-MM-dd hh:mm:ss")
                                np_updated_date = NPUtils.convertDateStringToEpoch(pInfo.getString("np_updated_date"), "yyyy-MM-dd hh:mm:ss")
                                np_status = getStatus(pInfo.getString("np_status"))
                                np_author = pInfo.getString("np_author")
                                np_artist = pInfo.getString("np_artist")
                            }
                        }

                        chapterList = it.getJSONArray("chapterList").let { chListData ->
                            val chList = ArrayList<ProjectData.ChapterInfo>()

                            for (chIndex in 0 until chListData.length()) {
                                val chInfo = chListData.getJSONObject(chIndex)

                                chList.add(ChapterInfo().apply {
                                    nc_chapter_id = chInfo.getString("nc_chapter_id").toInt()
                                    nc_chapter_no = chInfo.getString("nc_chapter_no").toFloat()
                                    nc_chapter_name = chInfo.getString("nc_chapter_name")
                                    nc_provider = chInfo.getString("nc_provider")
                                    nc_created_date = chInfo.getString("nc_created_date").let {
                                        it.split("-").toTypedArray().apply {
                                            this[1] = (NPUtils.monthList.indexOf(this[1].toUpperCase(Locale.ROOT)) + 1).toString().padStart(2, '0')
                                        }
                                    }.joinToString("-").let { NPUtils.convertDateStringToEpoch(it) }
                                    nc_owner_id = chInfo.getString("nc_owner_id").toInt()
                                    nc_data_file = chInfo.getString("nc_data_file").let {
                                        if (it.isNullOrBlank()) {
                                            legacy_data_file = true
                                            if (nc_chapter_no - nc_chapter_no.toInt() == 0f)
                                                nc_chapter_no.toInt().toString()
                                            else
                                                nc_chapter_no.toString()
                                        } else {
                                            it
                                        }
                                    }
                                })
                            }

                            chList
                        }

                        projectCate = it.getJSONArray("projectCate").let { cateListData ->
                            val cateList = ArrayList<ProjectData.ProjectCate>()

                            for (cateIndex in 0 until cateListData.length()) {
                                val cateInfo = cateListData.getJSONObject(cateIndex)

                                if (cateInfo.getString("project_id") != "null") {
                                    cateList.add(ProjectCate().apply {
                                        npc_id = cateInfo.getString("npc_id").toInt()
                                        npc_name = cateInfo.getString("npc_name")
                                        npc_name_link = cateInfo.getString("npc_name_link")
                                    })
                                }
                            }

                            cateList
                        }
                    }.also { projectList[projectID] = it }
                }
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return if (manga.status != SManga.LICENSED) {
            Observable.just(
                projectParser.getProjectData(manga.url.toInt()).chapterList.map { it.sChapter }
            )
        } else {
            Observable.error(Exception("Licensed - No chapters to show"))
        }
    }

    override fun chapterListSelector(): String = throw NotImplementedError("Unused")

    override fun chapterFromElement(element: Element): SChapter = throw NotImplementedError("Unused")

    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl)

    override fun imageUrlParse(document: Document): String = throw NotImplementedError("Unused")

    private var latestUpdatePageOffset: Int = 0

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        if (page == 1) {
            latestMangaList = HashSet()
            latestUpdatePageOffset = 0
        }

        return client.newCall(latestUpdatesRequest(page + latestUpdatePageOffset))
            .asObservableSuccess()
            .concatMap { response ->
                latestUpdatesParse(response).let {
                    if ((it.mangas as NPArrayList<SManga>).isListEmpty() && it.mangas.isNotEmpty()) {
                        latestUpdatePageOffset++
                        fetchLatestUpdates(page)
                    } else Observable.just(it)
                }
            }
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangaList = document.select(latestUpdatesSelector()).filter { element ->
            val dateText = element.select(".date").text().trim()
            val currentDate = Calendar.getInstance(Locale("th"))

            dateText.contains(currentDate.get(Calendar.DATE).toString()) && dateText.contains(NPUtils.monthList[currentDate.get(Calendar.MONTH)])
        }

        val mangas = NPArrayList(
            mangaList.map { element -> latestUpdatesFromElement(element) }.filter { manga ->
                if (!latestMangaList.contains(manga.url)) {
                    latestMangaList.add(manga.url)
                    true
                } else false
            },
            mangaList)

        val hasNextPage = mangaList.isNotEmpty()

        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val projectID = NPUtils.getMangaOrChapterAlias(element.select("a").attr("href")).toInt()
        return projectParser.getProjectData(projectID).sManga
    }

    override fun latestUpdatesNextPageSelector(): String? = throw Exception("Unused")

    override fun latestUpdatesRequest(page: Int): Request = GET("$mangaListUrl/${page - 1}")

    override fun latestUpdatesSelector(): String = "a[href]"

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(projectParser.getProjectData(manga.url.toInt()).sManga)

    override fun mangaDetailsParse(document: Document): SManga = throw NotImplementedError("Unused")

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val chData = chapter.url.split("/")
        val pj = projectParser.getProjectData(chData[0].toInt())
        val ch = pj.getChapterData(chData[1].toFloat())!!
        val pageList: ArrayList<Page> = ArrayList()

        if (ch.legacy_data_file) {
            JSONArray(URL("${legacyChapterDataUrl}${pj.info.np_project_id}/${ch.nc_data_file}").readText()).getJSONArray(3).let { pageItem ->
                for (pageIndex in 0 until pageItem.length()) {
                    pageList.add(pageItem.getJSONObject(pageIndex).let { pageData ->
                        Page(pageData.getString("page_no").toInt() - 1,
                            "",
                            "${ch.getChapterJsonFolder()}${pageData.getString("value_url")}")
                    })
                }
            }
        } else {
            JSONObject(URL("${ch.getChapterJsonFolder()}${ch.nc_data_file}").readText()).getJSONArray("pageItem").let { pageItem ->
                for (pageIndex in 0 until pageItem.length()) {
                    pageList.add(pageItem.getJSONObject(pageIndex).let { pageData ->
                        Page(pageData.getInt("pageNo") - 1,
                            "",
                            "${ch.getChapterJsonFolder()}${pageData.getString("fileName")}")
                    })
                }
            }
        }

        return Observable.just(pageList)
    }

    override fun pageListParse(document: Document): List<Page> = throw NotImplementedError("Unused")

    private var popularMangaPageOffset: Int = 0

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        if (page == 1) {
            popularMangaList = HashSet()
            popularMangaPageOffset = 0
        }

        return client.newCall(popularMangaRequest(page + popularMangaPageOffset))
            .asObservableSuccess()
            .concatMap { response ->
                popularMangaParse(response).let {
                    if ((it.mangas as NPArrayList<SManga>).isListEmpty() && it.mangas.isNotEmpty()) {
                        popularMangaPageOffset++
                        fetchPopularManga(page)
                    } else Observable.just(it)
                }
            }
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangaList = document.select(popularMangaSelector())

        val mangas = NPArrayList(
            mangaList.map { element -> popularMangaFromElement(element) }.filter { manga ->
                if (!popularMangaList.contains(manga.url)) {
                    popularMangaList.add(manga.url)
                    true
                } else false
            },
            mangaList)

        val hasNextPage = mangaList.isNotEmpty()

        return MangasPage(mangas, hasNextPage)
    }

    override fun popularMangaFromElement(element: Element): SManga = latestUpdatesFromElement(element)

    override fun popularMangaNextPageSelector(): String? = latestUpdatesNextPageSelector()

    override fun popularMangaRequest(page: Int): Request = latestUpdatesRequest(page)

    override fun popularMangaSelector(): String = latestUpdatesSelector()

    override fun getFilterList(): FilterList = FilterList(
        GenreFilter(),
        StatusFilter()
    )

    private class GenreFilter : Filter.Group<GenreCheckbox>("Genre", NPUtils.Genre.map { genre -> GenreCheckbox(genre.first) })

    private class GenreCheckbox(genre: String) : Filter.CheckBox(genre, false)

    private class StatusFilter : Filter.Group<StatusCheckbox>("Status", NPUtils.Status.map { status -> StatusCheckbox(status.first) })

    private class StatusCheckbox(status: String) : Filter.CheckBox(status, false)

    override fun searchMangaFromElement(element: Element): SManga = projectParser.getProjectData(NPUtils.getMangaOrChapterAlias(element.attr("href")).toInt()).sManga

    override fun searchMangaNextPageSelector(): String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (page > 1) throw Error("No more page")

        var queryString = query

        val genreList: Array<String> = try {
            (filters.find { filter -> filter is GenreFilter } as GenreFilter).state.filter { checkbox -> checkbox.state }.map { checkbox -> checkbox.name }.toTypedArray()
        } catch (e: Exception) {
            emptyArray<String>()
        }.let {
            when {
                it.isNotEmpty() -> it
                NPUtils.getValueOf(NPUtils.Genre, query) == null -> it
                else -> {
                    queryString = ""
                    arrayOf(query)
                }
            }
        }

        val statusList: Array<String> = try {
            (filters.find { filter -> filter is StatusFilter } as StatusFilter).state.filter { checkbox -> checkbox.state }.map { checkbox -> checkbox.name }.toTypedArray()
        } catch (e: Exception) {
            emptyArray()
        }

        return GET("$searchUrl?${NPUtils.getSearchQuery(queryString, genreList, statusList)}")
    }

    override fun searchMangaSelector(): String = ".list_project .item .project_info a"
}
