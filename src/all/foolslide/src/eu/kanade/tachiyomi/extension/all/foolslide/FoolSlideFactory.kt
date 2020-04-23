package eu.kanade.tachiyomi.extension.all.foolslide

import android.util.Base64
import com.github.salomonbrys.kotson.get
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.nodes.Document

class FoolSlideFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        JaminisBox(),
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
        BaixarHentai()
    )
}

class JaminisBox : FoolSlide("Jaimini's Box", "https://jaiminisbox.com", "en", "/reader") {
    val SLUG_REGEX = "(?:/read/)([\\w\\d-]+?)(?:/)".toRegex()
    override fun pageListRequest(chapter: SChapter): Request {
        val (slug) = SLUG_REGEX.find(chapter.url)!!.destructured
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

class IskultripScans : FoolSlide("Iskultrip Scans", "http://www.maryfaye.net", "en", "/reader")

class AnataNoMotokare : FoolSlide("Anata no Motokare", "https://motokare.xyz", "en", "/reader")

class DeathTollScans : FoolSlide("Death Toll Scans", "https://reader.deathtollscans.net", "en")

class DokiFansubs : FoolSlide("Doki Fansubs", "https://kobato.hologfx.com", "en", "/reader")

class YuriIsm : FoolSlide("Yuri-ism", "https://www.yuri-ism.net", "en", "/slide")

class AjiaNoScantrad : FoolSlide("Ajia no Scantrad", "https://ajianoscantrad.fr", "fr", "/reader")

class OneTimeScans : FoolSlide("One Time Scans", "https://reader.otscans.com", "en")

class MangaScouts : FoolSlide("MangaScouts", "http://onlinereader.mangascouts.org", "de")

class StormInHeaven : FoolSlide("Storm in Heaven", "https://www.storm-in-heaven.net", "it", "/reader-sih")

class Lilyreader : FoolSlide("Lilyreader", "https://manga.smuglo.li", "en")

class Russification : FoolSlide("Русификация", "https://rusmanga.ru", "ru")

class EvilFlowers : FoolSlide("Evil Flowers", "http://reader.evilflowers.com", "en")

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

class KirishimaFansub : FoolSlide("Kirishima Fansub", "https://kirishimafansub.net", "es", "/lector")

class PowerMangaIT : FoolSlide("PowerManga", "https://reader.powermanga.org", "it", "")

class BaixarHentai : FoolSlide("Baixar Hentai", "https://leitura.baixarhentai.net", "pt") {
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.select("h1.title").text()
            thumbnail_url = getDetailsThumbnail(document, "div.title a")
        }
    }
}
