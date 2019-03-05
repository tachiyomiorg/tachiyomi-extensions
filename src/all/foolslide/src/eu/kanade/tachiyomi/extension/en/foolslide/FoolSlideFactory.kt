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

class FoolSlideFactory : SourceFactory {
    override fun createSources(): List<Source> = getAllFoolSlide()
}

fun getAllFoolSlide(): List<Source> {
    return listOf(
            JaminisBox(),
            HelveticaScans(),
            SenseScans(),
            SeaOtterScans(),
            KireiCake(),
            HiranoMoeScansBureau(),
            SilentSky(),
            Mangatellers(),
            IskultripScans(),
            PinkFatale(),
            AnataNoMotokare(),
            HatigarmScans(),
            DeathTollScans(),
            DKThias(),
            MangaichiScanlationDivision(),
            WorldThree(),
            TheCatScans(),
            AngelicScanlations(),
            DokiFansubs(),
            YuriIsm(),
            AjiaNoScantrad(),
            OneTimeScans(),
            TsubasaSociety(),
            Helheim(),
            MangaScouts(),
            StormInHeaven(),
            Lilyreader(),
            MidnightHaven(),
            Russification(),
            NieznaniReader(),
            EvilFlowers(),
            AkaiYuhiMunTeam(),
            LupiTeam(),
            HotChocolateScans()
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

class HotChocolateScans : FoolSlide("Hot Chocolate Scans", "http://hotchocolatescans.com", "en", "/fs")

class HelveticaScans : FoolSlide("Helvetica Scans", "https://helveticascans.com", "en", "/r")

class SenseScans : FoolSlide("Sense-Scans", "https://sensescans.com", "en", "/reader")

class SeaOtterScans : FoolSlide("Sea Otter Scans", "https://reader.seaotterscans.com", "en")

class KireiCake : FoolSlide("Kirei Cake", "https://reader.kireicake.com", "en")

class HiranoMoeScansBureau : FoolSlide("HiranoMoe Scans Bureau", "https://hiranomoe.com", "en", "/r")

class SilentSky : FoolSlide("Silent Sky", "http://reader.silentsky-scans.net", "en")

class Mangatellers : FoolSlide("Mangatellers", "http://www.mangatellers.gr", "en", "/reader/reader") {
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl$urlModifier/list/$page/", headers)
    }
}

class IskultripScans : FoolSlide("Iskultrip Scans", "http://www.maryfaye.net", "en", "/reader")

class PinkFatale : FoolSlide("PinkFatale", "http://manga.pinkfatale.net", "en")

class AnataNoMotokare : FoolSlide("Anata no Motokare", "https://motokare.maos.ca", "en")

// Has other languages too but it is difficult to differentiate between them
class HatigarmScans : FoolSlide("Hatigarm Scans", "http://hatigarmscans.eu", "en", "/hs") {
    override fun chapterListSelector() = "div.list-group div.list-group-item:not(.active)"

    override val chapterDateSelector = "div.label"

    override val chapterUrlSelector = ".title > a"

    override fun popularMangaSelector() = ".well > a"

    override fun latestUpdatesSelector() = "div.latest > div.row"

    override val mangaDetailsInfoSelector = "div.col-md-9"

    override val mangaDetailsThumbnailSelector = "div.thumb > img"
}

class DeathTollScans : FoolSlide("Death Toll Scans", "https://reader.deathtollscans.net", "en")

class DKThias : FoolSlide("DKThias Scanlations", "http://reader.dkthias.com", "en", "/reader") {
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl$urlModifier/list/$page/", headers)
    }
}

class MangaichiScanlationDivision : FoolSlide("Mangaichi Scanlation Division", "http://mangaichiscans.mokkori.fr", "en", "/fs")

class WorldThree : FoolSlide("World Three", "http://www.slide.world-three.org", "en")

class TheCatScans : FoolSlide("The Cat Scans", "https://reader.thecatscans.com", "en")

class AngelicScanlations : FoolSlide("Angelic Scanlations", "http://www.angelicscans.net", "en", "/foolslide") {
    override fun latestUpdatesSelector() = "div.list > div.releases"

    override fun popularMangaSelector() = ".grouped > .series-block"

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            thumbnail_url = document.select(".preview > img").attr("src")

            val info = document.select(".data").first()
            title = info.select("h2.title").text().trim()
            val authorArtist = info.select(".author").text().split("/")
            author = authorArtist.getOrNull(0)?.trim()
            artist = authorArtist.getOrNull(1)?.trim()

            description = info.ownText().trim()
        }
    }

    override fun chapterListSelector() = ".list > .release"

    override val chapterDateSelector = ".metadata"
}

class DokiFansubs : FoolSlide("Doki Fansubs", "https://kobato.hologfx.com", "en", "/reader")

class YuriIsm : FoolSlide("Yuri-ism", "https://reader.yuri-ism.com", "en", "/slide")

class AjiaNoScantrad : FoolSlide("Ajia no Scantrad", "https://ajianoscantrad.fr", "fr", "/reader")

class OneTimeScans : FoolSlide("One Time Scans", "https://otscans.com", "en", "/foolslide")

class TsubasaSociety : FoolSlide("Tsubasa Society", "https://www.tsubasasociety.com", "en", "/reader/master/Xreader")

class Helheim : FoolSlide("Helheim", "http://helheim.pl", "pl", "/reader")

class MangaScouts : FoolSlide("MangaScouts", "http://onlinereader.mangascouts.org", "de")

class StormInHeaven : FoolSlide("Storm in Heaven", "http://www.storm-in-heaven.net", "it", "/reader-sih")

class Lilyreader : FoolSlide("Lilyreader", "https://manga.smuglo.li", "en")

class MidnightHaven : FoolSlide("Midnight Haven", "http://midnighthaven.shounen-ai.net", "en", "/reader")

class Russification : FoolSlide("Русификация", "https://rusmanga.ru", "ru")

class NieznaniReader : FoolSlide("Nieznani", "http://reader.nieznani.mynindo.pl", "pl")

class EvilFlowers : FoolSlide("Evil Flowers", "http://reader.evilflowers.com", "en")

class AkaiYuhiMunTeam : FoolSlide("AkaiYuhiMun team", "https://akaiyuhimun.ru", "ru", "/manga")

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
