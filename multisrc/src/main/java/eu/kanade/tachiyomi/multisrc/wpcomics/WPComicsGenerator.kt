package eu.kanade.tachiyomi.multisrc.wpcomics

import generator.ThemeSourceData.MultiLang
import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class WPComicsGenerator : ThemeSourceGenerator {

    override val themePkg = "wpcomics"

    override val themeClass = "WPComics"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
            SingleLang("ComicLatest", "https://comiclatest.com", "en", overrideVersionCode = 1),
            MultiLang("MangaSum", "https://mangasum.com", listOf("en", "ja")),
            SingleLang("NetTruyen", "https://www.nettruyen.com", "vi", overrideVersionCode = 1),
            SingleLang("NhatTruyen", "http://nhattruyen.com", "vi", overrideVersionCode = 1),
            SingleLang("TruyenChon", "http://truyenchon.com", "vi", overrideVersionCode = 1),
            SingleLang("XOXO Comics", "https://xoxocomics.com", "en", className = "XoxoComics", overrideVersionCode = 1),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            WPComicsGenerator().createAll()
        }
    }
}
