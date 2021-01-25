package eu.kanade.tachiyomi.extension.all.komga

import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.PreferenceScreen
import android.text.InputType
import android.widget.Toast
import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import eu.kanade.tachiyomi.extension.BuildConfig
import eu.kanade.tachiyomi.extension.all.komga.dto.BookDto
import eu.kanade.tachiyomi.extension.all.komga.dto.CollectionDto
import eu.kanade.tachiyomi.extension.all.komga.dto.LibraryDto
import eu.kanade.tachiyomi.extension.all.komga.dto.PageDto
import eu.kanade.tachiyomi.extension.all.komga.dto.PageWrapperDto
import eu.kanade.tachiyomi.extension.all.komga.dto.SeriesDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Credentials
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Single
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

open class Komga(suffix: String = "") : ConfigurableSource, HttpSource() {
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/api/v1/series?page=${page - 1}", headers)

    override fun popularMangaParse(response: Response): MangasPage =
        processSeriesPage(response)

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/api/v1/series/latest?page=${page - 1}", headers)

    override fun latestUpdatesParse(response: Response): MangasPage =
        processSeriesPage(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/api/v1/series?search=$query&page=${page - 1}")!!.newBuilder()

        filters.forEach { filter ->
            when (filter) {
                is UnreadOnly -> {
                    if (filter.state) {
                        url.addQueryParameter("read_status", "UNREAD")
                    }
                }
                is LibraryGroup -> {
                    val libraryToInclude = mutableListOf<String>()
                    filter.state.forEach { content ->
                        if (content.state) {
                            libraryToInclude.add(content.id)
                        }
                    }
                    if (libraryToInclude.isNotEmpty()) {
                        url.addQueryParameter("library_id", libraryToInclude.joinToString(","))
                    }
                }
                is CollectionGroup -> {
                    val collectionToInclude = mutableListOf<String>()
                    filter.state.forEach { content ->
                        if (content.state) {
                            collectionToInclude.add(content.id)
                        }
                    }
                    if (collectionToInclude.isNotEmpty()) {
                        url.addQueryParameter("collection_id", collectionToInclude.joinToString(","))
                    }
                }
                is StatusGroup -> {
                    val statusToInclude = mutableListOf<String>()
                    filter.state.forEach { content ->
                        if (content.state) {
                            statusToInclude.add(content.name.toUpperCase(Locale.ROOT))
                        }
                    }
                    if (statusToInclude.isNotEmpty()) {
                        url.addQueryParameter("status", statusToInclude.joinToString(","))
                    }
                }
                is GenreGroup -> {
                    val genreToInclude = mutableListOf<String>()
                    filter.state.forEach { content ->
                        if (content.state) {
                            genreToInclude.add(content.name)
                        }
                    }
                    if (genreToInclude.isNotEmpty()) {
                        url.addQueryParameter("genre", genreToInclude.joinToString(","))
                    }
                }
                is TagGroup -> {
                    val tagToInclude = mutableListOf<String>()
                    filter.state.forEach { content ->
                        if (content.state) {
                            tagToInclude.add(content.name)
                        }
                    }
                    if (tagToInclude.isNotEmpty()) {
                        url.addQueryParameter("tag", tagToInclude.joinToString(","))
                    }
                }
                is PublisherGroup -> {
                    val publisherToInclude = mutableListOf<String>()
                    filter.state.forEach { content ->
                        if (content.state) {
                            publisherToInclude.add(content.name)
                        }
                    }
                    if (publisherToInclude.isNotEmpty()) {
                        url.addQueryParameter("publisher", publisherToInclude.joinToString(","))
                    }
                }
                is Filter.Sort -> {
                    var sortCriteria = when (filter.state?.index) {
                        0 -> "metadata.titleSort"
                        1 -> "createdDate"
                        2 -> "lastModifiedDate"
                        else -> ""
                    }
                    if (sortCriteria.isNotEmpty()) {
                        sortCriteria += "," + if (filter.state?.ascending!!) "asc" else "desc"
                        url.addQueryParameter("sort", sortCriteria)
                    }
                }
            }
        }

        return GET(url.toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage =
        processSeriesPage(response)

    override fun mangaDetailsRequest(manga: SManga): Request =
        GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val series = gson.fromJson<SeriesDto>(response.body()?.charStream()!!)
        return series.toSManga()
    }

    override fun chapterListRequest(manga: SManga): Request =
        GET("$baseUrl${manga.url}/books?unpaged=true&media_status=READY", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val page = gson.fromJson<PageWrapperDto<BookDto>>(response.body()?.charStream()!!).content

        return page.map { book ->
            SChapter.create().apply {
                chapter_number = book.metadata.numberSort
                name = "${book.metadata.number} - ${book.metadata.title} (${book.size})"
                url = "$baseUrl/api/v1/books/${book.id}"
                date_upload = book.metadata.releaseDate?.let { parseDate(it) }
                    ?: parseDateTime(book.fileLastModified)
            }
        }.sortedByDescending { it.chapter_number }
    }

    override fun pageListRequest(chapter: SChapter): Request =
        GET("${chapter.url}/pages")

    override fun pageListParse(response: Response): List<Page> {
        val pages = gson.fromJson<List<PageDto>>(response.body()?.charStream()!!)
        return pages.map {
            val url = "${response.request().url()}/${it.number}" +
                if (!supportedImageTypes.contains(it.mediaType)) {
                    "?convert=png"
                } else {
                    ""
                }
            Page(
                index = it.number - 1,
                imageUrl = url
            )
        }
    }

    private fun processSeriesPage(response: Response): MangasPage {
        var lastPage: Boolean
        val page = with(gson.fromJson<PageWrapperDto<SeriesDto>>(response.body()?.charStream()!!)) {
            lastPage = last
            content
        }
        val mangas = page.map {
            it.toSManga()
        }
        return MangasPage(mangas, !lastPage)
    }

    private fun SeriesDto.toSManga(): SManga =
        SManga.create().apply {
            title = metadata.title
            url = "/api/v1/series/$id"
            thumbnail_url = "$baseUrl/api/v1/series/$id/thumbnail"
            status = when (metadata.status) {
                "ONGOING" -> SManga.ONGOING
                "ENDED" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            genre = (metadata.genres + metadata.tags).joinToString(", ")
            description = metadata.summary.ifBlank { booksMetadata.summary }
            booksMetadata.authors.groupBy { it.role }.let {
                author = it["writer"]?.map { it.name }?.distinct()?.joinToString()
                artist = it["penciller"]?.map { it.name }?.distinct()?.joinToString()
            }
        }

    private fun parseDate(date: String?): Long =
        if (date == null)
            Date().time
        else {
            try {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date).time
            } catch (ex: Exception) {
                Date().time
            }
        }

    private fun parseDateTime(date: String?): Long =
        if (date == null)
            Date().time
        else {
            try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(date).time
            } catch (ex: Exception) {
                try {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S", Locale.US).parse(date).time
                } catch (ex: Exception) {
                    Date().time
                }
            }
        }

