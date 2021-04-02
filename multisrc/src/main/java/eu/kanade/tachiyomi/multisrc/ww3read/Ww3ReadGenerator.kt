package eu.kanade.tachiyomi.multisrc.ww3read

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class Ww3ReadGenerator : ThemeSourceGenerator {

    override val themePkg = "ww3read"

    override val themeClass = "Ww3read"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("Read Boku no Hero Academia/My Hero Academia Manga", "https://ww6.readmha.com", "en", className = "ReadBokuNoHeroAcademiaMyHeroAcademiaManga"),
        SingleLang("Read One-Punch Man Manga Online", "https://ww3.readopm.com", "en", className = "ReadOnePunchManMangaOnlineTwo", pkgName = "readonepunchmanmangaonlinetwo"), //exact same name as the one in mangamainac extension
        SingleLang("Read Tokyo Ghoul Re & Tokyo Ghoul Manga Online", "https://ww8.tokyoghoulre.com", "en", className = "ReadTokyoGhoulReTokyoGhoulMangaOnline"),
        SingleLang("Read Nanatsu no Taizai/7 Deadly Sins Manga Online", "https://ww3.read7deadlysins.com", "en", className = "ReadNanatsuNoTaizai7DeadlySinsMangaOnline"),
        SingleLang("Read Kaguya-sama Manga Online", "https://ww1.readkaguyasama.com", "en", className = "ReadKaguyaSamaMangaOnline")
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Ww3ReadGenerator().createAll()
        }
    }
}
