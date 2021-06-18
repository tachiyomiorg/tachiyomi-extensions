package eu.kanade.tachiyomi.extension.pt.saikaiscan

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SaikaiScanResultDto<T>(
    val data: T? = null,
    val meta: SaikaiScanMetaDto? = null
)

typealias SaikaiScanPaginatedStoriesDto = SaikaiScanResultDto<List<SaikaiScanStoryDto>>
typealias SaikaiScanReleaseResultDto = SaikaiScanResultDto<SaikaiScanReleaseDto>

@Serializable
data class SaikaiScanMetaDto(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int
)

@Serializable
data class SaikaiScanStoryDto(
    val artists: List<SaikaiScanPersonDto> = emptyList(),
    val authors: List<SaikaiScanPersonDto> = emptyList(),
    val genres: List<SaikaiScanGenreDto> = emptyList(),
    val image: String,
    val releases: List<SaikaiScanReleaseDto> = emptyList(),
    val slug: String,
    val status: SaikaiScanStatusDto? = null,
    val synopsis: String,
    val title: String
)

@Serializable
data class SaikaiScanPersonDto(
    val name: String
)

@Serializable
data class SaikaiScanGenreDto(
    val name: String
)

@Serializable
data class SaikaiScanStatusDto(
    val name: String
)

@Serializable
data class SaikaiScanReleaseDto(
    val chapter: String,
    val id: Int,
    @SerialName("is_active") val isActive: Int = 1,
    @SerialName("published_at") val publishedAt: String,
    @SerialName("release_images") val releaseImages: List<SaikaiScanReleaseImageDto> = emptyList(),
    val slug: String,
    val title: String? = ""
)

@Serializable
data class SaikaiScanReleaseImageDto(
    val image: String
)
