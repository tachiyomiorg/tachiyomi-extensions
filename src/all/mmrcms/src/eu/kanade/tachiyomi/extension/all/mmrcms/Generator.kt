package eu.kanade.tachiyomi.extension.all.mmrcms

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.os.Build
import com.google.gson.Gson
import java.io.File
import java.io.PrintWriter
import java.security.cert.CertificateException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * This class generates the sources for MMRCMS.
 * Credit to nulldev for writing the original shell script
 *
 * CMS: https://getcyberworks.com/product/manga-reader-cms/
 */

class Generator {

    private var preRunTotal: String

    init {
        System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2,TLSv1.3")
        preRunTotal = Regex("""MMRSOURCE_(\d+)""").findAll(File(relativePath).readText(Charsets.UTF_8)).last().groupValues[1]
    }

    data class SourceData(val lang: String, val name: String, val baseUrl: String, val isNsfw: Boolean = false)

    @TargetApi(Build.VERSION_CODES.O)
    fun generate() {
        val buffer = StringBuffer()
        val dateTime = ZonedDateTime.now()
        val formattedDate = dateTime.format(DateTimeFormatter.RFC_1123_DATE_TIME)
        buffer.append("package eu.kanade.tachiyomi.extension.all.mmrcms")
        buffer.append("\n\n// GENERATED FILE, DO NOT MODIFY!\n// Generated $formattedDate\n\n")
        var number = 1
        sources.forEach {
            try {
                val map = mutableMapOf<String, Any>()
                map["language"] = it.lang
                map["name"] = it.name
                map["base_url"] = it.baseUrl
                map["supports_latest"] = supportsLatest(it.baseUrl)
                map["isNsfw"] = it.isNsfw

                val advancedSearchDocument = getDocument("${it.baseUrl}/advanced-search", false)

                var parseCategories = mutableListOf<Map<String, String>>()
                if (advancedSearchDocument != null) {
                    parseCategories = parseCategories(advancedSearchDocument)
                }

                val homePageDocument = getDocument(it.baseUrl)

                val itemUrl = getItemUrl(homePageDocument, it.baseUrl)

                var prefix = itemUrl.substringAfterLast("/").substringBeforeLast("/")

                // Sometimes itemUrl is the root of the website, and thus the prefix found is the website address.
                // In this case, we set the default prefix as "manga".
                if (prefix.startsWith("www") || prefix.startsWith("wwv")) {
                    prefix = "manga"
                }

                val mangaListDocument = getDocument("${it.baseUrl}/$prefix-list")!!

                if (parseCategories.isEmpty()) {
                    parseCategories = parseCategories(mangaListDocument)
                }
                map["item_url"] = "$itemUrl/"
                map["categories"] = parseCategories
                val tags = parseTags(mangaListDocument)
                map["tags"] = "null"
                if (tags.size in 1..49) {
                    map["tags"] = tags
                }

                if (!itemUrl.startsWith(it.baseUrl)) println("**Note: ${it.name} URL does not match! Check for changes: \n ${it.baseUrl} vs $itemUrl")

                val toJson = Gson().toJson(map)

                buffer.append("private const val MMRSOURCE_$number = \"\"\"$toJson\"\"\"\n")
                number++
            } catch (e: Exception) {
                println("error generating source ${it.name} ${e.printStackTrace()}")
            }
        }

        buffer.append("val SOURCES: List<String> get() = listOf(")
        for (i in 1 until number) {
            buffer.append("MMRSOURCE_$i")
            when (i) {
                number - 1 -> {
                    buffer.append(")\n")
                }
                else -> {
                    buffer.append(", ")
                }
            }
        }
        println("Pre-run sources: $preRunTotal")
        println("Post-run sources: ${number - 1}")
        val writer = PrintWriter(relativePath)
        writer.write(buffer.toString())
        writer.close()
    }

