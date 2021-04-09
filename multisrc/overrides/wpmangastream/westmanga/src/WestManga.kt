package eu.kanade.tachiyomi.extension.id.westmanga

import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient


class WestManga : WPMangaStream("West Manga", "https://westmanga.info", "id") {
    // Formerly "West Manga (WP Manga Stream)"
    override val id = 8883916630998758688

    private val rateLimitInterceptor = RateLimitInterceptor(4)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            document.select(".seriestucontent").firstOrNull()?.let { infoElement ->
                genre = infoElement.select(".seriestugenre a").joinToString { it.text() }
                status = parseStatus(infoElement.select(".infotable tr:contains(Status) td:last-child").firstOrNull()?.ownText())
                author = infoElement.select(".infotable tr:contains(Author) td:last-child").firstOrNull()?.ownText()
                description = infoElement.select(".entry-content-single[itemprop=\"description\"]").joinToString("\n") { it.text() }
                thumbnail_url = infoElement.select("div.thumb img").imgAttr()

                // add series type(manga/manhwa/manhua/other) thinggy to genre
                document.select(seriesTypeSelector).firstOrNull()?.ownText()?.let {
                    if (it.isEmpty().not() && genre!!.contains(it, true).not()) {
                        genre += if (genre!!.isEmpty()) it else ", $it"
                    }
                }

                // add alternative name to manga description
                document.select(altNameSelector).firstOrNull()?.ownText()?.let {
                    if (it.isEmpty().not() && it !="N/A" && it != "-") {
                        description += when {
                            description!!.isEmpty() -> altName + it
                            else -> "\n\n$altName" + it
                        }
                    }
                }
            }
        }
    }

    override val seriesTypeSelector = ".infotable tr:contains(Type) td:last-child"
    override fun getGenreList(): List<Genre> = listOf(
        Genre("4 Koma", "344"),
        Genre("Action", "13"),
        Genre("Adventure", "4"),
        Genre("Anthology", "1494"),
        Genre("Comedy", "5"),
        Genre("Cooking", "54"),
        Genre("Crime", "856"),
        Genre("Crossdressing", "1306"),
        Genre("Demon", "64"),
        Genre("Drama", "6"),
        Genre("Ecchi", "14"),
        Genre("Fantasy", "7"),
        Genre("Game", "36"),
        Genre("Gender Bender", "149"),
        Genre("Genderswap", "157"),
        Genre("Gore", "56"),
        Genre("Gyaru", "812"),
        Genre("Harem", "17"),
        Genre("Historical", "44"),
        Genre("Horror", "211"),
        Genre("Isekai", "20"),
        Genre("Isekai Action", "742"),
        Genre("Josei", "164"),
        Genre("Magic", "65"),
        Genre("Manga", "268"),
        Genre("Manhua", "32"),
        Genre("Martial Art", "754"),
        Genre("Martial Arts", "8"),
        Genre("Mature", "46"),
        Genre("Mecha", "22"),
        Genre("Medical", "704"),
        Genre("Medy", "1439"),
        Genre("Monsters", "91"),
        Genre("Music", "457"),
        Genre("Mystery", "30"),
        Genre("Office Workers", "1501"),
        Genre("Oneshot", "405"),
        Genre("Project", "313"),
        Genre("Psychological", "23"),
        Genre("Reincarnation", "57"),
        Genre("Reinkarnasi", "1170"),
        Genre("Romance", "15"),
        Genre("School", "102"),
        Genre("School Life", "9"),
        Genre("Sci-fi", "33"),
        Genre("Seinen", "18"),
        Genre("Shotacon", "1070"),
        Genre("Shoujo", "110"),
        Genre("Shoujo Ai", "113"),
        Genre("Shounen", "10"),
        Genre("Shounen Ai", "shounen-ai"),
        Genre("Si-fi", "776"),
        Genre("Slice of Lif", "773"),
        Genre("Slice of Life", "11"),
        Genre("Smut", "586"),
        Genre("Sports", "103"),
        Genre("Super Power", "274"),
        Genre("Supernatural", "34"),
        Genre("Suspense", "181"),
        Genre("Thriller", "170"),
        Genre("Tragedy", "92"),
        Genre("Urban", "1050"),
        Genre("Vampire", "160"),
        Genre("Video Games", "1093"),
        Genre("Webtoons", "486"),
        Genre("Yaoi", "yaoi"),
        Genre("Zombies", "377")
    )
}
