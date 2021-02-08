package eu.kanade.tachiyomi.multisrc.eromuse

import eu.kanade.tachiyomi.multisrc.ThemeSourceData.SingleLang
import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator

class EroMuseGenerator : ThemeSourceGenerator {

    override val themePkg = "eromuse"

    override val themeClass = "EroMuse"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("8Muses", "https://comics.8muses.com", "en", className = "EightMuses"),
        SingleLang("Erofus", "https://www.erofus.com", "en")
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            EroMuseGenerator().createAll()
        }
    }
}
