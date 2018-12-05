package eu.kanade.tachiyomi.extension.pt.mangalivre

import com.github.salomonbrys.kotson.nullString
import com.github.salomonbrys.kotson.obj
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.lang.Exception
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MangaLivre : HttpSource() {
  override val name = "MangaLivre"

  override val baseUrl = "https://mangalivre.com/"

  override val lang = "pt"

  override val supportsLatest = true

  // Sometimes the site is slow.
  override val client = network.client.newBuilder()
          .connectTimeout(1, TimeUnit.MINUTES)
          .readTimeout(1, TimeUnit.MINUTES)
          .writeTimeout(1, TimeUnit.MINUTES)
          .build()!!

  private val catalogHeaders = Headers.Builder().apply {
    add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.92 Safari/537.36")
    add("Host", "mangalivre.com")
    // The API doesn't return the result if this header isn't sent.
    add("X-Requested-With", "XMLHttpRequest")
  }.build()

  override fun popularMangaRequest(page: Int): Request {
    return GET("$baseUrl/home/most_read?page=$page", catalogHeaders)
  }

  override fun popularMangaParse(response: Response): MangasPage {
    val result = jsonParser.parse(response.body()!!.string()).obj

    // If "most_read" have boolean false value, then it doesn't have next page.
    if (!result["most_read"]!!.isJsonArray)
      return MangasPage(emptyList(), false)

    val popularMangas = result.getAsJsonArray("most_read")?.map {
      popularMangaItemParse(it.obj)
    }

    val hasNextPage = response.request().url().queryParameter("page")!!.toInt() < 10

    if (popularMangas != null)
      return MangasPage(popularMangas, hasNextPage)

    return MangasPage(emptyList(), false)
  }

  private fun popularMangaItemParse(obj: JsonObject) = SManga.create().apply {
    title = obj["serie_name"].nullString ?: ""
    thumbnail_url = obj["cover"].nullString
    url = obj["link"].nullString ?: ""
  }

  override fun latestUpdatesRequest(page: Int): Request {
    return GET("$baseUrl/home/releases?page=$page", catalogHeaders)
  }

  override fun latestUpdatesParse(response: Response): MangasPage {
    if (response.code() == 500)
      return MangasPage(emptyList(), false)

    val result = jsonParser.parse(response.body()!!.string()).obj

    val latestMangas = result.getAsJsonArray("releases")?.map {
      latestMangaItemParse(it.obj)
    }

    val hasNextPage = response.request().url().queryParameter("page")!!.toInt() < 5

    if (latestMangas != null)
      return MangasPage(latestMangas, hasNextPage)

    return MangasPage(emptyList(), false)
  }

  private fun latestMangaItemParse(obj: JsonObject) = SManga.create().apply {
    title = obj["name"].nullString ?: ""
    thumbnail_url = obj["image"].nullString
    url = obj["link"].nullString ?: ""
  }

  override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
    val form = FormBody.Builder().apply {
      add("search", query)
    }

    return POST("$baseUrl/lib/search/series.json", catalogHeaders, form.build())
  }

  override fun searchMangaParse(response: Response): MangasPage {
    val result = jsonParser.parse(response.body()!!.string()).obj

    // If "series" have boolean false value, then it doesn't have results.
    if (!result["series"]!!.isJsonArray)
      return MangasPage(emptyList(), false)

    val searchMangas = result.getAsJsonArray("series")?.map {
      searchMangaItemParse(it.obj)
    }

    if (searchMangas != null)
      return MangasPage(searchMangas, false)

    return MangasPage(emptyList(), false)
  }

  private fun searchMangaItemParse(obj: JsonObject) = SManga.create().apply {
    title = obj["name"].nullString ?: ""
    thumbnail_url = obj["cover"].nullString
    url = obj["link"].nullString ?: ""
    author = obj["author"].nullString
    artist = obj["artist"].nullString
  }

  override fun mangaDetailsParse(response: Response): SManga {
    val document = response.asJsoup()
    val isCompleted = document.select("div#series-data span.series-author i.complete-series").first() != null
    val cGenre = document.select("div#series-data ul.tags li").joinToString { it!!.text() }

    val seriesAuthor = if (isCompleted) {
      document.select("div#series-data span.series-author").first()!!.nextSibling().toString().substringBeforeLast("+")
    } else {
      document.select("div#series-data span.series-author").first()!!.text().substringBeforeLast("+")
    }

    val authors = seriesAuthor.split("&")
            .map { it.trim() }

    val cAuthor = authors.filter { !it.contains("(Arte)") }
            .map { author ->
              if (author.contains(", ")) {
                val authorSplit = author.split(", ")
                authorSplit[1] + " " + authorSplit[0]
              } else {
                author
              }
            }

    val cArtist = authors.filter { it.contains("(Arte)") }
            .map { it.replace("\\(Arte\\)".toRegex(), "").trim() }
            .map { author ->
              if (author.contains(", ")) {
                val authorSplit = author.split(", ")
                authorSplit[1] + " " + authorSplit[0]
              } else {
                author
              }
            }

    // Check if the manga was removed by the publisher.
    val cStatus = if (document.select("div.series-blocked-img").first() == null) {
      if (isCompleted) SManga.COMPLETED else SManga.ONGOING
    } else {
      SManga.LICENSED
    }

    return SManga.create().apply {
      genre = cGenre
      status = cStatus
      description = document.select("div#series-data span.series-desc").first()?.text()
      author = cAuthor.joinToString("; ")
      artist = if (cArtist.isEmpty()) cAuthor.joinToString("; ") else cArtist.joinToString("; ")
    }
  }

  // Need to override because the chapter API is paginated.
  // Adapted from:
  // https://stackoverflow.com/questions/35254323/rxjs-observable-pagination
  // https://stackoverflow.com/questions/40529232/angular-2-http-observables-and-recursive-requests
  override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
    return if (manga.status != SManga.LICENSED) {
      fetchChapterList(manga, 1)
    } else {
      Observable.error(Exception("Licensed - No chapters to show"))
    }
  }

  private fun fetchChapterList(manga: SManga, page: Int,
                               pastChapters: List<SChapter> = emptyList()): Observable<List<SChapter>> {
    val chapters = pastChapters.toMutableList()
    return fetchChapterListPage(manga, page)
            .flatMap {
              chapters += it
              if (it.isEmpty()) {
                Observable.just(chapters)
              } else {
                fetchChapterList(manga, page + 1, chapters)
              }
            }
  }

  private fun fetchChapterListPage(manga: SManga, page: Int): Observable<List<SChapter>> {
    return client.newCall(chapterListRequest(manga, page))
            .asObservableSuccess()
            .map { response ->
              chapterListParse(response)
            }
  }

  override fun chapterListRequest(manga: SManga): Request {
    return chapterListRequest(manga, 1)
  }

  private fun chapterListRequest(manga: SManga, page: Int): Request {
    val id = manga.url.substringAfterLast("/")
    return GET("$baseUrl/series/chapters_list.json?page=$page&id_serie=$id", catalogHeaders)
  }

  override fun chapterListParse(response: Response): List<SChapter> {
    val result = jsonParser.parse(response.body()!!.string()).obj

    if (!result["chapters"]!!.isJsonArray)
      return emptyList()

    return result.getAsJsonArray("chapters")?.map {
      chapterListItemParse(it.obj)
    } ?: emptyList()
  }

  private fun chapterListItemParse(obj: JsonObject): SChapter {
    val scan = obj["releases"]!!.asJsonObject!!.entrySet().first().value.obj
    val cName = obj["chapter_name"]!!.asString

    return SChapter.create().apply {
      name = if (cName == "") "Capítulo " + obj["number"]!!.asString else cName
      date_upload = parseChapterDate(obj["date"].nullString)
      scanlator = scan["scanlators"]!!.asJsonArray.get(0)!!.asJsonObject["name"].nullString
      url = scan["link"]!!.nullString ?: ""
      chapter_number = obj["number"]!!.asString.toFloatOrNull() ?: "1".toFloat()
    }
  }

  private fun parseChapterDate(date: String?) : Long {
    return try {
      SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).parse(date).time
    } catch (e: ParseException) {
      0L
    }
  }

  override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
    return client.newCall(pageListRequest(chapter))
            .asObservableSuccess()
            .flatMap { response ->
              val token = getReaderToken(response)
              return@flatMap if (token == "")
                Observable.error(Exception("Licensed - No chapter to show"))
              else fetchPageListApi(chapter, token)
            }
  }

  private fun fetchPageListApi(chapter: SChapter, token: String): Observable<List<Page>> {
    val id = "\\/(\\d+)\\/capitulo".toRegex().find(chapter.url)?.groupValues?.get(1) ?: ""
    return client.newCall(pageListApiRequest(id, token))
            .asObservableSuccess()
            .map { response ->
              pageListParse(response)
            }
  }

  private fun pageListApiRequest(id: String, token: String): Request {
    return GET("$baseUrl/leitor/pages/$id.json?key=$token", catalogHeaders)
  }

  override fun pageListParse(response: Response): List<Page> {
    val result = jsonParser.parse(response.body()!!.string()).obj

    return result["images"]!!.asJsonArray
            .mapIndexed { i, obj ->
              Page(i, obj.asString, obj.asString)
            }
  }

  override fun fetchImageUrl(page: Page): Observable<String> {
    return Observable.just(page.imageUrl!!)
  }

  override fun imageUrlParse(response: Response): String = ""

  private fun getReaderToken(response: Response): String {
    val document = response.asJsoup()
    // The pages API needs the token provided in the reader script.
    val scriptSrc = document.select("script[src*=\"reader.min.js\"]")?.first()?.attr("src") ?: ""
    return "token=(.*)&id".toRegex().find(scriptSrc)?.groupValues?.get(1) ?: ""
  }

  companion object {
    val jsonParser by lazy {
      JsonParser()
    }
  }
}