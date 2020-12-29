package eu.kanade.tachiyomi.extension.ar.gmanga

import android.annotation.SuppressLint
import com.github.salomonbrys.kotson.addAll
import com.github.salomonbrys.kotson.addProperty
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.nullString
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.Filter.TriState.Companion.STATE_EXCLUDE
import eu.kanade.tachiyomi.source.model.Filter.TriState.Companion.STATE_INCLUDE
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.lang.Exception
import java.text.ParseException
import java.text.SimpleDateFormat

class Gmanga : HttpSource() {

    private val domain: String = "gmanga.me"

    override val baseUrl: String = "https://$domain"

    override val lang: String = "ar"

    override val name: String = "GMANGA"

    override val supportsLatest: Boolean = true

    private val gson = Gson()

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")
        return GET("$baseUrl/api/mangas/$mangaId/releases", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = decryptResponse(response)
        val allChapters = data["rows"][0]["rows"].asJsonArray.map { it.asJsonArray }
        val chaptersByNumber = allChapters.groupBy { it.asJsonArray[6].asFloat }

        return chaptersByNumber.map { (number: Float, versions: List<JsonArray>) ->
            SChapter.create().apply {
                chapter_number = number

                val mostViewedScan = versions.maxByOrNull { it[4].asLong }!!
                val chapterName = mostViewedScan[8].asString.let { if (it.trim() != "") " - $it" else "" }

                url = "/r/${mostViewedScan[0]}"
                name = "${number.toInt()}$chapterName"
                date_upload = mostViewedScan[3].asLong * 1000
                scanlator = mostViewedScan[10].asString
            }
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = gson.fromJson<JsonObject>(response.asJsoup().select(".js-react-on-rails-component").html())
        return MangasPage(
            data["mangaDataAction"]["newMangas"].asJsonArray.map {
                SManga.create().apply {
                    url = "/mangas/${it["id"].asString}"
                    title = it["title"].asString
                    val thumbnail = "medium_${it["cover"].asString.substringBeforeLast(".")}.webp"
                    thumbnail_url = "https://media.$domain/uploads/manga/cover/${it["id"].asString}/$thumbnail"
                }
            },
            false
        )
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/mangas/latest", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = gson.fromJson<JsonObject>(response.asJsoup().select(".js-react-on-rails-component").html())
        val mangaData = data["mangaDataAction"]["mangaData"].asJsonObject
        return SManga.create().apply {
            description = mangaData["summary"].nullString ?: ""
            artist = mangaData["artists"].asJsonArray.joinToString(", ") { it.asJsonObject["name"].asString }
            author = mangaData["authors"].asJsonArray.joinToString(", ") { it.asJsonObject["name"].asString }
            genre = mangaData["categories"].asJsonArray.joinToString(", ") { it.asJsonObject["name"].asString }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val url = response.request().url().toString()
        val data = gson.fromJson<JsonObject>(response.asJsoup().select(".js-react-on-rails-component").html())
        val releaseData = data["readerDataAction"]["readerData"]["release"].asJsonObject
        return releaseData["webp_pages"].asJsonArray.map { it.asString }.mapIndexed { index, pageUri ->
            Page(index, "$url#page_$index", "https://media.$domain/uploads/releases/${releaseData["storage_key"].asString}/mq_webp/$pageUri")
        }
    }

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", getFilterList())

    override fun searchMangaParse(response: Response): MangasPage {
        val data = decryptResponse(response)
        val mangas = data["mangas"].asJsonArray
        return MangasPage(
            mangas.asJsonArray.map {
                SManga.create().apply {
                    url = "/mangas/${it["id"].asString}"
                    title = it["title"].asString
                    val thumbnail = "medium_${it["cover"].asString.substringBeforeLast(".")}.webp"
                    thumbnail_url = "https://media.$domain/uploads/manga/cover/${it["id"].asString}/$thumbnail"
                }
            },
            mangas.size() == 50
        )
    }

    private fun decryptResponse(response: Response): JsonObject {
        val encryptedData = gson.fromJson<JsonObject>(response.body()!!.string())["data"].asString
        val decryptedData = decrypt(encryptedData)
        return gson.fromJson(decryptedData)
    }

    private fun buildSearchRequestBody(page: Int, query: String = "", filters: FilterList): RequestBody {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val mangaTypeFilter = filterList.findInstance<MangaTypeFilter>()!!
        val oneShotFilter = filterList.findInstance<OneShotFilter>()!!
        val storyStatusFilter = filterList.findInstance<StoryStatusFilter>()!!
        val translationStatusFilter = filterList.findInstance<TranslationStatusFilter>()!!
        val chapterCountFilter = filterList.findInstance<ChapterCountFilter>()!!
        val dateRangeFilter = filterList.findInstance<DateRangeFilter>()!!
        val categoryFilter = filterList.findInstance<CategoryFilter>()!!

        val body = JsonObject().apply {

            oneShotFilter.state.first().let {
                when {
                    it.isIncluded() -> addProperty("oneshot", true)
                    it.isExcluded() -> addProperty("oneshot", false)
                    else -> addProperty("oneshot", JsonNull.INSTANCE)
                }
            }

            addProperty("title", query)
            addProperty("page", page)
            addProperty(
                "manga_types",
                JsonObject().apply {

                    addProperty(
                        "include",
                        JsonArray().apply {
                            addAll(mangaTypeFilter.state.filter { it.isIncluded() }.map { it.id })
                        }
                    )

                    addProperty(
                        "exclude",
                        JsonArray().apply {
                            addAll(mangaTypeFilter.state.filter { it.isExcluded() }.map { it.id })
                        }
                    )
                }
            )
            addProperty(
                "story_status",
                JsonObject().apply {

                    addProperty(
                        "include",
                        JsonArray().apply {
                            addAll(storyStatusFilter.state.filter { it.isIncluded() }.map { it.id })
                        }
                    )

                    addProperty(
                        "exclude",
                        JsonArray().apply {
                            addAll(storyStatusFilter.state.filter { it.isExcluded() }.map { it.id })
                        }
                    )
                }
            )
            addProperty(
                "translation_status",
                JsonObject().apply {

                    addProperty(
                        "include",
                        JsonArray().apply {
                            addAll(translationStatusFilter.state.filter { it.isIncluded() }.map { it.id })
                        }
                    )

                    addProperty(
                        "exclude",
                        JsonArray().apply {
                            addAll(translationStatusFilter.state.filter { it.isExcluded() }.map { it.id })
                        }
                    )
                }
            )
            addProperty(
                "categories",
                JsonObject().apply {

                    addProperty(
                        "include",
                        JsonArray().apply {
                            add(JsonNull.INSTANCE) // always included, maybe to avoid shifting index in the backend
                            addAll(categoryFilter.state.filter { it.isIncluded() }.map { it.id })
                        }
                    )

                    addProperty(
                        "exclude",
                        JsonArray().apply {
                            addAll(categoryFilter.state.filter { it.isExcluded() }.map { it.id })
                        }
                    )
                }
            )
            addProperty(
                "chapters",
                JsonObject().apply {

                    addPropertyFromValidatingTextFilter(
                        chapterCountFilter.state.first {
                            it.id == FILTER_ID_MIN_CHAPTER_COUNT
                        },
                        "min",
                        "Invalid min chapter count",
                        ""
                    )

                    addPropertyFromValidatingTextFilter(
                        chapterCountFilter.state.first {
                            it.id == FILTER_ID_MAX_CHAPTER_COUNT
                        },
                        "max",
                        "Invalid max chapter count",
                        ""
                    )
                }
            )
            addProperty(
                "dates",
                JsonObject().apply {

                    addPropertyFromValidatingTextFilter(
                        dateRangeFilter.state.first {
                            it.id == FILTER_ID_START_DATE
                        },
                        "start",
                        "Invalid start date"
                    )

                    addPropertyFromValidatingTextFilter(
                        dateRangeFilter.state.first {
                            it.id == FILTER_ID_END_DATE
                        },
                        "end",
                        "Invalid end date"
                    )
                }
            )
        }

        return RequestBody.create(MEDIA_TYPE, body.toString())
    }

    private fun JsonObject.addPropertyFromValidatingTextFilter(filter: ValidatingTextFilter, property: String, invalidErrorMessage: String, default: String? = null) {
        filter.let {
            when {
                it.state == "" -> if (default == null) {
                    addProperty(property, JsonNull.INSTANCE)
                } else addProperty(property, default)
                it.isValid() -> addProperty(property, it.state)
                else -> throw Exception(invalidErrorMessage)
            }
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val body = buildSearchRequestBody(page, query, filters)
        return POST("$baseUrl/api/mangas/search", headers, body)
    }

    override fun getFilterList() = FilterList(
        MangaTypeFilter(),
        OneShotFilter(),
        StoryStatusFilter(),
        TranslationStatusFilter(),
        ChapterCountFilter(),
        DateRangeFilter(),
        CategoryFilter()
    )

    private class MangaTypeFilter() : Filter.Group<TagFilter>(
        "الأصل",
        listOf(
            TagFilter("1", "يابانية", STATE_INCLUDE),
            TagFilter("2", "كورية", STATE_INCLUDE),
            TagFilter("3", "صينية", STATE_INCLUDE),
            TagFilter("4", "عربية", STATE_INCLUDE),
            TagFilter("5", "كوميك", STATE_INCLUDE),
            TagFilter("6", "هواة", STATE_INCLUDE),
            TagFilter("7", "إندونيسية", STATE_INCLUDE),
            TagFilter("8", "روسية", STATE_INCLUDE),
        )
    )

    private class OneShotFilter() : Filter.Group<TagFilter>(
        "ونشوت؟",
        listOf(
            TagFilter(FILTER_ID_ONE_SHOT, "نعم", STATE_EXCLUDE)
        )
    )

    private class StoryStatusFilter() : Filter.Group<TagFilter>(
        "حالة القصة",
        listOf(
            TagFilter("2", "مستمرة"),
            TagFilter("3", "منتهية")
        )
    )

    private class TranslationStatusFilter() : Filter.Group<TagFilter>(
        "حالة الترجمة",
        listOf(
            TagFilter("0", "منتهية"),
            TagFilter("1", "مستمرة"),
            TagFilter("2", "متوقفة"),
            TagFilter("3", "غير مترجمة", STATE_EXCLUDE),
        )
    )

    private class ChapterCountFilter() : Filter.Group<IntFilter>(
        "عدد الفصول",
        listOf(
            IntFilter(FILTER_ID_MIN_CHAPTER_COUNT, "على الأقل"),
            IntFilter(FILTER_ID_MAX_CHAPTER_COUNT, "على الأكثر")
        )
    )

    private class DateRangeFilter() : Filter.Group<DateFilter>(
        "تاريخ النشر",
        listOf(
            DateFilter(FILTER_ID_START_DATE, "تاريخ النشر"),
            DateFilter(FILTER_ID_END_DATE, "تاريخ الإنتهاء")
        )
    )

    private class CategoryFilter() : Filter.Group<TagFilter>(
        "التصنيفات",
        listOf(
            TagFilter("1", "إثارة"),
            TagFilter("2", "أكشن"),
            TagFilter("3", "الحياة المدرسية"),
            TagFilter("4", "الحياة اليومية"),
            TagFilter("5", "آليات"),
            TagFilter("6", "تاريخي"),
            TagFilter("7", "تراجيدي"),
            TagFilter("8", "جوسيه"),
            TagFilter("9", "حربي"),
            TagFilter("10", "خيال"),
            TagFilter("11", "خيال علمي"),
            TagFilter("12", "دراما"),
            TagFilter("13", "رعب"),
            TagFilter("14", "رومانسي"),
            TagFilter("15", "رياضة"),
            TagFilter("16", "ساموراي"),
            TagFilter("17", "سحر"),
            TagFilter("18", "سينين"),
            TagFilter("19", "شوجو"),
            TagFilter("20", "شونين"),
            TagFilter("21", "عنف"),
            TagFilter("22", "غموض"),
            TagFilter("23", "فنون قتال"),
            TagFilter("24", "قوى خارقة"),
            TagFilter("25", "كوميدي"),
            TagFilter("26", "لعبة"),
            TagFilter("27", "مسابقة"),
            TagFilter("28", "مصاصي الدماء"),
            TagFilter("29", "مغامرات"),
            TagFilter("30", "موسيقى"),
            TagFilter("31", "نفسي"),
            TagFilter("32", "نينجا"),
            TagFilter("33", "وحوش"),
            TagFilter("34", "حريم"),
            TagFilter("35", "راشد"),
            TagFilter("38", "ويب-تون"),
            TagFilter("39", "زمنكاني")
        )
    )

    private class TagFilter(val id: String, name: String, state: Int = STATE_IGNORE) : Filter.TriState(name, state)

    private abstract class ValidatingTextFilter(name: String) : Filter.Text(name) {
        abstract fun isValid(): Boolean
    }

    private class DateFilter(val id: String, name: String) : ValidatingTextFilter("($DATE_FILTER_PATTERN) $name)") {
        override fun isValid(): Boolean = DATE_FITLER_FORMAT.isValid(this.state)
    }
    private class IntFilter(val id: String, name: String) : ValidatingTextFilter(name) {
        override fun isValid(): Boolean = state.toIntOrNull() != null
    }

    companion object {
        private val MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8")
        private const val DATE_FILTER_PATTERN = "yyyy/MM/dd"

        private const val FILTER_ID_ONE_SHOT = "oneshot"

        private const val FILTER_ID_START_DATE = "start"
        private const val FILTER_ID_END_DATE = "end"

        private const val FILTER_ID_MIN_CHAPTER_COUNT = "min"
        private const val FILTER_ID_MAX_CHAPTER_COUNT = "max"

        @SuppressLint("SimpleDateFormat")
        private val DATE_FITLER_FORMAT = SimpleDateFormat(DATE_FILTER_PATTERN).apply {
            isLenient = false
        }

        private fun SimpleDateFormat.isValid(date: String): Boolean {
            return try {
                this.parse(date)
                true
            } catch (e: ParseException) {
                false
            }
        }

        private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T
    }
}
