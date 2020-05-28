data class GenresDto(
    val id: Int,
    val name: String
)
data class BranchesDto(
    val id: Long
)
data class ImgDto(
    val high: String,
    val mid: String,
    val low: String
)

data class LibraryDto(
    val id: Long,
    val en_name: String,
    val rus_name: String,
    val dir: String,
    val issue_year: Int,
    val genres: List<GenresDto>,
    val img: ImgDto
)
data class MangaDetDto(
    val id: Long,
    val en_name: String,
    val rus_name: String,
    val dir: String,
    val description: String,
    val issue_year: Int,
    val img: ImgDto,
    val genres: List<GenresDto>,
    val branches: List<BranchesDto>
)
data class PropsDto(
    val total_items: Int,
    val total_pages: Int,
    val page: Int
)

data class PageWrapperDto<T>(
    val msg: String,
    val content: List<T>,
    val props: PropsDto,
    val last: Boolean
)

data class SeriesWrapperDto<T>(
    val msg: String,
    val content: T,
    val props: PropsDto
)

data class BookDto(
    val id: Long,
    val tome: Int,
    val chapter: Float,
    val name: String,
    val upload_date: String
)
