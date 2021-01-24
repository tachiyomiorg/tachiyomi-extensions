data class GenresDto(
    val id: Int,
    val name: String
)

data class BranchesDto(
    val id: Long,
    val count_chapters: Int
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

data class StatusDto(
    val id: Int,
    val name: String
)

data class MangaDetDto(
    val id: Long,
    val en_name: String,
    val rus_name: String,
    val dir: String,
    val description: String,
    val issue_year: Int,
    val img: ImgDto,
    val type: GenresDto,
    val genres: List<GenresDto>,
    val branches: List<BranchesDto>,
    val status: StatusDto
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

data class PublisherDto(
    val name: String,
    val dir: String
)

data class BookDto(
    val id: Long,
    val tome: Int,
    val chapter: String,
    val name: String,
    val upload_date: String,
    val is_paid: Boolean,
    val is_bought: Boolean,
    val publishers: List<PublisherDto>
)

data class PagesDto(
    val id: Int,
    val link: String,
    val page: Int,
    val count_comments: Int
)

data class PageDto(
    val pages: List<PagesDto>
)

data class UserDto(
    val access_token: String
)

data class PaidPagesDto(
    val id: Long,
    val link: String,
    val page: Int
)

data class PaidPageDto(
    val pages: List<List<PaidPagesDto>>
)
