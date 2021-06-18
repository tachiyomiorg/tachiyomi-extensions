package eu.kanade.tachiyomi.extension.en.bilibilicomics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BilibiliResultDto<T>(
    val code: Int = 0,
    val data: T? = null,
    @SerialName("msg") val message: String = ""
)

@Serializable
data class BilibiliFeaturedDto(
    @SerialName("roll_six_comics") val rollSixComics: List<BilibiliComicDto> = emptyList()
)

@Serializable
data class BilibiliSearchDto(
    val list: List<BilibiliComicDto> = emptyList()
)

@Serializable
data class BilibiliComicDto(
    @SerialName("author_name") val authorName: List<String> = emptyList(),
    @SerialName("classic_lines") val classicLines: String = "",
    @SerialName("comic_id") val comicId: Int = 0,
    @SerialName("ep_list") val episodeList: List<BilibiliEpisodeDto> = emptyList(),
    val id: Int = 0,
    @SerialName("is_finish") val isFinish: Int = 0,
    val styles: List<String> = emptyList(),
    val title: String,
    @SerialName("vertical_cover") val verticalCover: String = ""
)

@Serializable
data class BilibiliEpisodeDto(
    val id: Int,
    @SerialName("is_locked") val isLocked: Boolean,
    @SerialName("ord") val order: Float,
    @SerialName("pub_time") val publicationTime: String,
    val title: String
)

@Serializable
data class BilibiliReader(
    val images: List<BilibiliImageDto> = emptyList()
)

@Serializable
data class BilibiliImageDto(
    val path: String
)

@Serializable
data class BilibiliPageDto(
    val token: String,
    val url: String
)
