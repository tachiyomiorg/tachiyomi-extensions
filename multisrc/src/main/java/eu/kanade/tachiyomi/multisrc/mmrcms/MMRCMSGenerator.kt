package eu.kanade.tachiyomi.multisrc.mmrcms

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class MMRCMSGenerator : ThemeSourceGenerator {

    override val themePkg = "mmrcms"

    override val themeClass = "MMRCMS"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("مانجا اون لاين", "https://onma.me", "ar", className = "ONMA"),
        SingleLang("Read Comics Online", "https://readcomicsonline.ru", "en"),
        SingleLang("Fallen Angels", "https://manga.fascans.com", "en"),
        SingleLang("Zahard", "https://zahard.top", "en"),
        SingleLang("Manhwas Men", "https://manhwas.men", "en"),
        SingleLang("Scan FR", "https://www.scan-fr.cc", "fr"),
        SingleLang("Scan VF", "https://www.scan-vf.net", "fr"),
        SingleLang("Scan OP", "https://scan-op.cc", "fr"),
        SingleLang("Komikid", "https://www.komikid.com", "id"),
        SingleLang("Nikushima", "http://azbivo.webd.pro", "pl"),
        SingleLang("MangaHanta", "http://mangahanta.com", "tr"),
        SingleLang("Fallen Angels Scans", "https://truyen.fascans.com", "vi"),
        SingleLang("LeoManga", "https://leomanga.me", "es"),
        SingleLang("submanga", "https://submanga.io", "es"),
        SingleLang("Mangadoor", "https://mangadoor.com", "es"),
        SingleLang("Mangas.pw", "https://mangas.in", "es", className = "MangasPw"),
        SingleLang("Utsukushii", "https://manga.utsukushii-bg.com", "bg"),
        SingleLang("Phoenix-Scans", "https://phoenix-scans.pl", "pl", className = "PhoenixScans"),
        SingleLang("Puzzmos", "https://puzzmos.com", "tr"),
        SingleLang("Scan-1", "https://wwv.scan-1.com", "fr", className = "ScanOne"),
        SingleLang("Lelscan-VF", "https://lelscan-vf.co", "fr", className = "LelscanVF"),
        SingleLang("Komik Manga", "https://adm.komikmanga.com", "id"),
        SingleLang("Mangazuki Raws", "https://raws.mangazuki.co", "ko"),
        SingleLang("Mangazuki", "https://mangazuki.co/", "en"),
        SingleLang("Remangas", "https://remangas.top", "pt-BR"),
        SingleLang("AnimaRegia", "https://animaregia.net", "pt-BR"),
        SingleLang("MangaVadisi", "http://manga-v2.mangavadisi.org", "tr"),
        SingleLang("MangaID", "https://mangaid.click", "id"),
        SingleLang("Jpmangas", "https://jpmangas.co", "fr"),
        SingleLang("Op-VF", "https://www.op-vf.com", "fr", className = "OpVF"),
        // NOTE: THIS SOURCE CONTAINS A CUSTOM LANGUAGE SYSTEM (which will be ignored)!
        SingleLang("HentaiShark", "https://www.hentaishark.com", "other", isNsfw = true),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MMRCMSGenerator().createAll()
        }
    }
}

