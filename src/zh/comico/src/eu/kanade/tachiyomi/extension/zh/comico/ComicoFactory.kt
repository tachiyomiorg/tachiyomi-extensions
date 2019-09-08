package eu.kanade.tachiyomi.extension.zh.comico

import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document

class ComicoFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        ComicoOfficial(),
        ComicoChallenge()
    )
}

class ComicoOfficial : Comico("Comico Official (Limited free chapters)", "", false)
class ComicoChallenge : Comico("Comico Challenge", "/challenge", true) {
    override fun popularMangaRequest(page: Int): Request {
        val body = FormBody.Builder()
            .add("page", page.toString())
            .build()

        return POST("$baseUrl$urlModifier/updateList.nhn?order=new", headers, body)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = mutableListOf<SManga>()
        val body = response.body()!!.string()

        gson.fromJson<JsonObject>(body)["result"]["list"].asJsonArray.forEach{
            val manga = SManga.create()

            manga.thumbnail_url = it["img_url"].asString
            manga.title = it["article_title"].asString
            manga.author = it["author"].asString
            manga.description = it["description"].asString
            manga.url = it["article_url"].asString.substringAfter(urlModifier)
            manga.status = if (it["is_end"].asString == "false") SManga.ONGOING else SManga.COMPLETED

            mangas.add(manga)
        }

        val lastPage = gson.fromJson<JsonObject>(body)["result"]["totalPageCnt"].asString
        val currentPage = gson.fromJson<JsonObject>(body)["result"]["currentPageNo"].asString

        return MangasPage(mangas, currentPage < lastPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val body = FormBody.Builder()
            .add("page", page.toString())
            .build()

        return POST("$baseUrl$urlModifier/updateList.nhn?order=update", headers, body)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaSelector() = "div#challengeList ul.list-article02__list li.list-article02__item a"

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.article-hero03__inner")

        val manga = SManga.create()
        manga.title = infoElement.select("h1").text()
        manga.author = infoElement.select("p.article-hero03__author").text()
        manga.description = infoElement.select("div.article-hero03__description p").text()
        manga.thumbnail_url = infoElement.select("img").attr("src")

        return manga
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()

        gson.fromJson<JsonObject>(response.body()!!.string())["result"]["list"].asJsonArray
            .forEach{ chapters.add(chapterFromJson(it)) }

        return chapters.reversed()
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("img.comic-image__image").forEachIndexed{ i, img ->
            pages.add(Page(i, "", img.attr("src")))
        }

        return pages
    }
}



