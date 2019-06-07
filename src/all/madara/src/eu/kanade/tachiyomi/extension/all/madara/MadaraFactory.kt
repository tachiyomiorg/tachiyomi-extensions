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

class LeviatanScans(lang: String) : Madara("LeviatanScans", "https://leviatanscans.com", lang, dateFormat = SimpleDateFormat("MMMM dd, yy", Locale("es", "ES")))
class Mangasushi : Madara("Mangasushi", "https://mangasushi.net", "en") {
    override fun latestUpdatesSelector() = "div.page-item-detail"
}
class NinjaScans : Madara("NinjaScans", "https://ninjascans.com", "en", urlModifier = "/manhua") {
    override fun popularMangaNextPageSelector() = "div.nav-previous"
    override fun searchMangaNextPageSelector() = "div.nav-previous"
}
class ReadManhua : Madara("ReadManhua", "https://readmanhua.net", "en", dateFormat = SimpleDateFormat("dd MMM yy", Locale.US))
class ZeroScans : Madara("ZeroScans", "https://zeroscans.com", "en") {
    override fun popularMangaNextPageSelector() = "div.nav-previous"
    override fun searchMangaNextPageSelector() = "div.nav-previous"
}