    override fun imageUrlParse(response: Response): String = ""

    private class LibraryFilter(val id: String, name: String) : Filter.CheckBox(name, false)
    private class LibraryGroup(libraries: List<LibraryFilter>) : Filter.Group<LibraryFilter>("Libraries", libraries)
    private class CollectionFilter(val id: String, name: String) : Filter.CheckBox(name, false)
    private class CollectionGroup(collections: List<CollectionFilter>) : Filter.Group<CollectionFilter>("Collections", collections)
    private class SeriesSort : Filter.Sort("Sort", arrayOf("Alphabetically", "Date added", "Date updated"), Selection(0, true))
    private class StatusFilter(name: String) : Filter.CheckBox(name, false)
    private class StatusGroup(filters: List<StatusFilter>) : Filter.Group<StatusFilter>("Status", filters)
    private class UnreadOnly : Filter.CheckBox("Unread only", false)
    private class GenreFilter(genre: String) : Filter.CheckBox(genre, false)
    private class GenreGroup(genres: List<GenreFilter>) : Filter.Group<GenreFilter>("Genres", genres)
    private class TagFilter(tag: String) : Filter.CheckBox(tag, false)
    private class TagGroup(tags: List<TagFilter>) : Filter.Group<TagFilter>("Tags", tags)
    private class PublisherFilter(publisher: String) : Filter.CheckBox(publisher, false)
    private class PublisherGroup(publishers: List<PublisherFilter>) : Filter.Group<PublisherFilter>("Publishers", publishers)

    override fun getFilterList(): FilterList =
        FilterList(
            UnreadOnly(),
            LibraryGroup(libraries.map { LibraryFilter(it.id, it.name) }.sortedBy { it.name.toLowerCase() }),
            CollectionGroup(collections.map { CollectionFilter(it.id, it.name) }.sortedBy { it.name.toLowerCase() }),
            StatusGroup(listOf("Ongoing", "Ended", "Abandoned", "Hiatus").map { StatusFilter(it) }),
            GenreGroup(genres.map { GenreFilter(it) }),
            TagGroup(tags.map { TagFilter(it) }),
            PublisherGroup(publishers.map { PublisherFilter(it) }),
            SeriesSort()
        )

    private var libraries = emptyList<LibraryDto>()
    private var collections = emptyList<CollectionDto>()
    private var genres = emptySet<String>()
    private var tags = emptySet<String>()
    private var publishers = emptySet<String>()

    override val name = "Komga${if (suffix.isNotBlank()) " ($suffix)" else ""}"
    override val lang = "en"
    override val supportsLatest = true

