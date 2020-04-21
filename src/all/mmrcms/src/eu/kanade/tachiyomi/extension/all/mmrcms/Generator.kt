package eu.kanade.tachiyomi.extension.all.mmrcms

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.os.Build
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.PrintWriter
import java.security.cert.CertificateException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * This class generates the sources for MMRCMS.
 * Credit to nulldev for writing the original shell script
 *
 * CMS: https://getcyberworks.com/product/manga-reader-cms/
 */

class Generator {

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
                map["language"] = it.first
                map["name"] = it.second
                map["base_url"] = it.third
                map["supports_latest"] = supportsLatest(it.third)

                val advancedSearchDocument = getDocument("${it.third}/advanced-search", false)

                var parseCategories = mutableListOf<Map<String, String>>()
                if (advancedSearchDocument != null) {
                    parseCategories = parseCategories(advancedSearchDocument)
                }

                val homePageDocument = getDocument(it.third)!!

                val itemUrl = getItemUrl(homePageDocument)

                var prefix = itemUrl.substringAfterLast("/").substringBeforeLast("/")

                //Sometimes itemUrl is the root of the website, and thus the prefix found is the website address.
                // In this case, we set the default prefix as "manga".
                if (prefix.startsWith("www")){
                    prefix="manga"
                }

                val mangaListDocument = getDocument("${it.third}/$prefix-list")!!

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

                if (!itemUrl.startsWith(it.third)) println("**Note: ${it.second} URL does not match! Check for changes: \n ${it.third} vs $itemUrl")

                val toJson = Gson().toJson(map)


