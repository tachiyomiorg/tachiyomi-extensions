package eu.kanade.tachiyomi.extension.en.readattackontitanshingekinokyojinmanga

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.util.asJsoup
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import rx.Observable
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ReadAttackOnTitanShingekiNoKyojinManga : MangaCatalog("Read Attack on Titan Shingeki no Kyojin Manga", "https://ww7.readsnk.com", "en") {
    override val sourceList = listOf(
        Pair("Shingeki No Kyojin", "$baseUrl/manga/shingeki-no-kyojin/"),
        Pair("Colored", "$baseUrl/manga/shingeki-no-kyojin-colored/"),
        Pair("Before the Fall", "$baseUrl/manga/shingeki-no-kyojin-before-the-fall/"),
        Pair("Lost Girls", "$baseUrl/manga/shingeki-no-kyojin-lost-girls/"),
        Pair("No Regrets", "$baseUrl/manga/attack-on-titan-no-regrets/"),
        Pair("Junior High", "$baseUrl/manga/attack-on-titan-junior-high/"),
        Pair("Harsh Mistress", "$baseUrl/manga/attack-on-titan-harsh-mistress-of-the-city/"),
        Pair("Anthology", "$baseUrl/manga/attack-on-titan-anthology/"),
        Pair("Art Book", "$baseUrl/manga/attack-on-titan-exclusive-art-book/"),
        Pair("Spoof", "$baseUrl/manga/spoof-on-titan/"),
        Pair("Guidebook", "$baseUrl/manga/attack-on-titan-guidebook-inside-outside/"),
        Pair("No Regrets Colored", "$baseUrl/manga/attack-on-titan-no-regrets-colored/"),
    ).sortedBy { it.first }.distinctBy { it.second }

    override fun chapterListSelector(): String = "div.w-full > .bg-white > .flex"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val name1 = element.select(".flex > a.text-gray-900").text()
        val name2 = element.select(".flex > div.text-xs").text()
        if (name2 == ""){
            name = name1
        } else {
            name = "$name1 - $name2"
        }
        url = element.select(".ml-auto div.flex a").attr("abs:href")
        date_upload = System.currentTimeMillis()
    }
}
