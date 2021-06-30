package eu.kanade.tachiyomi.extension.all.mangadex.dto

import kotlinx.serialization.Serializable

@Serializable
data class AtHomeDto(
    val baseUrl: String
)

@Serializable
data class ImageReportDto(
    val url: String,
    val success: Boolean,
    val bytes: Int?,
    val cached: Boolean,
    val duration: Long,
)
