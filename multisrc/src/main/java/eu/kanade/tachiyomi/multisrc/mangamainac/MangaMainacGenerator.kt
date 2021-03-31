package eu.kanade.tachiyomi.multisrc.mangamainac

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class MangaMainacGenerator : ThemeSourceGenerator {

    override val themePkg = "mangamainac"

    override val themeClass = "MangaMainac"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("Read Boku No Hero Academia Manga Online", "https://w23.readheroacademia.com/", "en", className = "ReadBokuNoHeroAcademiaMangaOnline", pkgName = "readbokunoheroacademiamangaonline"),
        SingleLang("Read One Punch Man Manga Online", "https://w17.readonepunchman.net/", "en", className = "ReadOnePunchManMangaOnline", pkgName = "readonepunchmanmangaonline"),
        SingleLang("Read One Webcomic Manga Online", "https://w1.onewebcomic.net/", "en", className = "ReadOneWebcomicMangaOnline", pkgName = "readonewebcomicmangaonline"),
        SingleLang("Read Solo Leveling", "https://w3.sololeveling.net/", "en", className = "ReadSoloLeveling", pkgName = "readsololeveling"),
        SingleLang("Read Jojolion", "https://readjojolion.com/", "en", className = "ReadJojolion", pkgName = "readjojolion"),
        SingleLang("Hajime no Ippo Manga", "https://readhajimenoippo.com/", "en", className = "HajimeNoIppoManga", pkgName = "hajimenoippomanga"),
        SingleLang("Read Berserk Manga Online", "https://berserkmanga.net/", "en", className = "ReadBerserkMangaOnline", pkgName = "readberserkmangaonline"),
        SingleLang("Read Kaguya-sama: Love is War", "https://kaguyasama.net/", "en", className = "ReadKaguyaSamaLoveIsWar", pkgName = "readkaguyasamaloveiswar"),
        SingleLang("Read Domestic Girlfriend Manga", "https://domesticgirlfriend.net/", "en", className = "ReadDomesticGirlfriendManga", pkgName = "readdomesticgirlfriendmanga"),
        SingleLang("Read Black Clover Manga", "https://w1.blackclovermanga2.com/", "en", className = "ReadBlackCloverManga", pkgName = "readblackclovermanga"),
        SingleLang("TCB Scans", "https://onepiecechapters.com/", "en", className = "TCBScans", pkgName = "tcbscans", overrideVersionCode = 2),
        SingleLang("Read Shingeki no Kyojin Manga", "https://readshingekinokyojin.com/", "en", className = "ReadShingekiNoKyojinManga", pkgName = "readshingekinokyojinmanga"),
        SingleLang("Read Nanatsu no Taizai Manga", "https://w1.readnanatsutaizai.net/", "en", className = "ReadNanatsuNoTaizaiManga", pkgName = "readnanatsunotaizaimanga"),
        SingleLang("Read Rent a Girlfriend Manga", "https://kanojo-okarishimasu.com/", "en", className = "ReadRentAGirlfriendManga", pkgName = "readrentagirlfriendmanga"),
        //Sites that are currently down from my end, should be rechecked by some one else at some point
        //
        //SingleLang("", "https://5-toubunnohanayome.net/", "en", className = "", pkgName = ""), //Down at time of creating this generator
        //SingleLang("", "http://beastars.net/", "en", className = "", pkgName = ""), //Down at time of creating this generator
        //SingleLang("", "https://neverlandmanga.net/", "en", className = "", pkgName = ""), //Down at time of creating this generator
        //SingleLang("", "https://ww1.readhunterxhunter.net/", "en", className = "", pkgName = ""), //Down at time of creating this generator
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MangaMainacGenerator().createAll()
        }
    }
}
