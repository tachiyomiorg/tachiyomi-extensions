package eu.kanade.tachiyomi.multisrc.mangadventure

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

/** [MangAdventure] source generator. */
class MangAdventureGenerator : ThemeSourceGenerator {
    override val themePkg = "mangadventure"

    override val themeClass = "MangAdventure"

    override val baseVersionCode = 1

    override val sources = listOf(
        SingleLang("Arc-Relight", "https://arc-relight.com", "en", className = "ArcRelight"),
    )

    companion object {
        @JvmStatic fun main(args: Array<String>) = MangAdventureGenerator().createAll()
    }
}
