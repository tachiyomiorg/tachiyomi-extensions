package eu.kanade.tachiyomi.extension.all.madara

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import java.text.SimpleDateFormat
import java.util.*

class MadaraFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
            LeviatanScans("en"),
            LeviatanScans("es"),
            Mangasushi(),
            NinjaScans(),
            ReadManhua(),
            ZeroScans()
    )
}

class LeviatanScans(lang: String) : Madara("LeviatanScans", "https://leviatanscans.com", lang, dateFormat = SimpleDateFormat("MMMM dd, yy", Locale("es", "ES"))) {
    override fun popularMangaSelector() = if(lang == "en") "div.page-item-detail:contains(Chapter)" else "div.page-item-detail:contains(Capitulo)"
    override fun latestUpdatesSelector() = if(lang == "en") "div.item__wrap:contains(Chapter)" else "div.item__wrap:contains(Capitulo)"
    // Workaround - it might give a 404 error
    override fun popularMangaNextPageSelector() = "div.page-listing-item:nth-child(5) > div:nth-child(1) > div:nth-child(2)"
}
class Mangasushi : Madara("Mangasushi", "https://mangasushi.net", "en") {
    override fun latestUpdatesSelector() = "div.page-item-detail"
    // Workaround - it might give a 404 error
    override fun popularMangaNextPageSelector() = "div.page-listing-item:nth-child(6) > div:nth-child(1) > div:nth-child(2)"
}
class NinjaScans : Madara("NinjaScans", "https://ninjascans.com", "en", urlModifier = "/manhua") {
    override fun popularMangaNextPageSelector() = "div.nav-previous"
}
class ReadManhua : Madara("ReadManhua", "https://readmanhua.net", "en", dateFormat = SimpleDateFormat("dd MMM yy", Locale.US)) {
    // Workaround - it might give a 404 error
    override fun popularMangaNextPageSelector() = "div.page-listing-item:nth-child(6) > div:nth-child(1) > div:nth-child(2)"
}
class ZeroScans : Madara("ZeroScans", "https://zeroscans.com", "en") {
    override fun popularMangaNextPageSelector() = "div.nav-previous"
}