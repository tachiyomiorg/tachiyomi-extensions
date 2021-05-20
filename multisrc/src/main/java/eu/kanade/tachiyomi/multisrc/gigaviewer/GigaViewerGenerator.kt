package eu.kanade.tachiyomi.multisrc.gigaviewer

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class GigaViewerGenerator : ThemeSourceGenerator {

    override val themePkg = "gigaviewer"

    override val themeClass = "GigaViewer"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("Comic Gardo", "https://comic-gardo.com", "ja"),
        SingleLang("Kurage Bunch", "https://kuragebunch.com", "ja"),
        SingleLang("Magazine Pocket", "https://pocket.shonenmagazine.com", "ja"),
        SingleLang("Shonen Jump+", "https://shonenjumpplus.com", "ja", pkgName = "shonenjumpplus", className = "ShonenJumpPlus", overrideVersionCode = 2),
        SingleLang("Tonari no Young Jump", "https://tonarinoyj.jp", "ja", className = "TonariNoYoungJump")
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            GigaViewerGenerator().createAll()
        }
    }
}
