package eu.kanade.tachiyomi.extension.all.madara


import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import java.text.SimpleDateFormat
import java.util.*
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request

class MadaraFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Mangasushi(),
        NinjaScans(),
        ReadManhua(),
        ZeroScans(),
        IsekaiScanCom(),
        HappyTeaScans(),
        JustForFun(),
        AoCTranslations(),
        Kanjiku(),
        KomikGo(),
        LuxyScans(),
        TritiniaScans(),
        TsubakiNoScan(),
        YokaiJump(),
        ZManga(),
        MangazukiMe(),
        MangazukiOnline(),
        MangazukiClub()
    )
}

class Mangasushi : Madara("Mangasushi", "https://mangasushi.net", "en")
class NinjaScans : Madara("NinjaScans", "https://ninjascans.com", "en")
class ReadManhua : Madara("ReadManhua", "https://readmanhua.net", "en", dateFormat = SimpleDateFormat("dd MMM yy", Locale.US))
class ZeroScans : Madara("ZeroScans", "https://zeroscans.com", "en")
class IsekaiScanCom : Madara("IsekaiScan.com", "http://isekaiscan.com/", "en")
class HappyTeaScans : Madara("Happy Tea Scans", "https://happyteascans.com/", "en")
class JustForFun : Madara("Just For Fun", "https://just-for-fun.ru/", "ru", dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US))
class AoCTranslations : Madara("Agent of Change Translations", "https://aoc.moe/", "en")
class Kanjiku : Madara("Kanjiku", "https://kanjiku.net/", "de", dateFormat = SimpleDateFormat("dd. MMM yyyy", Locale.GERMAN))
class KomikGo : Madara("KomikGo", "https://komikgo.com/", "id")
class LuxyScans : Madara("Luxy Scans", "https://luxyscans.com/", "en")
class TritiniaScans : Madara("Tritinia Scans", "http://tritiniascans.ml/", "en") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/index-m_orderby=views.html", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/index-m_orderby=latest.html", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/index.html?s=$query", headers)
    override fun latestUpdatesNextPageSelector(): String? = null
    override fun popularMangaNextPageSelector(): String? = null

}
class TsubakiNoScan : Madara("Tsubaki No Scan", "https://tsubakinoscan.com/", "fr", dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US))
class YokaiJump : Madara("Yokai Jump", "https://yokaijump.fr/", "fr", dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US))
class ZManga : Madara("ZManga", "https://zmanga.org/", "es")
class MangazukiMe : Madara("MangazukiMe", "https://mangazuki.me/", "en")
class MangazukiOnline : Madara("MangazukiOnline", "https://www.mangazuki.online/", "en")
class MangazukiClub : Madara("MangazukiClub", "https://mangazuki.club/", "ko")


