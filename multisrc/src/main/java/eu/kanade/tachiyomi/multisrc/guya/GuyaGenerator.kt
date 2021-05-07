package eu.kanade.tachiyomi.multisrc.guya

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class GuyaGenerator : ThemeSourceGenerator {

    override val themePkg = "guya"

    override val themeClass = "Guya"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("Guya", "https://guya.moe", "en", overrideVersionCode = 18),
        SingleLang("Danke fürs Lesen", "https://danke.moe", "en", className = "DankeFursLesen"),
    )
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            GuyaGenerator().createAll()
        }
    }
}
