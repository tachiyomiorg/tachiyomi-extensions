package eu.kanade.tachiyomi.multisrc.mangasproject

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class MangasProjectGenerator : ThemeSourceGenerator {

    override val themePkg = "mangasproject"

    override val themeClass = "MangasProject"

    override val baseVersionCode: Int = 6

    override val sources = listOf(
        SingleLang("Leitor.net", "https://leitor.net", "pt-BR", className = "LeitorNet", isNsfw = true),
        SingleLang("Mangá Livre", "https://mangalivre.net", "pt-BR", className = "MangaLivre", isNsfw = true),
        SingleLang("Toonei", "https://toonei.net", "pt-BR", isNsfw = true),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MangasProjectGenerator().createAll()
        }
    }
}
