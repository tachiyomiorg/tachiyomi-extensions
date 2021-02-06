package eu.kanade.tachiyomi.extension.all.zbulu

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document

class ZbuluFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        HolyManga(),
        HeavenManga(),
        KooManga(),
        BuluManga(),
    )
}

class HolyManga : Zbulu("HolyManga", "https://w15.holymanga.net")
class HeavenManga : Zbulu("HeavenManga", "http://heaventoon.com")
class KooManga : Zbulu("Koo Manga", "http://ww1.koomanga.com")
class BuluManga : Zbulu("Bulu Manga", "https://ww5.bulumanga.net") {
    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.chapter-content img").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:src").substringAfter("url="))
        }
    }
}
