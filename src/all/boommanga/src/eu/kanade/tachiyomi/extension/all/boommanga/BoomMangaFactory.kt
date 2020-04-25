package eu.kanade.tachiyomi.extension.all.boommanga

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class BoomMangaFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        BoomMangacom(),
        ManManga(),
        TwinsComics(),
        BoomMangazh(),
        ManMangazh(),
        TwinsComicszh()
    )
}

class BoomMangacom : BoomManga("BoomManga", "https://m.boommanga.com", "en")

class ManManga : BoomManga("ManManga", "https://m.manmanga.com", "en") {
    override fun nameselector(element: Element) = element.select("a").attr("alt")
    override fun authorget(document: Document) = document.select(".author").text().substringAfter("：").trim()
    override fun thumbnailget(document: Document) = document.select(".bg-box .bg").attr("style").substringAfter("'").substringBefore("'")
    override fun genreget(document: Document) = document.select(".tags span").map {
        it.text().trim()
    }.joinToString(", ")
    override fun statusget(document: Document) = when (document.select(".type").text().substringAfter("：").trim()) {
        "Ongoing" -> SManga.ONGOING
        "Completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}
class TwinsComics : BoomManga("TwinsComics", "https://m.twinscomics.com", "en") {
    override fun nameselector(element: Element) = element.select("a").attr("alt")
    override fun authorget(document: Document) = document.select(".author").text().substringAfter("：").trim()
    override fun thumbnailget(document: Document) = document.select(".bg-box .bg").attr("style").substringAfter("'").substringBefore("'")
    override fun genreget(document: Document) = document.select(".tags span").map {
        it.text().trim()
    }.joinToString(", ")
    override fun statusget(document: Document) = when (document.select(".type").text().substringAfter("：").trim()) {
        "Ongoing" -> SManga.ONGOING
        "Completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}

class BoomMangazh : BoomManga("BoomManga", "https://m.boommanga.com/cn", "zh")

class ManMangazh : BoomManga("ManManga", "https://m.manmanga.com/cn", "zh") {
    override fun nameselector(element: Element) = element.select("a").attr("alt")
    override fun authorget(document: Document) = document.select(".author").text().substringAfter("：").trim()
    override fun thumbnailget(document: Document) = document.select(".bg-box .bg").attr("style").substringAfter("'").substringBefore("'")
    override fun genreget(document: Document) = document.select(".tags span").map {
        it.text().trim()
    }.joinToString(", ")
    override fun statusget(document: Document) = when (document.select(".type").text().substringAfter("：").trim()) {
        "连载中" -> SManga.ONGOING
        // "Completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}

class TwinsComicszh : BoomManga("TwinsComics", "https://m.twinscomics.com/cn", "zh") {
    override fun nameselector(element: Element) = element.select("a").attr("alt")
    override fun authorget(document: Document) = document.select(".author").text().substringAfter("：").trim()
    override fun thumbnailget(document: Document) = document.select(".bg-box .bg").attr("style").substringAfter("'").substringBefore("'")
    override fun genreget(document: Document) = document.select(".tags span").map {
        it.text().trim()
    }.joinToString(", ")
    override fun statusget(document: Document) = when (document.select(".type").text().substringAfter("：").trim()) {
        "连载中" -> SManga.ONGOING
        // "Completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}
