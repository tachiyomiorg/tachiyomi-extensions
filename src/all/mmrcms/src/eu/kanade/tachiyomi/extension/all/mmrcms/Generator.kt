package eu.kanade.tachiyomi.extension.all.mmrcms

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
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * This class generates the sources for MMRCMS.
 * Credit to nulldev for writing the original shell script
 *
# CMS: https://getcyberworks.com/product/manga-reader-cms/
 */

class Generator {

    @TargetApi(Build.VERSION_CODES.O)
    fun generate() {
        val buffer = StringBuffer()
        val dateTime = ZonedDateTime.now()
        val formattedDate = dateTime.format(DateTimeFormatter.RFC_1123_DATE_TIME)
        buffer.append("package eu.kanade.tachiyomi.extension.all.mmrcms")
        buffer.append("\n\n// GENERATED FILE, DO NOT MODIFY!\n//Generated $formattedDate\n\n")
        var number = 1
        sources.forEach {
            try {
                var map = mutableMapOf<String, Any>()
                map["language"] = it.first
                map["name"] = it.second
                map["base_url"] = it.third
                map["supports_latest"] = supportsLatest(it.third)

                val advancedSearchDocument = getDocument("${it.third}/advanced-search", false)

                var parseCategories = mutableListOf<Map<String, String>>()
                if (advancedSearchDocument != null) {
                    parseCategories = parseCategories(advancedSearchDocument)
                }

                val homePageDocument = getDocument("${it.third}")!!

                val itemUrl = getItemUrl(homePageDocument)

                var prefix = itemUrl.substringAfterLast("/").substringBeforeLast("/")

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
        try {

            val response = getOkHttpClient().newCall(Request.Builder().url(url).build()).execute()
            if (response.code() == 200) {
                return Jsoup.parse(response.body()?.string())
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
        var array = mutableListOf<Map<String, String>>()
        elements.forEach {
            var map = mutableMapOf<String, String>()
            map["id"] = it.attr("href").substringAfterLast("/")
            map["name"] = it.text()
            array.add(map)
        }
        return array

    }

    private fun getItemUrl(document: Document): String {
        return document.toString().substringAfter("showURL = \"").substringBefore("/SELECTION\";")
    }

    private fun supportsLatest(third: String): Boolean {
        getDocument("$third/filterList?page=1&sortBy=last_release&asc=false", false) ?: return false
        return true
    }

    private fun parseCategories(document: Document): MutableList<Map<String, String>> {
        var array = mutableListOf<Map<String, String>>()
        var elements = document.select("select[name^=categories] option")
        if (elements.size == 0) {
            return mutableListOf()
        }
        var id = 1
        elements.forEach {
            var map = mutableMapOf<String, String>()
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
            @Throws(CertificateException::class)
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
            }

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

        val builder = OkHttpClient.Builder()
        builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
        builder.hostnameVerifier { _, _ -> true }
        return builder.build()
    }


    companion object {
        const val DRY_RUN = false
        val sources = listOf(
                Triple("ar", "مانجا اون لاين", "http://www.on-manga.com"),
                //Went offline
                //Triple("ar", "Manga FYI", "http://mangafyi.com/manga/arabic"
                Triple("en", "Read Comics Online", "http://readcomicsonline.ru"),
                Triple("en", "Fallen Angels", "http://manga.fascans.com"),
                //Went offline
                //Triple("en", "MangaRoot", "http://mangaroot.com"),
                Triple("en", "Mangawww Reader", "http://mangawww.club"),
                //Went offline
                //Triple("en", "MangaForLife", "http://manga4ever.com"),
                //Went offline
                //Triple("en", "Manga Spoil", "http://mangaspoil.com"),
                Triple("en", "MangaBlue", "http://mangablue.com"),
                //Went offline
                //Triple("en", "Manga Forest", "https://mangaforest.com"),
                //Went offline
                //Triple("en", "DManga", "http://dmanga.website"
                Triple("en", "Chibi Manga Reader", "http://www.cmreader.info"),
                Triple("en", "ZXComic", "http://zxcomic.com"),
                //Went offline
                //Triple("en", "DB Manga", "http://dbmanga.com"),
                //Went offline
                //Triple("en", "Mangacox", "http://mangacox.com"),

                //Protected by CloudFlare
                //Triple("en", "GO Manhwa", "http://gomanhwa.xyz"
                //Went offline
                //Triple("en", "KoManga", "https://komanga.net"
                //Went offline
                //Triple("en", "Manganimecan", "http://manganimecan.com"),
                //Went offline
                //Triple("en", "Hentai2Manga", "http://hentai2manga.com"),
                Triple("en", "White Cloud Pavilion", "http://www.whitecloudpavilion.com/manga/free"),
                //Went offline
                //Triple("en", "4 Manga", "http://4-manga.com"),
                Triple("en", "XYXX.INFO", "http://xyxx.info"),
                Triple("en", "MangaTreat Scans", "http://www.mangatreat.com"),
                Triple("en", "Isekai Manga Reader", "https://isekaimanga.club"),
                Triple("es", "My-mangas.com", "https://my-mangas.com"),
                Triple("es", "SOS Scanlation", "https://sosscanlation.com"),
                //Went offline
                //Triple("fa", "TrinityReader", "http://trinityreader.pw"
                Triple("fr", "Manga-LEL", "https://www.manga-lel.com"),
                Triple("fr", "Manga Etonnia", "https://www.etonnia.com"),
                Triple("fr", "Scan FR", "http://www.scan-fr.io"),
                //Went offline
                //Triple("fr", "ScanFR.com"),, "http://scanfr.com"),
                //Went offline
                //Triple("fr", "Manga FYI", "http://mangafyi.com/manga/french"
                //Went offline
                //Triple("fr", "scans-manga", "http://scans-manga.com"),
                Triple("fr", "Henka no Kaze", "http://henkanokazelel.esy.es/upload"),
                //Went offline
                //Triple("fr", "Tous Vos Scans", "http://www.tous-vos-scans.com"),
                //Went offline
                //Triple("id", "Manga Desu", "http://mangadesu.net"
                //Went offline
                //Triple("id", "Komik Mangafire.ID", "http://go.mangafire.id"
                Triple("id", "MangaOnline", "https://mangaonline.web.id"),
                //Went offline
                //Triple("id", "MangaNesia", "https://manganesia.com"),
                Triple("id", "Komikid", "http://www.komikid.com"),
                //Now uses wpmanga
                //Triple("id", "MangaID", "https://mangaid.me"
                //Went offline
                //Triple("id", "Manga Seru", "http://www.mangaseru.top"
                //Went offline
                //Triple("id", "Manga FYI", "http://mangafyi.com/manga/indonesian"
                Triple("id", "Bacamangaku", "http://www.bacamangaku.com"),
                //Went offline
                //Triple("id", "Indo Manga Reader", "http://indomangareader.com"),
                //Protected by Cloudflare
                //Triple("it", "Kingdom Italia Reader", "http://kireader.altervista.org"),
                //Went offline
                //Triple("ja", "IchigoBook", "http://ichigobook.com"),
                //Went offline
                //Triple("ja", "Mangaraw Online", "http://mangaraw.online"
                Triple("ja", "Mangazuki RAWS", "https://raws.mangazuki.co"),
                Triple("ja", "RAW MANGA READER", "https://rawmanga.site"),
                //Went offline
                //Triple("ja", "MangaRAW", "https://www.mgraw.com"),
                Triple("ja", "マンガ/漫画 マガジン/雑誌 raw", "http://netabare-manga-raw.com"),
                Triple("pl", "ToraScans", "http://torascans.pl"),
                Triple("pt", "Comic Space", "https://www.comicspace.com.br"),
                Triple("pt", "Mangás Yuri", "https://mangasyuri.net"),
                Triple("pl", "Dracaena", "http://dracaena.webd.pl/czytnik"),
                Triple("pl", "Nikushima", "http://azbivo.webd.pro"),
                Triple("ru", "NAKAMA", "http://nakama.ru"),
                Triple("ru", "Anigai clan", "http://anigai.ru"),
                //Went offline
                //Triple("tr", "MangAoi", "http://mangaoi.com"),
                Triple("tr", "MangaHanta", "http://mangahanta.com"),
                //WEnt offline
                //Triple("tr", "ManhuaTR", "http://www.manhua-tr.com"),
                Triple("vi", "Fallen Angels Scans", "http://truyen.fascans.com"),
                //Blocks bots (like this one)
                //Triple("tr", "Epikmanga", "http://www.epikmanga.com"),
                //NOTE: THIS SOURCE CONTAINS A CUSTOM LANGUAGE SYSTEM (which will be ignored)!
                Triple("other", "HentaiShark", "https://www.hentaishark.com"))

        val relativePath = System.getProperty("user.dir") + "/src/all/mmrcms/src/eu/kanade/tachiyomi/extension/all/mmrcms/GeneratedSources.kt"
        val relativePathTest = System.getProperty("user.dir") + "/src/all/mmrcms/TestGeneratedSources.kt"


        @JvmStatic
        fun main(args: Array<String>) {
            Generator().generate()
        }
    }
}