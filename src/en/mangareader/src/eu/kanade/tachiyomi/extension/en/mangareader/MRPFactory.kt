package eu.kanade.tachiyomi.extension.en.mangareader

import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.google.gson.Gson
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MRPFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
            Mangareader(),
            Mangapanda())
}

class Mangareader : MRP("Mangareader", "https://www.mangareader.net") {
    override fun popularMangaSelector() = "div > div > table"
    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("a").let {
                setUrlWithoutDomain(it.attr("href"))
                title = it.text()
            }
            thumbnail_url = element.select("div[data-src]").attr("abs:data-src")
        }
    }
    override fun popularMangaNextPageSelector() = "li:has(.pcur) + li a"
    override fun latestUpdatesSelector() = "div:has(i) + div div > a"
    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            title = element.text()
        }
    }
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            thumbnail_url = document.select("div > img[alt]").attr("abs:src")
            status = parseStatus(document.select("div > table tr td:contains(Status:) + td").text())
            author = document.select("div > table tr td:contains(Author:) + td").text()
            artist = document.select("div > table tr td:contains(Artist:) + td").text()
            genre = document.select("div > table tr td:contains(Genre:) + td").joinToString { it.text() }
            description = document.select("div > div + p").text()
        }
    }
    override fun chapterListSelector() = "tr:has(i)"
    private val gson by lazy { Gson() }
    override fun pageListParse(document: Document): List<Page> {
        val script = document.select("script:containsData(document[)").firstOrNull()?.data() ?: throw Exception("script not found")
        return gson.fromJson<JsonObject>(script.substringAfterLast("="))["im"].asJsonArray.mapIndexed { i, json ->
            Page(i, "", "https:" + json["u"].string)
        }
    }
}
class Mangapanda : MRP("Mangapanda", "https://www.mangapanda.com")
