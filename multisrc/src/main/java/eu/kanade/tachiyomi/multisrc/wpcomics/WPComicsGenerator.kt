package eu.kanade.tachiyomi.multisrc.wpcomics

import generator.ThemeSourceData.MultiLang
import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class WPComicsGenerator : ThemeSourceGenerator {

    override val themePkg = "wpcomics"

    override val themeClass = "WPComics"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
            MultiLang("MangaToro", "https://mangatoro.com", listOf("en", "ja")),
            SingleLang("NetTruyen", "http://www.nettruyentop.com", "vi", overrideVersionCode = 2),
            SingleLang("NhatTruyen", "http://nhattruyentranh.com", "vi", overrideVersionCode = 2),
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
