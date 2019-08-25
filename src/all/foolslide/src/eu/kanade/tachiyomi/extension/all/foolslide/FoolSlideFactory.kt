package eu.kanade.tachiyomi.extension.all.foolslide

import android.util.Base64
import com.github.salomonbrys.kotson.get
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class FoolSlideFactory : SourceFactory {
    override fun createSources(): List<Source> = getAllFoolSlide()
}

fun getAllFoolSlide(): List<Source> {
    return listOf(
            JaminisBox(),
            HelveticaScans(),
            SenseScans(),
            KireiCake(),
            SilentSky(),
            Mangatellers(),
            IskultripScans(),
            AnataNoMotokare(),
            DeathTollScans(),
            DKThias(),
            WorldThree(),
            DokiFansubs(),
            YuriIsm(),
            AjiaNoScantrad(),
            OneTimeScans(),
            TsubasaSociety(),
            MangaScouts(),
            StormInHeaven(),
            Lilyreader(),
            Russification(),
            EvilFlowers(),
            AkaiYuhiMunTeam(),
            LupiTeam(),
            HentaiCafe(),
            ShoujoSense(),
            TheCatScans(),
            ShoujoHearts(),
            ZandynoFansub()
    )
}

class JaminisBox : FoolSlide("Jaimini's Box", "https://jaiminisbox.com", "en", "/reader") {

    override fun pageListParse(document: Document): List<Page> {
        val doc = document.toString()
        var jsonstr = doc.substringAfter("var pages = ").substringBefore(";")
        if (jsonstr.contains("JSON.parse")) {
            val base64Json = jsonstr.substringAfter("JSON.parse(atob(\"").substringBefore("\"));")
            jsonstr = String(Base64.decode(base64Json, Base64.DEFAULT))
        }
        val json = JsonParser().parse(jsonstr).asJsonArray
        val pages = mutableListOf<Page>()
        json.forEach {
            pages.add(Page(pages.size, "", it["url"].asString))
        }
        return pages
    }
}

class TheCatScans : FoolSlide("The Cat Scans", "https://reader2.thecatscans.com/", "en")

class HelveticaScans : FoolSlide("Helvetica Scans", "https://helveticascans.com", "en", "/r")

class SenseScans : FoolSlide("Sense-Scans", "http://sensescans.com", "en", "/reader")

class KireiCake : FoolSlide("Kirei Cake", "https://reader.kireicake.com", "en")

class SilentSky : FoolSlide("Silent Sky", "https://reader.silentsky-scans.net", "en")

class Mangatellers : FoolSlide("Mangatellers", "http://www.mangatellers.gr", "en", "/reader/reader") {
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl$urlModifier/list/$page/", headers)
    }
}

class IskultripScans : FoolSlide("Iskultrip Scans", "http://www.maryfaye.net", "en", "/reader")

class AnataNoMotokare : FoolSlide("Anata no Motokare", "https://motokare.xyz", "en", "/reader")

class DeathTollScans : FoolSlide("Death Toll Scans", "https://reader.deathtollscans.net", "en")

class DKThias : FoolSlide("DKThias Scanlations", "http://reader.dkthias.com", "en", "/reader") {
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl$urlModifier/list/$page/", headers)
    }
}

class WorldThree : FoolSlide("World Three", "http://www.slide.world-three.org", "en")

class DokiFansubs : FoolSlide("Doki Fansubs", "https://kobato.hologfx.com", "en", "/reader")

class YuriIsm : FoolSlide("Yuri-ism", "https://www.yuri-ism.net", "en", "/slide")

class AjiaNoScantrad : FoolSlide("Ajia no Scantrad", "https://ajianoscantrad.fr", "fr", "/reader")

class OneTimeScans : FoolSlide("One Time Scans", "https://otscans.com", "en", "/foolslide")

class TsubasaSociety : FoolSlide("Tsubasa Society", "https://www.tsubasasociety.com", "en", "/reader/master/Xreader")

class MangaScouts : FoolSlide("MangaScouts", "http://onlinereader.mangascouts.org", "de")

class StormInHeaven : FoolSlide("Storm in Heaven", "https://www.storm-in-heaven.net", "it", "/reader-sih")

class Lilyreader : FoolSlide("Lilyreader", "https://manga.smuglo.li", "en")

class Russification : FoolSlide("Русификация", "https://rusmanga.ru", "ru")

class EvilFlowers : FoolSlide("Evil Flowers", "http://reader.evilflowers.com", "en")

class AkaiYuhiMunTeam : FoolSlide("AkaiYuhiMun team", "https://akaiyuhimun.ru", "ru", "/manga")

class ShoujoSense : FoolSlide("ShoujoSense", "http://reader.shoujosense.com", "en")

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
        manga.thumbnail_url = document.select(mangaDetailsThumbnailSelector).first()?.absUrl("src")

        return manga
    }

}

class ShoujoHearts : FoolSlide("ShoujoHearts", "http://shoujohearts.com", "en", "/reader") {
    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        element.select("a[title]").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        element.select("img").first()?.let {
            manga.thumbnail_url = it.absUrl("src")
        }
        return manga
    }
}

class ZandynoFansub : FoolSlide("Zandy no Fansub", "http://zandynofansub.aishiteru.org", "en", "/reader")
