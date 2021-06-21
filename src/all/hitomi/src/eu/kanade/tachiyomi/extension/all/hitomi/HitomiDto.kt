package eu.kanade.tachiyomi.extension.all.hitomi

import kotlinx.serialization.Serializable

@Serializable
data class HitomiChapterDto(
    val files: List<HitomiFileDto> = emptyList(),
)

@Serializable
data class HitomiFileDto(
    val name: String,
    val hasavif: Int,
    val hash: String,
    val haswebp: Int,
)
