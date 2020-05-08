package eu.kanade.tachiyomi.extension.all.myreadingmanga

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MyReadingMangaFactory : SourceFactory {
    override fun createSources(): List<Source> = languageList.map { MyReadingManga(it.first, it.second) }
}

// These should all be valid. Add a language code and uncomment to enable
private val languageList = listOf(
    Pair("ar", "Arabic"),
//    Pair("", "Bahasa"),
    Pair("id", "Indonesia"),
//    Pair("", "Bulgarian"),
    Pair("zh", "Chinese"),
//    Pair("", "Croatian"),
//    Pair("", "Czech"),
    Pair("en", "English"),
//    Pair("", "Filipino"),
//    Pair("", "Finnish"),
//    Pair("", "Flemish"),
//    Pair("", "Dutch"),
    Pair("fr", "French"),
    Pair("de", "German"),
//    Pair("", "Greek"),
//    Pair("", "Hebrew"),
//    Pair("", "Hindi"),
//    Pair("", "Hungarian"),
    Pair("it", "Italian"),
    Pair("ja", "Japanese"),
    Pair("ko", "Korean"),
//    Pair("", "Polish"),
    Pair("pt-BR", "Portuguese"),
//    Pair("", "Romanian"),
    Pair("ru", "Russian"),
//    Pair("", "Slovak"),
    Pair("es", "Spanish"),
//    Pair("", "Swedish"),
//    Pair("", "Thai"),
    Pair("tr", "Turkish"),
    Pair("vi", "Vietnamese")
)
