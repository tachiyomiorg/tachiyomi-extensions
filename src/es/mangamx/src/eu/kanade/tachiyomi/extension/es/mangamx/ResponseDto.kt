package eu.kanade.tachiyomi.extension.es.mangamx

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResponseDto(
    @SerialName("mangas") val mangaList: List<MangaDto> = emptyList(),
    @SerialName("usuarios") val usersList: List<UserDto>? = emptyList(),
    @SerialName("grupos") val groupsList: List<GroupDto>? = emptyList(),
)

@Serializable
data class MangaDto(
    @SerialName("nombre") val name: String = "",
    @SerialName("alterno") val alternative: String? = "",
    @SerialName("tipo") val type: Int = 0,
    @SerialName("lanzamiento") val releaseYear: Int = 0,
    @SerialName("autor") val author: String? = "",
    val visible: Int = 0,
    val cover: String? = "",
    val slug: String = "",
    val url: String = "",
    val img: String = "",
)

@Serializable
data class UserDto(
    @SerialName("usuario") val username: String = "",
    @SerialName("perfil") val profile: String? = "",
    @SerialName("genero") val gender: String? = "",
    val id: Int? = 0,
    val url: String = "",
    val img: String = "",
)

@Serializable
data class GroupDto(
    @SerialName("nombre") val name: String = "",
    val id: Int? = 0,
    val cover: String? = "",
    val url: String = "",
    val img: String = "",
)
