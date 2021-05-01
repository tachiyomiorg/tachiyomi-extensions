package eu.kanade.tachiyomi.multisrc.mmrcms

import java.util.Locale


class MMRCMSSources {
    companion object {
        sealed class SourceData {
            abstract val name: String
            abstract val baseUrl: String
            abstract val isNsfw: Boolean
            abstract val className: String
            abstract val pkgName: String
            abstract val overrideVersionCode: Int

            data class Single(
                override val name: String,
                override val baseUrl: String,
                val lang: String,
                override val isNsfw: Boolean = false,
                override val className: String = name.replace(" ", ""),
                override val pkgName: String = className.toLowerCase(Locale.ENGLISH),
                override val overrideVersionCode: Int = 0,
            ) : SourceData()

            data class Multi(
                override val name: String,
                override val baseUrl: String,
                val langs: List<String>,
                override val isNsfw: Boolean = false,
                override val className: String = name.replace(" ", "") + "Factory",
                override val pkgName: String = className.substringBefore("Factory").toLowerCase(Locale.ENGLISH),
                override val overrideVersionCode: Int = 0,
            ) : SourceData()
        }
        val version: Int = 3
        val sourceList: List<SourceData.Single> = listOf(
            SourceData.Single("مانجا اون لاين", "https://onma.me", "ar", className = "onma"),
            SourceData.Single("Read Comics Online", "https://readcomicsonline.ru", "en"),
            SourceData.Single("Fallen Angels", "https://manga.fascans.com", "en"),
            SourceData.Single("Zahard", "https://zahard.top", "en"),
            SourceData.Single("Manhwas Men", "https://manhwas.men", "en"),
            SourceData.Single("Scan FR", "https://www.scan-fr.cc", "fr"),
            SourceData.Single("Scan VF", "https://www.scan-vf.net", "fr"),
            SourceData.Single("Scan OP", "https://scan-op.cc", "fr"),
            SourceData.Single("Komikid", "https://www.komikid.com", "id"),
            SourceData.Single("Nikushima", "http://azbivo.webd.pro", "pl"),
            SourceData.Single("MangaHanta", "http://mangahanta.com", "tr"),
            SourceData.Single("Fallen Angels Scans", "https://truyen.fascans.com", "vi"),
            SourceData.Single("LeoManga", "https://leomanga.me", "es"),
            SourceData.Single("submanga", "https://submanga.io", "es"),
            SourceData.Single("Mangadoor", "https://mangadoor.com", "es"),
            SourceData.Single("Mangas.pw", "https://mangas.in", "es", className = "MangasPw"),
            SourceData.Single("Utsukushii", "https://manga.utsukushii-bg.com", "bg"),
            SourceData.Single("Phoenix-Scans", "https://phoenix-scans.pl", "pl", className = "PhoenixScans"),
            SourceData.Single("Puzzmos", "https://puzzmos.com", "tr"),
            SourceData.Single("Scan-1", "https://wwv.scan-1.com", "fr", className = "ScanOne"),
            SourceData.Single("Lelscan-VF", "https://lelscan-vf.co", "fr", className = "LelscanVF"),
            SourceData.Single("Komik Manga", "https://adm.komikmanga.com", "id"),
            SourceData.Single("Mangazuki Raws", "https://raws.mangazuki.co", "ko"),
            SourceData.Single("Mangazuki", "https://mangazuki.co", "en"),
            SourceData.Single("Remangas", "https://remangas.top", "pt-BR"),
            SourceData.Single("AnimaRegia", "https://animaregia.net", "pt-BR"),
            SourceData.Single("MangaVadisi", "http://manga-v2.mangavadisi.org", "tr"),
            SourceData.Single("MangaID", "https://mangaid.click", "id"),
            SourceData.Single("Jpmangas", "https://jpmangas.co", "fr"),
            SourceData.Single("Op-VF", "https://www.op-vf.com", "fr", className = "OpVF"),
            SourceData.Single("FR Scan", "https://frscan.cc", "fr"),
            SourceData.Single("GekkouScan", "http://leitor.gekkouscans.com.br", "pt-BR"),
            // NOTE: THIS SOURCE CONTAINS A CUSTOM LANGUAGE SYSTEM (which will be ignored)!
            SourceData.Single("HentaiShark", "https://www.hentaishark.com", "all", isNsfw = true),
            //MultiLang("HentaiShark", "https://www.hentaishark.com", listOf("en", "ja", "zh", "de", "nl", "ko", "cz", "eo", "mn", "ar", "sk", "la", "ua", "ceb", "tl", "fi", "bg", "tr"), isNsfw = true, className = "HentaiSharkFactory"),
        )
    }
}



//SingleLang("Mangás Yuri", "https://mangasyuri.net", "pt-BR", className = "MangasYuri"), override val id: Long = 6456162511058446409
//SingleLang("FR Scan", "https://www.frscan.me", "fr"),
// Reference from old Factory Source
// Changed CMS
// SourceData("es", "Tumangaonline.co", "http://tumangaonline.com"),
// SourceData("id", "MangaYu", "https://mangayu.com"),
// SourceData("en", "MangaTreat Scans", "http://www.mangatreat.com"),
// SourceData("en", "Chibi Manga Reader", "https://www.cmreader.info"),
// SourceData("tr", "Epikmanga", "https://www.epikmanga.com"),
// SourceData("en", "Hatigarm Scans", "https://hatigarmscans.net"),
// Went offline
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
