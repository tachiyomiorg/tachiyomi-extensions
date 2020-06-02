package eu.kanade.tachiyomi.extension.all.foolslide

import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.PreferenceScreen
import android.widget.Toast
import com.github.salomonbrys.kotson.get
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.extension.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class FoolSlideFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        JaiminisBox(),
        SenseScans(),
        KireiCake(),
        SilentSky(),
        Mangatellers(),
        IskultripScans(),
        AnataNoMotokare(),
        DeathTollScans(),
        DokiFansubs(),
        YuriIsm(),
        AjiaNoScantrad(),
        OneTimeScans(),
        MangaScouts(),
        StormInHeaven(),
        Lilyreader(),
        Russification(),
        EvilFlowers(),
        LupiTeam(),
        HentaiCafe(),
        TheCatScans(),
        ZandynoFansub(),
        HelveticaScans(),
        KirishimaFansub(),
        PowerMangaIT(),
        BaixarHentai(),
        HNIScantrad(),
        HNIScantradEN(),
        PhoenixScans(),
        GTO(),
        Kangaryu(),
        FallenWorldOrder(),
        NIFTeam(),
        TuttoAnimeManga(),
        Customizable(),
        TortugaCeviri(),
        Rama(),
        Mabushimajo()
    )
}

class JaiminisBox : FoolSlide("Jaimini's Box", "https://jaiminisbox.com", "en", "/reader") {
    private val slugRegex = "(?:/read/)([\\w\\d-]+?)(?:/)".toRegex()
    override fun pageListRequest(chapter: SChapter): Request {
        val (slug) = slugRegex.find(chapter.url)!!.destructured
        var (major, minor) = chapter.chapter_number.toString().split(".")
        if (major == "-1") major = "0" // Some oneshots don't have a chapter
        return GET("$baseUrl$urlModifier/api/reader/chapter?comic_stub=$slug&chapter=$major&subchapter=$minor")
    }

    override fun pageListParse(document: Document): List<Page> {
        val pagesJson = JSONObject(document.body().ownText())
        val json = JsonParser().parse(pagesJson.getString("pages")).asJsonArray
        val pages = ArrayList<Page>()
        json.forEach {
            pages.add(Page(pages.size, "", JsonParser().parse(it.toString())["url"].asString))
        }
        return pages
    }
}

class TheCatScans : FoolSlide("The Cat Scans", "https://reader2.thecatscans.com/", "en")

class SenseScans : FoolSlide("Sense-Scans", "http://sensescans.com", "en", "/reader")

class KireiCake : FoolSlide("Kirei Cake", "https://reader.kireicake.com", "en") {
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            description = document.select("$mangaDetailsInfoSelector li:has(b:contains(description))")
                .first()?.ownText()?.substringAfter(":")
            thumbnail_url = getDetailsThumbnail(document)
        }
    }
}

class SilentSky : FoolSlide("Silent Sky", "https://reader.silentsky-scans.net", "en")

class Mangatellers : FoolSlide("Mangatellers", "http://www.mangatellers.gr", "en", "/reader/reader") {
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl$urlModifier/list/$page/", headers)
    }
}

class IskultripScans : FoolSlide("Iskultrip Scans", "https://maryfaye.net", "en", "/reader")

class AnataNoMotokare : FoolSlide("Anata no Motokare", "https://motokare.xyz", "en", "/reader")

class DeathTollScans : FoolSlide("Death Toll Scans", "https://reader.deathtollscans.net", "en")

class DokiFansubs : FoolSlide("Doki Fansubs", "https://kobato.hologfx.com", "en", "/reader")

class YuriIsm : FoolSlide("Yuri-ism", "https://www.yuri-ism.net", "en", "/slide")

class AjiaNoScantrad : FoolSlide("Ajia no Scantrad", "https://www.ajianoscantrad.fr", "fr", "/reader")

class OneTimeScans : FoolSlide("One Time Scans", "https://reader.otscans.com", "en")

class MangaScouts : FoolSlide("MangaScouts", "http://onlinereader.mangascouts.org", "de")

class StormInHeaven : FoolSlide("Storm in Heaven", "https://www.storm-in-heaven.net", "it", "/reader-sih")

class Lilyreader : FoolSlide("Lilyreader", "https://manga.smuglo.li", "en")

class Russification : FoolSlide("Русификация", "https://rusmanga.ru", "ru")

class EvilFlowers : FoolSlide("Evil Flowers", "https://reader.evilflowers.com", "en")

class LupiTeam : FoolSlide("LupiTeam", "https://lupiteam.net", "it", "/reader") {
    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select(mangaDetailsInfoSelector).first().text()

        val manga = SManga.create()
        manga.author = infoElement.substringAfter("Autore: ").substringBefore("Artista: ")
        manga.artist = infoElement.substringAfter("Artista: ").substringBefore("Target: ")
        val stato = infoElement.substringAfter("Stato: ").substringBefore("Trama: ").substring(0, 8)
        manga.status = when (stato) {
            "In corso" -> SManga.ONGOING
            "Completa" -> SManga.COMPLETED
            "Licenzia" -> SManga.LICENSED
            else -> SManga.UNKNOWN
        }
        manga.description = infoElement.substringAfter("Trama: ")
        manga.thumbnail_url = getDetailsThumbnail(document)

