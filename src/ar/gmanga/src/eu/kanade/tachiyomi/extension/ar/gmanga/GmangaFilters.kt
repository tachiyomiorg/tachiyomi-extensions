package eu.kanade.tachiyomi.extension.ar.gmanga

import android.annotation.SuppressLint
import com.github.salomonbrys.kotson.addAll
import com.github.salomonbrys.kotson.addProperty
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import java.lang.Exception
import java.text.ParseException
import java.text.SimpleDateFormat

class GmangaFilters() {

    companion object {

        fun getFilterList() = FilterList(
            MangaTypeFilter(),
            OneShotFilter(),
            StoryStatusFilter(),
            TranslationStatusFilter(),
            ChapterCountFilter(),
            DateRangeFilter(),
            CategoryFilter()
        )

        fun buildSearchPayload(page: Int, query: String = "", filters: FilterList): JsonObject {
            val mangaTypeFilter = filters.findInstance<MangaTypeFilter>()!!
            val oneShotFilter = filters.findInstance<OneShotFilter>()!!
            val storyStatusFilter = filters.findInstance<StoryStatusFilter>()!!
            val translationStatusFilter = filters.findInstance<TranslationStatusFilter>()!!
            val chapterCountFilter = filters.findInstance<ChapterCountFilter>()!!
            val dateRangeFilter = filters.findInstance<DateRangeFilter>()!!
            val categoryFilter = filters.findInstance<CategoryFilter>()!!

            return JsonObject().apply {

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
                            ERROR_INVALID_MIN_CHAPTER_COUNT,
                            ""
                        )

                        addPropertyFromValidatingTextFilter(
                            chapterCountFilter.state.first {
                                it.id == FILTER_ID_MAX_CHAPTER_COUNT
                            },
                            "max",
                            ERROR_INVALID_MAX_CHAPTER_COUNT,
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
                            ERROR_INVALID_START_DATE
                        )

                        addPropertyFromValidatingTextFilter(
                            dateRangeFilter.state.first {
                                it.id == FILTER_ID_END_DATE
                            },
                            "end",
                            ERROR_INVALID_END_DATE
                        )
                    }
                )
            }
        }

        // filter IDs
        private const val FILTER_ID_ONE_SHOT = "oneshot"
        private const val FILTER_ID_START_DATE = "start"
        private const val FILTER_ID_END_DATE = "end"
        private const val FILTER_ID_MIN_CHAPTER_COUNT = "min"
        private const val FILTER_ID_MAX_CHAPTER_COUNT = "max"

        // error messages
        private const val ERROR_INVALID_START_DATE = "تاريخ بداية غير صالح"
        private const val ERROR_INVALID_END_DATE = " تاريخ نهاية غير صالح"
        private const val ERROR_INVALID_MIN_CHAPTER_COUNT = "الحد الأدنى لعدد الفصول غير صالح"
        private const val ERROR_INVALID_MAX_CHAPTER_COUNT = "الحد الأقصى لعدد الفصول غير صالح"

        private class MangaTypeFilter() : Filter.Group<TagFilter>(
            "الأصل",
            listOf(
                TagFilter("1", "يابانية", TriState.STATE_INCLUDE),
                TagFilter("2", "كورية", TriState.STATE_INCLUDE),
                TagFilter("3", "صينية", TriState.STATE_INCLUDE),
                TagFilter("4", "عربية", TriState.STATE_INCLUDE),
                TagFilter("5", "كوميك", TriState.STATE_INCLUDE),
                TagFilter("6", "هواة", TriState.STATE_INCLUDE),
                TagFilter("7", "إندونيسية", TriState.STATE_INCLUDE),
                TagFilter("8", "روسية", TriState.STATE_INCLUDE),
            )
        )

        private class OneShotFilter() : Filter.Group<TagFilter>(
            "ونشوت؟",
            listOf(
                TagFilter(FILTER_ID_ONE_SHOT, "نعم", TriState.STATE_EXCLUDE)
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
                TagFilter("3", "غير مترجمة", TriState.STATE_EXCLUDE),
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

        private const val DATE_FILTER_PATTERN = "yyyy/MM/dd"

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

        private fun JsonObject.addPropertyFromValidatingTextFilter(
            filter: ValidatingTextFilter,
            property: String,
            invalidErrorMessage: String,
            default: String? = null
        ) {
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

        private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T

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
    }
}
