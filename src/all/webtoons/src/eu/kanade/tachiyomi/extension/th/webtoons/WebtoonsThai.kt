package eu.kanade.tachiyomi.extension.th.webtoons

import eu.kanade.tachiyomi.extension.all.webtoons.WebtoonsDefault
import java.text.SimpleDateFormat
import java.util.*

class WebtoonsThai: WebtoonsDefault("th") {
    override fun chapterParseDate(date: String): Long {
        return SimpleDateFormat("d MMM yyyy", Locale("th")).parse(date).time
    }
}
