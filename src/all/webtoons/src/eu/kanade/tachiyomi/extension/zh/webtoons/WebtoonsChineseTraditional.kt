package eu.kanade.tachiyomi.extension.zh.webtoons

import eu.kanade.tachiyomi.extension.all.webtoons.WebtoonsDefault
import java.text.SimpleDateFormat
import java.util.*

class WebtoonsChineseTraditional: WebtoonsDefault("zh", "zh-hant") {
    override fun chapterParseDate(date: String): Long {
        return SimpleDateFormat("yyyy/MM/dd", Locale.TRADITIONAL_CHINESE).parse(date).time
    }
}
