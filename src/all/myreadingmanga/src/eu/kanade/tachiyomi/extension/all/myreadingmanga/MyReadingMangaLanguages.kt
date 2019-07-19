package eu.kanade.tachiyomi.extension.all.myreadingmanga

/**
 * MyReadingManga languages
 */


class MyReadingMangaEnglish : MyReadingManga("en")

fun getAllMyReadingMangaLanguages() = listOf(
        MyReadingMangaEnglish()
)