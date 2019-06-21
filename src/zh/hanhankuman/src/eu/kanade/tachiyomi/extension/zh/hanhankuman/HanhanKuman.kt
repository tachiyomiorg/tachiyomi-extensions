package eu.kanade.tachiyomi.extension.zh.hanhankuman

import android.util.Base64
import com.squareup.duktape.Duktape
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class HanhanKuman : ParsedHttpSource() {

    override val name = "汗汗酷漫"
    override val baseUrl = "http://www.hhimm.com/"
    override val lang = "zh"
    override val supportsLatest = true
    val imageServer = arrayOf("https://res.333dm.com", "https://res02.333dm.com")

    override fun popularMangaSelector() = "li.list-comic"
    override fun searchMangaSelector() = popularMangaSelector()
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun chapterListSelector() = "ul#chapter-list-1 > li"

    override fun searchMangaNextPageSelector() = "li.next"
    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()
    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/list_$page/", headers)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/update/$page/", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/search/?keywords=$query")?.newBuilder()
        return GET(url.toString(), headers)
    }

    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl + manga.url, headers)
    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)
    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url, headers)

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)
    private fun mangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a.comic_img").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.select("img").attr("alt").trim()
            manga.thumbnail_url = if (it.select("img").attr("src").trim().indexOf("http") == -1)
                "https:${it.select("img").attr("src").trim()}"
            else it.select("img").attr("src").trim()
        }
        manga.author = element.select("span.comic_list_det > p").first()?.text()?.substring(3)
        return manga
    }

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a.image-link").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title").trim()
            manga.thumbnail_url = it.select("img").attr("src").trim()
        }
        manga.author = element.select("p.auth").text().trim()
        return manga
    }

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a")

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.attr("title").trim()
        return chapter
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.description = document.select("p.comic_deCon_d").text().trim()
        manga.thumbnail_url = document.select("div.comic_i_img > img").attr("src")
        return manga
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).asReversed()
    }

    // ref: https://jueyue.iteye.com/blog/1830792
    fun decryptAES(value: String, key: String, iv: String): String? {
        try {
            val secretKey = SecretKeySpec(key.toByteArray(), "AES")
            val iv = IvParameterSpec(iv.toByteArray())
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)

            val code = Base64.decode(value, Base64.NO_WRAP)

            return String(cipher.doFinal(code))

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    fun decrypt(code: String): String? {
        val key = "123456781234567G"
        val iv = "ABCDEF1G34123412"

        return decryptAES(code, key, iv)
    }

    override fun pageListParse(document: Document): List<Page> {
        val html = document.html()
        val re = Regex("""var chapterImages =\s*"(.*?)";""")
        val imgCodeStr = re.find(html)?.groups?.get(1)?.value
        val imgCode = decrypt(imgCodeStr!!)
        val imgPath = Regex("""var chapterPath =\s*"(.*?)";""").find(html)?.groups?.get(1)?.value
        val imgArrStr = Duktape.create().use {
            it.evaluate(imgCode!! + """.join('|')""") as String
        }
        return imgArrStr.split('|').mapIndexed { i, imgStr ->
            //Log.i("test", "img => ${imageServer[0]}/$imgPath$imgStr")
            Page(i, "", if (imgStr.indexOf("http") == -1) "${imageServer[0]}/$imgPath$imgStr" else imgStr)
        }
    }

    override fun imageUrlParse(document: Document) = ""

    private class GenreFilter(genres: Array<String>) : Filter.Select<String>("Genre", genres)

    override fun getFilterList() = FilterList(
        GenreFilter(getGenreList())
    )

    private fun getGenreList() = arrayOf(
        "All"
    )


}

