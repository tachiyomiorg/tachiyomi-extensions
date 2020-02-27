package eu.kanade.tachiyomi.extension.all.lanraragi.model

data class ArchiveSearchResult(
    val data: List<Archive>,
    val draw: Int,
    val recordsFiltered: Int,
    val recordsTotal: Int
)
