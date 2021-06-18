package eu.kanade.tachiyomi.extension.pt.bruttal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BruttalHomeDto(
    val list: List<BruttalComicBookDto> = emptyList()
)

@Serializable
data class BruttalComicBookDto(
    val author: String,
    val illustrator: String,
    @SerialName("image_mobile") val imageMobile: String,
    val keywords: String,
    val seasons: List<BruttalSeasonDto> = emptyList(),
    @SerialName("soon_text") val soonText: String = "",
    val synopsis: String,
    val title: String,
    val url: String
)

@Serializable
data class BruttalSeasonDto(
    val alias: String,
    val chapters: List<BruttalChapterDto> = emptyList()
)

@Serializable
data class BruttalChapterDto(
    val alias: String,
    val images: List<BruttalImageDto> = emptyList(),
    @SerialName("share_title") val shareTitle: String,
    val title: String,
    val url: String
)

@Serializable
data class BruttalImageDto(
    val image: String
)
