package eu.kanade.tachiyomi.multisrc.mangacatalog

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class MangaCatalogGenerator : ThemeSourceGenerator {

    override val themePkg = "mangacatalog"

    override val themeClass = "MangaCatalog"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("Read Boku no Hero Academia/My Hero Academia Manga", "https://ww6.readmha.com", "en", className = "ReadBokuNoHeroAcademiaMyHeroAcademiaManga", overrideVersionCode = 1),
        SingleLang("Read One-Punch Man Manga Online", "https://ww3.readopm.com", "en", className = "ReadOnePunchManMangaOnlineTwo", pkgName = "readonepunchmanmangaonlinetwo", overrideVersionCode = 1), //exact same name as the one in mangamainac extension
        SingleLang("Read Tokyo Ghoul Re & Tokyo Ghoul Manga Online", "https://ww8.tokyoghoulre.com", "en", className = "ReadTokyoGhoulReTokyoGhoulMangaOnline", overrideVersionCode = 1),
        SingleLang("Read Nanatsu no Taizai/7 Deadly Sins Manga Online", "https://ww3.read7deadlysins.com", "en", className = "ReadNanatsuNoTaizai7DeadlySinsMangaOnline", overrideVersionCode = 1),
        SingleLang("Read Kaguya-sama Manga Online", "https://ww1.readkaguyasama.com", "en", className = "ReadKaguyaSamaMangaOnline", overrideVersionCode = 1),
        SingleLang("Read Jujutsu Kaisen Manga Online", "https://ww1.readjujutsukaisen.com", "en", overrideVersionCode = 1),
        SingleLang("Read Tower of God Manhwa/Manga Online", "https://ww1.readtowerofgod.com", "en", className = "ReadTowerOfGodManhwaMangaOnline", overrideVersionCode = 1),
        SingleLang("Read Hunter x Hunter Manga Online", "https://ww2.readhxh.com", "en", overrideVersionCode = 1),
        SingleLang("Read Solo Leveling Manga/Manhwa Online", "https://readsololeveling.org", "en", className = "ReadSoloLevelingMangaManhwaOnline", overrideVersionCode = 1),
        SingleLang("Read The Promised Neverland Manga Online", "https://ww3.readneverland.com", "en", overrideVersionCode = 1),
        SingleLang("Read Attack on Titan/Shingeki no Kyojin Manga", "https://ww7.readsnk.com", "en", className = "ReadAttackOnTitanShingekiNoKyojinManga", overrideVersionCode = 1),
        SingleLang("Read Berserk Manga", "https://readberserk.com", "en"),
        SingleLang("Read Fairy Tail & Edens Zero Manga Online", "https://ww4.readfairytail.com", "en", className = "ReadFairyTailEdensZeroMangaOnline"),
        SingleLang("Read One Piece Manga Online", "https://ww8.readonepiece.com", "en"),
        SingleLang("Read Dr. Stone Manga Online", "https://ww3.readdrstone.com", "en", className = "ReadDrStoneMangaOnline"),
        SingleLang("Read Chainsaw Man Manga Online", "https://ww1.readchainsawman.com", "en"),
        SingleLang("Read Dragon Ball Super/Chou Manga Online", "https://ww6.dbsmanga.com", "en", className = "ReadDragonBallSuperChouMangaOnline"),
        SingleLang("Read Kingdom Manga Online", "https://ww2.readkingdom.com", "en"),
        SingleLang("Read Naruto/Boruto/Samurai 8 Manga Online", "https://ww7.readnaruto.com", "en", className = "ReadNarutoBorutoSamurai8MangaOnline"),
        SingleLang("Read Vinland Saga Manga Online", "https://ww1.readvinlandsaga.com", "en"),
        SingleLang("Read Haikyuu!! Manga Online", "https://ww6.readhaikyuu.com", "en", className = "ReadHaikyuuMangaOnline"),
        SingleLang("Read Goblin Slayer Manga Online", "https://manga.watchgoblinslayer.com", "en"),
        SingleLang("Read Sword Art Online Manga Online", "https://manga.watchsao.tv", "en"),
        SingleLang("Read Noblesse Manhwa Online", "https://ww2.readnoblesse.com", "en"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MangaCatalogGenerator().createAll()
        }
    }
}
