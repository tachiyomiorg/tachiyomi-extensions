package eu.kanade.tachiyomi.multisrc.fmreader

import generator.ThemeSourceData.MultiLang
import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class FMReaderGenerator : ThemeSourceGenerator {

    override val themePkg = "fmreader"

    override val themeClass = "FMReader"

    override val baseVersionCode: Int = 4

    /** For future sources: when testing and popularMangaRequest() returns a Jsoup error instead of results
     *  most likely the fix is to override popularMangaNextPageSelector()   */

    override val sources = listOf(
        SingleLang("Epik Manga", "https://www.epikmanga.com", "tr"),
        SingleLang("HeroScan", "https://heroscan.com", "en"),
        SingleLang("KissLove", "https://kissaway.net", "ja"),
        SingleLang("LHTranslation", "https://lhtranslation.net", "en", overrideVersionCode = 1),
        SingleLang("Manga-TR", "https://manga-tr.com", "tr", className = "MangaTR"),
        SingleLang("ManhuaScan", "https://manhuascan.com", "en", isNsfw = true, overrideVersionCode = 1),
        SingleLang("Manhwa18", "https://manhwa18.com", "en", isNsfw = true),
        MultiLang("Manhwa18.net", "https://manhwa18.net", listOf("en", "ko"), className = "Manhwa18NetFactory", isNsfw = true),
        SingleLang("ManhwaSmut", "https://manhwasmut.com", "en", isNsfw = true, overrideVersionCode = 2),
        SingleLang("RawLH", "https://lovehug.net", "ja"),
        SingleLang("Say Truyen", "https://saytruyen.com", "vi"),
        SingleLang("KSGroupScans", "https://ksgroupscans.com", "en"),
        // Sites that went down
        //SingleLang("18LHPlus", "https://18lhplus.com", "en", className = "EighteenLHPlus"),
        //SingleLang("HanaScan (RawQQ)", "https://hanascan.com", "ja", className = "HanaScanRawQQ"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            FMReaderGenerator().createAll()
        }
    }
}
