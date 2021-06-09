package eu.kanade.tachiyomi.extension.all.mangadex.dto
import kotlinx.serialization.Serializable

@Serializable
data class ChapterListDto(
    val limit: Int,
    val offset: Int,
    val total: Int,
    val results: List<ChapterDto>
)

@Serializable
data class ChapterDto(
    val result: String,
    val data: ChapterDataDto,
    val relationships: List<RelationshipDto>
)

@Serializable
data class ChapterDataDto(
    val id: String,
    val type: String,
    val attributes: ChapterAttributesDto,
)

@Serializable
data class ChapterAttributesDto(
    val title: String?,
    val volume: String?,
    val chapter: String?,
    val translatedLanguage: String,
    val publishAt: String,
    val data: List<String>,
    val dataSaver: List<String>,
    val hash: String,
)

@Serializable
data class GroupListDto(
    val limit: Int,
    val offset: Int,
    val total: Int,
    val results: List<GroupDto>
)

@Serializable
data class GroupDto(
    val result: String,
    val data: GroupDataDto,
)

@Serializable
data class GroupDataDto(
    val id: String,
    val attributes: GroupAttributesDto,
)

@Serializable
data class GroupAttributesDto(
    val name: String,
)
