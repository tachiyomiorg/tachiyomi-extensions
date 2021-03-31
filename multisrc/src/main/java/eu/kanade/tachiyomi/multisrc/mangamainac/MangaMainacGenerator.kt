package eu.kanade.tachiyomi.multisrc.mangamainac

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class MangaMainacGenerator : ThemeSourceGenerator {

    override val themePkg = "mangamainac"

    override val themeClass = "MangaMainac"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("Read Boku No Hero Academia Manga Online", "https://w23.readheroacademia.com/", "en"),
        SingleLang("Read One Punch Man Manga Online", "https://w17.readonepunchman.net/", "en"),
        SingleLang("Read One Webcomic Manga Online", "https://w1.onewebcomic.net/", "en"),
        SingleLang("Read Solo Leveling", "https://w3.sololeveling.net/", "en"),
        SingleLang("Read Jojolion", "https://readjojolion.com/", "en"),
        SingleLang("Hajime no Ippo Manga", "https://readhajimenoippo.com/", "en"),
        SingleLang("Read Berserk Manga Online", "https://berserkmanga.net/", "en"),
        SingleLang("Read Kaguya-sama: Love is War", "https://kaguyasama.net/", "en", className = "ReadKaguyaSamaLoveIsWar", pkgName = "readkaguyasamaloveiswar"),
        SingleLang("Read Domestic Girlfriend Manga", "https://domesticgirlfriend.net/", "en""),
        SingleLang("Read Black Clover Manga", "https://w1.blackclovermanga2.com/", "en"),
        SingleLang("TCB Scans", "https://onepiecechapters.com/", "en", overrideVersionCode = 2),
        SingleLang("Read Shingeki no Kyojin Manga", "https://readshingekinokyojin.com/", "en"),
        SingleLang("Read Nanatsu no Taizai Manga", "https://w1.readnanatsutaizai.net/", "en"),
        SingleLang("Read Rent a Girlfriend Manga", "https://kanojo-okarishimasu.com/", "en"),
        //Sites that are currently down from my end, should be rechecked by some one else at some point
        //
        //SingleLang("", "https://5-toubunnohanayome.net/", "en"), //Down at time of creating this generator
        //SingleLang("", "http://beastars.net/", "en"), //Down at time of creating this generator
        //SingleLang("", "https://neverlandmanga.net/", "en"), //Down at time of creating this generator
        //SingleLang("", "https://ww1.readhunterxhunter.net/", "en"), //Down at time of creating this generator
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MangaMainacGenerator().createAll()
        }
    }
}