                buffer.append("private const val MMRSOURCE_$number = \"\"\"$toJson\"\"\"\n")
                number++

            } catch (e: Exception) {
                println("error generating source ${it.second} ${e.printStackTrace()}")
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
        println("Number of sources successfully generated: ${number - 1}")
        if (!DRY_RUN) {
            val writer = PrintWriter(relativePath)
            writer.write(buffer.toString())
            writer.close()

        } else {
            val writer = PrintWriter(relativePathTest)
            writer.write(buffer.toString())
            writer.close()
        }
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

    private fun getItemUrl(document: Document): String {
        return document.toString().substringAfter("showURL = \"").substringAfter("showURL=\"").substringBefore("/SELECTION\";")

        //Some websites like mangasyuri use javascript minifiers, and thus "showURL = " becomes "showURL="https://mangasyuri.net/manga/SELECTION""
        //(without spaces). Hence the double substringAfter.
    }

    private fun supportsLatest(third: String): Boolean {
        val document = getDocument("$third/latest-release?page=1", false) ?: return false
        return document.select("div.mangalist div.manga-item a").isNotEmpty()
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
        const val DRY_RUN = false
        val sources = listOf(
            Triple("ar", "مانجا اون لاين", "https://onma.me"),
            Triple("en", "Read Comics Online", "https://readcomicsonline.ru"),
            Triple("en", "Biamam Scans", "https://biamam.com/"),
            Triple("en", "Fallen Angels", "https://manga.fascans.com"),
            Triple("en", "Mangawww Reader", "https://mangawww.club"),
            Triple("en", "White Cloud Pavilion", "https://www.whitecloudpavilion.com/manga/free"),
            Triple("fr", "Scan FR", "https://www.scan-fr.co"),
            Triple("fr", "Scan VF", "https://www.scan-vf.net"),
            Triple("fr", "Scan OP","https://scan-op.com"),
            Triple("id", "Komikid", "https://www.komikid.com"),
            Triple("pl", "ToraScans", "http://torascans.pl"),
            Triple("pt", "Comic Space", "https://www.comicspace.com.br"),
            Triple("pt", "Mangás Yuri", "https://mangasyuri.net"),
            Triple("pl", "Dracaena", "https://dracaena.webd.pl/czytnik"),
            Triple("pl", "Nikushima", "http://azbivo.webd.pro"),
            Triple("tr", "MangaHanta", "http://mangahanta.com"),
            Triple("vi", "Fallen Angels Scans", "https://truyen.fascans.com"),
            Triple("es", "LeoManga", "https://leomanga.me"),
            Triple("es", "submanga", "https://submanga.li"),
            Triple("es", "Mangadoor", "https://mangadoor.com"),
            Triple("es", "Mangas.pw", "https://mangas.in"),
            Triple("es", "Tumangaonline.co", "http://tumangaonline.uno"),
            Triple("bg", "Utsukushii", "https://manga.utsukushii-bg.com"),
            Triple("es", "Universo Yuri", "https://universoyuri.com"),
            Triple("pl", "Phoenix-Scans", "https://phoenix-scans.pl"),
            Triple("ru", "Japit Comics","https://j-comics.ru"),
            Triple("tr", "Puzzmos", "https://puzzmos.com"),
            Triple("fr", "Scan-1", "https://www.scan-1.com"),
            Triple("fr", "Lelscan-VF", "https://www.lelscan-vf.com"),
            //NOTE: THIS SOURCE CONTAINS A CUSTOM LANGUAGE SYSTEM (which will be ignored)!
            Triple("other", "HentaiShark", "https://www.hentaishark.com"))
            //Changed CMS
            //Triple("en", "MangaTreat Scans", "http://www.mangatreat.com"),
            //Triple("en", "Chibi Manga Reader", "https://www.cmreader.info"),
            //Triple("tr", "Epikmanga", "https://www.epikmanga.com"),
            //Triple("en", "Hatigarm Scans", "https://hatigarmscans.net"),
            //Went offline
            //Triple("ru", "Anigai clan", "http://anigai.ru"),
            //Triple("en", "ZXComic", "http://zxcomic.com"),
            //Triple("es", "SOS Scanlation", "https://sosscanlation.com"),
            //Triple("es", "MangaCasa", "https://mangacasa.com"))
            //Triple("ja", "RAW MANGA READER", "https://rawmanga.site"),
            //Triple("ar", "Manga FYI", "http://mangafyi.com/manga/arabic"),
            //Triple("en", "MangaRoot", "http://mangaroot.com"),
            //Triple("en", "MangaForLife", "http://manga4ever.com"),
            //Triple("en", "Manga Spoil", "http://mangaspoil.com"),
            //Triple("en", "MangaBlue", "http://mangablue.com"),
            //Triple("en", "Manga Forest", "https://mangaforest.com"),
            //Triple("en", "DManga", "http://dmanga.website"),
            //Triple("en", "DB Manga", "http://dbmanga.com"),
            //Triple("en", "Mangacox", "http://mangacox.com"),
            //Triple("en", "GO Manhwa", "http://gomanhwa.xyz"),
            //Triple("en", "KoManga", "https://komanga.net"),
            //Triple("en", "Manganimecan", "http://manganimecan.com"),
            //Triple("en", "Hentai2Manga", "http://hentai2manga.com"),
            //Triple("en", "4 Manga", "http://4-manga.com"),
            //Triple("en", "XYXX.INFO", "http://xyxx.info"),
            //Triple("en", "Isekai Manga Reader", "https://isekaimanga.club"),
            //Triple("fa", "TrinityReader", "http://trinityreader.pw"),
            //Triple("fr", "Manga-LEL", "https://www.manga-lel.com"),
            //Triple("fr", "Manga Etonnia", "https://www.etonnia.com"),
            //Triple("fr", "ScanFR.com"), "http://scanfr.com"),
            //Triple("fr", "Manga FYI", "http://mangafyi.com/manga/french"),
            //Triple("fr", "scans-manga", "http://scans-manga.com"),
            //Triple("fr", "Henka no Kaze", "http://henkanokazelel.esy.es/upload"),
            //Triple("fr", "Tous Vos Scans", "http://www.tous-vos-scans.com"),
            //Triple("id", "Manga Desu", "http://mangadesu.net"),
            //Triple("id", "Komik Mangafire.ID", "http://go.mangafire.id"),
            //Triple("id", "MangaOnline", "https://mangaonline.web.id"),
            //Triple("id", "MangaNesia", "https://manganesia.com"),
            //Triple("id", "MangaID", "https://mangaid.me"
            //Triple("id", "Manga Seru", "http://www.mangaseru.top"
            //Triple("id", "Manga FYI", "http://mangafyi.com/manga/indonesian"
            //Triple("id", "Bacamangaku", "http://www.bacamangaku.com"),
            //Triple("id", "Indo Manga Reader", "http://indomangareader.com"),
            //Triple("it", "Kingdom Italia Reader", "http://kireader.altervista.org"),
            //Triple("ja", "IchigoBook", "http://ichigobook.com"),
            //Triple("ja", "Mangaraw Online", "http://mangaraw.online"
            //Triple("ja", "Mangazuki RAWS", "https://raws.mangazuki.co"),
            //Triple("ja", "MangaRAW", "https://www.mgraw.com"),
            //Triple("ja", "マンガ/漫画 マガジン/雑誌 raw", "http://netabare-manga-raw.com"),
            //Triple("ru", "NAKAMA", "http://nakama.ru"),
            //Triple("tr", "MangAoi", "http://mangaoi.com"),
            //Triple("tr", "ManhuaTR", "http://www.manhua-tr.com"),

        val relativePath = System.getProperty("user.dir") + "/src/all/mmrcms/src/eu/kanade/tachiyomi/extension/all/mmrcms/GeneratedSources.kt"
        val relativePathTest = System.getProperty("user.dir") + "/src/all/mmrcms/TestGeneratedSources.kt"


        @JvmStatic
        fun main(args: Array<String>) {
            Generator().generate()
        }
    }
}