// Reference from old Factory Source
// Changed CMS
// SingleLang("Mangás Yuri", "https://mangasyuri.net", "pt-BR", className = "MangasYuri"), //ID "Mangás Yuri" -> 6456162511058446409 //Is now selling manga 
// SourceData("es", "Tumangaonline.co", "http://tumangaonline.com"),
// SourceData("id", "MangaYu", "https://mangayu.com"),
// SourceData("en", "MangaTreat Scans", "http://www.mangatreat.com"),
// SourceData("en", "Chibi Manga Reader", "https://www.cmreader.info"),
// SourceData("tr", "Epikmanga", "https://www.epikmanga.com"),
// SourceData("en", "Hatigarm Scans", "https://hatigarmscans.net"),
// Went offline
// SingleLang("FR Scan", "https://www.frscan.me", "fr"),
// SourceData("ru", "Japit Comics", "https://j-comics.ru"),
// SourceData("es", "Universo Yuri", "https://universoyuri.com"),
// SourceData("pl", "Dracaena", "https://dracaena.webd.pl/czytnik"),
// SourceData("pt-BR", "Comic Space", "https://www.comicspace.com.br"), //ID "Comic Space" -> 1847392744200215680
// SourceData("pl", "ToraScans", "http://torascans.pl"),
// SourceData("en", "White Cloud Pavilion", "https://www.whitecloudpavilion.com/manga/free"),
// SourceData("en", "Biamam Scans", "https://biamam.com"),
// SourceData("en", "Mangawww Reader", "https://mangawww.club"),
// SourceData("ru", "Anigai clan", "http://anigai.ru"),
// SourceData("en", "ZXComic", "http://zxcomic.com"),
// SourceData("es", "SOS Scanlation", "https://sosscanlation.com"),
// SourceData("es", "MangaCasa", "https://mangacasa.com"))
// SourceData("ja", "RAW MANGA READER", "https://rawmanga.site"),
// SourceData("ar", "Manga FYI", "http://mangafyi.com/manga/arabic"),
// SourceData("en", "MangaRoot", "http://mangaroot.com"),
// SourceData("en", "MangaForLife", "http://manga4ever.com"),
// SourceData("en", "Manga Spoil", "http://mangaspoil.com"),
// SourceData("en", "MangaBlue", "http://mangablue.com"),
// SourceData("en", "Manga Forest", "https://mangaforest.com"),
// SourceData("en", "DManga", "http://dmanga.website"),
// SourceData("en", "DB Manga", "http://dbmanga.com"),
// SourceData("en", "Mangacox", "http://mangacox.com"),
// SourceData("en", "GO Manhwa", "http://gomanhwa.xyz"),
// SourceData("en", "KoManga", "https://komanga.net"),
// SourceData("en", "Manganimecan", "http://manganimecan.com"),
// SourceData("en", "Hentai2Manga", "http://hentai2manga.com"),
// SourceData("en", "4 Manga", "http://4-manga.com"),
// SourceData("en", "XYXX.INFO", "http://xyxx.info"),
// SourceData("en", "Isekai Manga Reader", "https://isekaimanga.club"),
// SourceData("fa", "TrinityReader", "http://trinityreader.pw"),
// SourceData("fr", "Manga-LEL", "https://www.manga-lel.com"),
// SourceData("fr", "Manga Etonnia", "https://www.etonnia.com"),
// SourceData("fr", "ScanFR.com"), "http://scanfr.com"),
// SourceData("fr", "Manga FYI", "http://mangafyi.com/manga/french"),
// SourceData("fr", "scans-manga", "http://scans-manga.com"),
// SourceData("fr", "Henka no Kaze", "http://henkanokazelel.esy.es/upload"),
// SourceData("fr", "Tous Vos Scans", "http://www.tous-vos-scans.com"),
// SourceData("id", "Manga Desu", "http://mangadesu.net"),
// SourceData("id", "Komik Mangafire.ID", "http://go.mangafire.id"),
// SourceData("id", "MangaOnline", "https://mangaonline.web.id"),
// SourceData("id", "MangaNesia", "https://manganesia.com"),
// SourceData("id", "MangaID", "https://mangaid.me"
// SourceData("id", "Manga Seru", "http://www.mangaseru.top"
// SourceData("id", "Manga FYI", "http://mangafyi.com/manga/indonesian"
// SourceData("id", "Bacamangaku", "http://www.bacamangaku.com"),
// SourceData("id", "Indo Manga Reader", "http://indomangareader.com"),
// SourceData("it", "Kingdom Italia Reader", "http://kireader.altervista.org"),
// SourceData("ja", "IchigoBook", "http://ichigobook.com"),
// SourceData("ja", "Mangaraw Online", "http://mangaraw.online"
// SourceData("ja", "Mangazuki RAWS", "https://raws.mangazuki.co"),
// SourceData("ja", "MangaRAW", "https://www.mgraw.com"),
// SourceData("ja", "マンガ/漫画 マガジン/雑誌 raw", "http://netabare-manga-raw.com"),
// SourceData("ru", "NAKAMA", "http://nakama.ru"),
// SourceData("tr", "MangAoi", "http://mangaoi.com"),
// SourceData("tr", "ManhuaTR", "http://www.manhua-tr.com"),