    override val baseUrl by lazy { getPrefBaseUrl() }
    private val username by lazy { getPrefUsername() }
    private val password by lazy { getPrefPassword() }
    private val gson by lazy { Gson() }

    override fun headersBuilder(): Headers.Builder =
        Headers.Builder()
            .add("User-Agent", "Tachiyomi Komga v${BuildConfig.VERSION_NAME}")

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client: OkHttpClient =
        network.client.newBuilder()
            .authenticator { _, response ->
                if (response.request().header("Authorization") != null) {
                    null // Give up, we've already failed to authenticate.
                } else {
                    response.request().newBuilder()
                        .addHeader("Authorization", Credentials.basic(username, password))
                        .build()
                }
            }
            .build()

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        screen.addPreference(screen.editTextPreference(ADDRESS_TITLE, ADDRESS_DEFAULT, baseUrl))
        screen.addPreference(screen.editTextPreference(USERNAME_TITLE, USERNAME_DEFAULT, username))
        screen.addPreference(screen.editTextPreference(PASSWORD_TITLE, PASSWORD_DEFAULT, password, true))
    }

    private fun androidx.preference.PreferenceScreen.editTextPreference(title: String, default: String, value: String, isPassword: Boolean = false): androidx.preference.EditTextPreference {
        return androidx.preference.EditTextPreference(context).apply {
            key = title
            this.title = title
            summary = value
            this.setDefaultValue(default)
            dialogTitle = title

            if (isPassword) {
                setOnBindEditTextListener {
                    it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
            }

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(title, newValue as String).commit()
                    Toast.makeText(context, "Restart Tachiyomi to apply new setting.", Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addPreference(screen.supportEditTextPreference(ADDRESS_TITLE, ADDRESS_DEFAULT, baseUrl))
        screen.addPreference(screen.supportEditTextPreference(USERNAME_TITLE, USERNAME_DEFAULT, username))
        screen.addPreference(screen.supportEditTextPreference(PASSWORD_TITLE, PASSWORD_DEFAULT, password))
    }

    private fun PreferenceScreen.supportEditTextPreference(title: String, default: String, value: String): EditTextPreference {
        return EditTextPreference(context).apply {
            key = title
            this.title = title
            summary = value
            this.setDefaultValue(default)
            dialogTitle = title

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(title, newValue as String).commit()
                    Toast.makeText(context, "Restart Tachiyomi to apply new setting.", Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }
    }

    private fun getPrefBaseUrl(): String = preferences.getString(ADDRESS_TITLE, ADDRESS_DEFAULT)!!
    private fun getPrefUsername(): String = preferences.getString(USERNAME_TITLE, USERNAME_DEFAULT)!!
    private fun getPrefPassword(): String = preferences.getString(PASSWORD_TITLE, PASSWORD_DEFAULT)!!

    init {
        Single.fromCallable {
            client.newCall(GET("$baseUrl/api/v1/libraries", headers)).execute()
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { response ->
                    libraries = try {
                        gson.fromJson(response.body()?.charStream()!!)
                    } catch (e: Exception) {
                        emptyList()
                    }
                },
                {}
            )

        Single.fromCallable {
            client.newCall(GET("$baseUrl/api/v1/collections?unpaged=true", headers)).execute()
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { response ->
                    collections = try {
                        gson.fromJson<PageWrapperDto<CollectionDto>>(response.body()?.charStream()!!).content
                    } catch (e: Exception) {
                        emptyList()
                    }
                },
                {}
            )

        Single.fromCallable {
            client.newCall(GET("$baseUrl/api/v1/genres", headers)).execute()
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { response ->
                    genres = try {
                        gson.fromJson(response.body()?.charStream()!!)
                    } catch (e: Exception) {
                        emptySet()
                    }
                },
                {}
            )

        Single.fromCallable {
            client.newCall(GET("$baseUrl/api/v1/tags", headers)).execute()
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { response ->
                    tags = try {
                        gson.fromJson(response.body()?.charStream()!!)
                    } catch (e: Exception) {
                        emptySet()
                    }
                },
                {}
            )

        Single.fromCallable {
            client.newCall(GET("$baseUrl/api/v1/publishers", headers)).execute()
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { response ->
                    publishers = try {
                        gson.fromJson(response.body()?.charStream()!!)
                    } catch (e: Exception) {
                        emptySet()
                    }
                },
                {}
            )
    }

    companion object {
        private const val ADDRESS_TITLE = "Address"
        private const val ADDRESS_DEFAULT = ""
        private const val USERNAME_TITLE = "Username"
        private const val USERNAME_DEFAULT = ""
        private const val PASSWORD_TITLE = "Password"
        private const val PASSWORD_DEFAULT = ""

        private val supportedImageTypes = listOf("image/jpeg", "image/png", "image/gif", "image/webp")
    }
}
