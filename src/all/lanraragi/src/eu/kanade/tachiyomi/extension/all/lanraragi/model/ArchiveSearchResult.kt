package eu.kanade.tachiyomi.extension.all.lanraragi.model

data class ArchiveSearchResult(
    val data: List<Archive>,
    val draw: Number,
    val recordsFiltered: Number,
    val recordsTotal: Number
)