    private fun getDocument(url: String, printStackTrace: Boolean = true): Document? {
        val serverCheck = arrayOf("cloudflare-nginx", "cloudflare")

        try {
            val request = Request.Builder().url(url)
            getOkHttpClient().newCall(request.build()).execute().let { response ->
                // Bypass Cloudflare ("Please wait 5 seconds" page)
                if (response.code() == 503 && response.header("Server") in serverCheck) {
                    var cookie = "${response.header("Set-Cookie")!!.substringBefore(";")}; "
                    Jsoup.parse(response.body()!!.string()).let { document ->
                        val path = document.select("[id=\"challenge-form\"]").attr("action")
                        val chk = document.select("[name=\"s\"]").attr("value")
                        getOkHttpClient().newCall(Request.Builder().url("$url/$path?s=$chk").build()).execute().let { solved ->
                            cookie += solved.header("Set-Cookie")!!.substringBefore(";")
                            request.addHeader("Cookie", cookie).build().let {
                                return Jsoup.parse(getOkHttpClient().newCall(it).execute().body()?.string())
                            }
                        }
                    }
                }
                if (response.code() == 200) {
                    return Jsoup.parse(response.body()?.string())
                }
            }
        } catch (e: Exception) {
            if (printStackTrace) {
                e.printStackTrace()
            }
        }
        return null
    }

    private fun parseTags(mangaListDocument: Document): MutableList<Map<String, String>> {
        val elements = mangaListDocument.select("div.tag-links a")

        if (elements.isEmpty()) {
            return mutableListOf()
        }
        val array = mutableListOf<Map<String, String>>()
        elements.forEach {
            val map = mutableMapOf<String, String>()
            map["id"] = it.attr("href").substringAfterLast("/")
            map["name"] = it.text()
            array.add(map)
        }
        return array
    }

    private fun getItemUrl(document: Document?, url: String): String {
        document ?: throw Exception("Couldn't get document for: $url")
        return document.toString().substringAfter("showURL = \"").substringAfter("showURL=\"").substringBefore("/SELECTION\";")

        // Some websites like mangasyuri use javascript minifiers, and thus "showURL = " becomes "showURL="https://mangasyuri.net/manga/SELECTION""
        // (without spaces). Hence the double substringAfter.
    }

    private fun supportsLatest(third: String): Boolean {
        val document = getDocument("$third/latest-release?page=1", false) ?: return false
        return document.select("div.mangalist div.manga-item a, div.grid-manga tr").isNotEmpty()
    }

    private fun parseCategories(document: Document): MutableList<Map<String, String>> {
        val array = mutableListOf<Map<String, String>>()
        val elements = document.select("select[name^=categories] option, a.category")
        if (elements.size == 0) {
            return mutableListOf()
        }
        var id = 1
        elements.forEach {
            val map = mutableMapOf<String, String>()
            map["id"] = id.toString()
            map["name"] = it.text()
            array.add(map)
            id++
        }
        return array
    }

