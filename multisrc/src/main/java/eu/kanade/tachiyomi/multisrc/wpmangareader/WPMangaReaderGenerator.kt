package eu.kanade.tachiyomi.multisrc.wpmangareader

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class WPMangaReaderGenerator : ThemeSourceGenerator {

    override val themePkg = "wpmangareader"

    override val themeClass = "WPMangaReader"

    override val baseVersionCode: Int = 7

    override val sources = listOf(
        SingleLang("Kiryuu", "https://kiryuu.id", "id", overrideVersionCode = 1),
        SingleLang("KomikMama", "https://komikmama.net", "id"),
        SingleLang("MangaKita", "https://mangakita.net", "id", overrideVersionCode = 1),
        SingleLang("Martial Manga", "https://martialmanga.com/", "es"),
        SingleLang("Ngomik", "https://ngomik.net", "id", overrideVersionCode = 1),
        SingleLang("Sekaikomik", "https://www.sekaikomik.xyz", "id", isNsfw = true, overrideVersionCode = 5),
        SingleLang("Davey Scans", "https://daveyscans.com/", "id"),
        SingleLang("Mangasusu", "https://mangasusu.co.in", "id", isNsfw = true),
        SingleLang("TurkToon", "https://turktoon.com", "tr"),
        SingleLang("Gecenin Lordu", "https://geceninlordu.com/", "tr", overrideVersionCode = 1),
        SingleLang("Flame Scans", "https://flamescans.org", "en", overrideVersionCode = 3),
        SingleLang("A Pair of 2+", "https://pairof2.com", "en", className = "APairOf2"),
        SingleLang("PMScans", "https://reader.pmscans.com", "en"),
        SingleLang("Skull Scans", "https://www.skullscans.com", "en"),
        SingleLang("Luminous Scans", "https://www.luminousscans.com", "en"),
        SingleLang("Azure Scans", "https://azuremanga.com", "en"),
        SingleLang("Seafoam Scans", "https://seafoamscans.com", "en"),
        SingleLang("GS Nation", "https://gs-nation.fr", "fr", overrideVersionCode = 1),
        SingleLang("YugenMangas", "https://yugenmangas.com", "es"),
        SingleLang("DragonTranslation", "https://dragontranslation.com", "es", isNsfw = true)
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            WPMangaReaderGenerator().createAll()
        }
    }
}