        return manga
    }
}

class ZandynoFansub : FoolSlide("Zandy no Fansub", "https://zandynofansub.aishiteru.org", "en", "/reader")

class HelveticaScans : FoolSlide("Helvetica Scans", "https://helveticascans.com", "en", "/r")

class KirishimaFansub : FoolSlide("Kirishima Fansub", "https://www.kirishimafansub.net", "es", "/lector")

class PowerMangaIT : FoolSlide("PowerManga", "https://reader.powermanga.org", "it", "")

class BaixarHentai : FoolSlide("Baixar Hentai", "https://leitura.baixarhentai.net", "pt-BR") {
    // Hardcode the id because the language wasn't specific.
    override val id: Long = 8908032188831949972

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.select("h1.title").text()
            thumbnail_url = getDetailsThumbnail(document, "div.title a")
        }
    }
}

class HNIScantrad : FoolSlide("HNI-Scantrad", "https://hni-scantrad.com", "fr", "/lel")

class HNIScantradEN : FoolSlide("HNI-Scantrad", "https://hni-scantrad.com", "en", "/eng/lel") {
    override val supportsLatest = false
    override fun popularMangaRequest(page: Int) = GET(baseUrl + urlModifier, headers)
    override fun popularMangaSelector() = "div.listed"
    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("a:has(h3)").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("abs:href"))
            }
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl$urlModifier/?manga=${query.replace(" ", "+")}")
    override fun searchMangaSelector(): String = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun chapterListSelector() = "div.theList > a"
    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            name = element.select("div.chapter b").text()
            setUrlWithoutDomain(element.attr("abs:href"))
        }
    }
    override fun pageListParse(response: Response): List<Page> {
        return Regex("""imageArray\[\d+]='(.*)'""").findAll(response.body()!!.string()).toList().mapIndexed { i, mr ->
            Page(i, "", "$baseUrl$urlModifier/${mr.groupValues[1]}")
        }
    }
}

class PhoenixScans : FoolSlide("The Phoenix Scans", "https://www.phantomreader.com", "it", "/reader")

class GTO : FoolSlide("GTO The Great Site", "https://www.gtothegreatsite.net", "it", "/reader")

class Kangaryu : FoolSlide("Kangaryu", "https://kangaryu-team.fr", "fr") {
    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers).also { latestUpdatesUrls.clear() }
    override fun latestUpdatesSelector() = "div.card"
    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("div.card-text a").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }
    override fun latestUpdatesNextPageSelector(): String? = null
    override val mangaDetailsInfoSelector = "div.info:not(.comic)"
}

class FallenWorldOrder : FoolSlide("Fall World Reader", "https://faworeader.altervista.org", "it", "/slide")

class NIFTeam : FoolSlide("NIFTeam", "http://read-nifteam.info", "it", "/slide")

class TuttoAnimeManga : FoolSlide("TuttoAnimeManga", "https://tuttoanimemanga.net", "it", "/slide")

class Customizable : ConfigurableSource, FoolSlide("Customizable", "", "other") {
    override val baseUrl: String by lazy { getPrefBaseUrl() }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val baseUrlPref = androidx.preference.EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF_TITLE
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            this.setDefaultValue(DEFAULT_BASEURL)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: $DEFAULT_BASEURL"

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(BASE_URL_PREF, newValue as String).commit()
                    Toast.makeText(screen.context, RESTART_TACHIYOMI, Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        screen.addPreference(baseUrlPref)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val baseUrlPref = EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF_TITLE
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            this.setDefaultValue(DEFAULT_BASEURL)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: $DEFAULT_BASEURL"

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(BASE_URL_PREF, newValue as String).commit()
                    Toast.makeText(screen.context, RESTART_TACHIYOMI, Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        screen.addPreference(baseUrlPref)
    }

    /**
     *  Tell the user to include /directory/ in the URL even though we remove it
     *  To increase the chance they input a usable URL
     */
    private fun getPrefBaseUrl() = preferences.getString(BASE_URL_PREF, DEFAULT_BASEURL)!!.substringBefore("/directory")

    companion object {
        private const val DEFAULT_BASEURL = "https://127.0.0.1"
        private const val BASE_URL_PREF_TITLE = "Example URL: https://domain.com/path_to/directory/"
        private const val BASE_URL_PREF = "overrideBaseUrl_v${BuildConfig.VERSION_NAME}"
        private const val BASE_URL_PREF_SUMMARY = "Connect to a designated FoolSlide server"
        private const val RESTART_TACHIYOMI = "Restart Tachiyomi to apply new setting."
    }
}

class TortugaCeviri : FoolSlide("Tortuga Ceviri", "http://tortuga-ceviri.com", "tr", "/okuma")

class Rama : FoolSlide("Rama", "https://www.ramareader.it", "it", "/read")

class Mabushimajo : FoolSlide("Mabushimajo", "http://mabushimajo.com", "tr", "/onlineokuma")