    @Throws(Exception::class)
    private fun getOkHttpClient(): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            @SuppressLint("TrustAllX509TrustManager")
            @Throws(CertificateException::class)
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
            }

            @SuppressLint("TrustAllX509TrustManager")
            @Throws(CertificateException::class)
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
            }

            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> {
                return arrayOf()
            }
        })

        // Install the all-trusting trust manager

        val sc = SSLContext.getInstance("SSL")
        sc.init(null, trustAllCerts, java.security.SecureRandom())
        val sslSocketFactory = sc.socketFactory

        // Create all-trusting host name verifier
        // Install the all-trusting host verifier

        return OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(1, TimeUnit.MINUTES)
            .readTimeout(1, TimeUnit.MINUTES)
            .writeTimeout(1, TimeUnit.MINUTES)
            .build()
    }

    companion object {
        val sources = listOf(
            SourceData("ar", "مانجا اون لاين", "https://onma.me"),
            SourceData("en", "Read Comics Online", "https://readcomicsonline.ru"),
            SourceData("en", "Biamam Scans", "https://biamam.com"),
            SourceData("en", "Fallen Angels", "https://manga.fascans.com"),
            SourceData("en", "White Cloud Pavilion", "https://www.whitecloudpavilion.com/manga/free"),
            SourceData("fr", "Scan FR", "https://www.scan-fr.co"),
            SourceData("fr", "Scan VF", "https://www.scan-vf.net"),
            SourceData("fr", "Scan OP", "https://scan-op.com"),
            SourceData("id", "Komikid", "https://www.komikid.com"),
            SourceData("pl", "ToraScans", "http://torascans.pl"),
            SourceData("pt-BR", "Comic Space", "https://www.comicspace.com.br"),
            SourceData("pt-BR", "Mangás Yuri", "https://mangasyuri.net"),
            SourceData("pl", "Dracaena", "https://dracaena.webd.pl/czytnik"),
            SourceData("pl", "Nikushima", "http://azbivo.webd.pro"),
            SourceData("tr", "MangaHanta", "http://mangahanta.com"),
            SourceData("vi", "Fallen Angels Scans", "https://truyen.fascans.com"),
            SourceData("es", "LeoManga", "https://leomanga.me"),
            SourceData("es", "submanga", "https://submangas.net"),
            SourceData("es", "Mangadoor", "https://mangadoor.com"),
            SourceData("es", "Mangas.pw", "https://mangas.in"),
            SourceData("es", "Tumangaonline.co", "http://tumangaonline.uno"),
            SourceData("bg", "Utsukushii", "https://manga.utsukushii-bg.com"),
            SourceData("es", "Universo Yuri", "https://universoyuri.com"),
            SourceData("pl", "Phoenix-Scans", "https://phoenix-scans.pl"),
            SourceData("ru", "Japit Comics", "https://j-comics.ru"),
            SourceData("tr", "Puzzmos", "https://puzzmos.com"),
            SourceData("fr", "Scan-1", "https://wwv.scan-1.com"),
            SourceData("fr", "Lelscan-VF", "https://www.lelscan-vf.com"),
            SourceData("id", "MangaSusu", "https://www.mangasusu.site"),
            SourceData("id", "Komik Manga", "https://adm.komikmanga.com"),
            SourceData("ko", "Mangazuki Raws", "https://raws.mangazuki.co"),
            SourceData("pt-BR", "Remangas", "https://remangas.top"),
            SourceData("pt-BR", "AnimaRegia", "https://animaregia.net"),
            SourceData("tr", "NoxSubs", "https://noxsubs.com"),
            SourceData("id", "MangaYu", "https://mangayu.com"),
            SourceData("tr", "MangaVadisi", "http://manga-v2.mangavadisi.org"),
            SourceData("id", "MangaID", "https://mangaid.click"),
            SourceData("fr", "Jpmangas", "https://www.jpmangas.com"),
            SourceData("fr", "Op-VF", "https://www.op-vf.com"),
            SourceData("fr", "FR Scan", "https://www.frscan.me"),
            // NOTE: THIS SOURCE CONTAINS A CUSTOM LANGUAGE SYSTEM (which will be ignored)!
            SourceData("other", "HentaiShark", "https://www.hentaishark.com", true))
            // Changed CMS
            // SourceData("en", "MangaTreat Scans", "http://www.mangatreat.com"),
            // SourceData("en", "Chibi Manga Reader", "https://www.cmreader.info"),
            // SourceData("tr", "Epikmanga", "https://www.epikmanga.com"),
            // SourceData("en", "Hatigarm Scans", "https://hatigarmscans.net"),
            // Went offline
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

        val relativePath = System.getProperty("user.dir") + "/src/all/mmrcms/src/eu/kanade/tachiyomi/extension/all/mmrcms/GeneratedSources.kt"

        @JvmStatic
        fun main(args: Array<String>) {
            Generator().generate()
        }
    }
}
