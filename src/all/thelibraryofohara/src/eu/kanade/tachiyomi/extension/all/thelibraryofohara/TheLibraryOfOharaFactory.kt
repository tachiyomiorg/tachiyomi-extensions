package eu.kanade.tachiyomi.extension.all.thelibraryofohara

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class TheLibraryOfOharaFactory : SourceFactory {
    override fun createSources(): List<Source> = languageList.map { TheLibraryOfOhara(it.tachiLang, it.siteLang, it.latestLang) }
}

private data class Source(val tachiLang: String, val siteLang: String, val latestLang: String = siteLang)

private val languageList = listOf(

    Source("id", "Indonesia"),
    Source("en", "English"),
    Source("es", "Spanish")

)
