package eu.kanade.tachiyomi.extension.pt.tsukimangas

import android.app.Application
import android.content.SharedPreferences
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.lib.ratelimit.SpecificHostRateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

@Nsfw
class TsukiMangas : HttpSource(), ConfigurableSource {

    override val name = "Tsuki Mangás"

    override val baseUrl = "https://tsukimangas.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(SpecificHostRateLimitInterceptor(baseUrl.toHttpUrl(), 1))
        .addInterceptor(SpecificHostRateLimitInterceptor(CDN_1_URL, 1, period = 2))
        .addInterceptor(SpecificHostRateLimitInterceptor(CDN_2_URL, 1, period = 2))
        .addInterceptor(::tsukiAuthIntercept)
        .build()

    private val json: Json by injectLazy()

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val usernameOrEmail: String
        get() = preferences.getString(USERNAME_OR_EMAIL_PREF_KEY, "")!!

    private val password: String
        get() = preferences.getString(PASSWORD_PREF_KEY, "")!!

    private var apiToken: String? = null

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Accept", ACCEPT)
        .add("Accept-Language", ACCEPT_LANGUAGE)
        .add("User-Agent", USER_AGENT)
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/api/v2/mangas?page=$page&title=&adult_content=false&filter=0", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = json.decodeFromString<TsukiPaginatedDto>(response.body!!.string())

        val popularMangas = result.data.map(::popularMangaItemParse)

        val hasNextPage = result.page < result.lastPage

        return MangasPage(popularMangas, hasNextPage)
    }

    private fun popularMangaItemParse(manga: TsukiMangaDto) = SManga.create().apply {
        val poster = manga.poster?.substringBefore("?")

        title = manga.title + (if (manga.format == NOVEL_FORMAT_ID) " (Novel)" else "")
        thumbnail_url = baseUrl + (if (poster.isNullOrEmpty()) EMPTY_COVER else "/imgs/$poster")
        url = "/obra/${manga.id}/${manga.url}"
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/api/v2/home/lastests?page=$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = json.decodeFromString<TsukiPaginatedDto>(response.body!!.string())

        val latestMangas = result.data.map(::latestMangaItemParse)

        val hasNextPage = result.page < result.lastPage

        return MangasPage(latestMangas, hasNextPage)
    }

    private fun latestMangaItemParse(manga: TsukiMangaDto) = SManga.create().apply {
        val poster = manga.poster?.substringBefore("?")

        title = manga.title + (if (manga.format == NOVEL_FORMAT_ID) " (Novel)" else "")
        thumbnail_url = baseUrl + (if (poster.isNullOrEmpty()) EMPTY_COVER else "/imgs/$poster")
        url = "/obra/${manga.id}/${manga.url}"
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.startsWith(PREFIX_ID_SEARCH) && query.matches(ID_SEARCH_PATTERN)) {
            return mangaDetailsApiRequest(query.removePrefix(PREFIX_ID_SEARCH))
        }

        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/lista-completa")
            .build()

        val url = "$baseUrl/api/v2/mangas?page=$page".toHttpUrlOrNull()!!.newBuilder()
        url.addQueryParameter("title", query)

        // Some filters have to follow an order in the URL.
        filters.filterIsInstance<GenreFilter>().firstOrNull()?.state
            ?.filter { it.state }
            ?.forEach { url.addQueryParameter("genres[]", it.name) }

        filters.filterIsInstance<AdultFilter>().firstOrNull()
            ?.let {
                if (it.state == Filter.TriState.STATE_INCLUDE) {
                    url.addQueryParameter("adult_content", "1")
                } else if (it.state == Filter.TriState.STATE_EXCLUDE) {
                    url.addQueryParameter("adult_content", "false")
                }

                return@let null
            }

        filters.filterIsInstance<SortByFilter>().firstOrNull()
            ?.let { filter ->
                if (filter.state!!.index == 0) {
                    url.addQueryParameter("filter", if (filter.state!!.ascending) "1" else "0")
                } else {
                    url.addQueryParameter("filter", if (filter.state!!.ascending) "3" else "2")
                }
            }

        filters.forEach { filter ->
            when (filter) {
                is DemographyFilter -> {
                    if (filter.state > 0) {
                        url.addQueryParameter("demography", filter.state.toString())
                    }
                }

                is FormatFilter -> {
                    if (filter.state > 0) {
                        url.addQueryParameter("format", filter.state.toString())
                    }
                }

                is StatusFilter -> {
                    if (filter.state > 0) {
                        url.addQueryParameter("status", (filter.state - 1).toString())
                    }
                }
            }
        }

        return GET(url.toString(), newHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.toString().contains("/mangas/")) {
            val manga = mangaDetailsParse(response)

            return MangasPage(listOf(manga), hasNextPage = false)
        }

        val result = json.decodeFromString<TsukiPaginatedDto>(response.body!!.string())

        val searchResults = result.data.map(::searchMangaItemParse)

        val hasNextPage = result.page < result.lastPage

        return MangasPage(searchResults, hasNextPage)
    }

    private fun searchMangaItemParse(manga: TsukiMangaDto) = SManga.create().apply {
        val poster = manga.poster?.substringBefore("?")

        title = manga.title + (if (manga.format == NOVEL_FORMAT_ID) " (Novel)" else "")
        thumbnail_url = baseUrl + (if (poster.isNullOrEmpty()) EMPTY_COVER else "/imgs/$poster")
        url = "/obra/${manga.id}/${manga.url}"
    }

    // Workaround to allow "Open in browser" use the real URL.
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsApiRequest(manga.url))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    private fun mangaDetailsApiRequest(mangaUrl: String): Request {
        val mangaId = mangaUrl.substringAfter("obra/").substringBefore("/")

        return GET("$baseUrl/api/v2/mangas/$mangaId", headers)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val newHeaders = headersBuilder()
            .removeAll("Accept")
            .build()

        return GET(baseUrl + manga.url, newHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val mangaDto = json.decodeFromString<TsukiMangaDto>(response.body!!.string())
        val poster = mangaDto.poster?.substringBefore("?")

        title = mangaDto.title + (if (mangaDto.format == NOVEL_FORMAT_ID) " (Novel)" else "")
        thumbnail_url = baseUrl + (if (poster.isNullOrEmpty()) EMPTY_COVER else "/imgs/$poster")
        description = mangaDto.synopsis.orEmpty()
        status = mangaDto.status.orEmpty().toStatus()
        author = mangaDto.author.orEmpty()
        artist = mangaDto.artist.orEmpty()
        genre = mangaDto.genres.joinToString { it.genre }
        url = "/obra/${mangaDto.id}/${mangaDto.url}"
    }

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfter("obra/").substringBefore("/")

        val newHeaders = headersBuilder()
            .set("Referer", baseUrl + manga.url)
            .build()

        return GET("$baseUrl/api/v2/chapters/$mangaId/all", newHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaUrl = response.request.header("Referer")!!.substringAfter(baseUrl)

        return json
            .decodeFromString<List<TsukiChapterDto>>(response.body!!.string())
            .flatMap { chapterListItemParse(it, mangaUrl) }
            .reversed()
    }

    private fun chapterListItemParse(chapter: TsukiChapterDto, mangaUrl: String): List<SChapter> {
        val mangaId = mangaUrl.substringAfter("obra/").substringBefore("/")
        val mangaSlug = mangaUrl.substringAfterLast("/")

        return chapter.versions.map { version ->
            SChapter.create().apply {
                name = "Cap. " + chapter.number +
                    (if (!chapter.title.isNullOrEmpty()) " - " + chapter.title else "")
                chapter_number = chapter.number.toFloatOrNull() ?: -1f
                scanlator = version.scans
                    .sortedBy { it.scan.name }
                    .joinToString { it.scan.name }
                date_upload = version.createdAt.substringBefore(" ").toDate()
                url = "/leitor/$mangaId/${version.id}/$mangaSlug/${chapter.number}"
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val newHeaders = headersBuilder()
            .set("Referer", baseUrl + chapter.url)
            .build()

        val mangaId = chapter.url
            .substringAfter("leitor/")
            .substringBefore("/")
        val versionId = chapter.url
            .substringAfter("$mangaId/")
            .substringBefore("/")

        return GET("$baseUrl/api/v2/chapter/versions/$versionId", newHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = json.decodeFromString<TsukiReaderDto>(response.body!!.string())

        return result.pages.mapIndexed { i, page ->
            val cdnUrl = "https://cdn${page.server}.tsukimangas.com"
            Page(i, "$baseUrl/", cdnUrl + page.url)
        }
    }

    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl!!)

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", ACCEPT_IMAGE)
            .set("Accept-Language", ACCEPT_LANGUAGE)
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val usernameOrEmailPref = EditTextPreference(screen.context).apply {
            key = USERNAME_OR_EMAIL_PREF_KEY
            title = USERNAME_OR_EMAIL_PREF_TITLE
            setDefaultValue("")
            summary = USERNAME_OR_EMAIL_PREF_SUMMARY
            dialogTitle = USERNAME_OR_EMAIL_PREF_TITLE

            setOnPreferenceChangeListener { _, newValue ->
                apiToken = null

                preferences.edit()
                    .putString(USERNAME_OR_EMAIL_PREF_KEY, newValue as String)
                    .commit()
            }
        }

        val passwordPref = EditTextPreference(screen.context).apply {
            key = PASSWORD_PREF_KEY
            title = PASSWORD_PREF_TITLE
            setDefaultValue("")
            summary = PASSWORD_PREF_SUMMARY
            dialogTitle = PASSWORD_PREF_TITLE

            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }

            setOnPreferenceChangeListener { _, newValue ->
                apiToken = null

                preferences.edit()
                    .putString(PASSWORD_PREF_KEY, newValue as String)
                    .commit()
            }
        }

        screen.addPreference(usernameOrEmailPref)
        screen.addPreference(passwordPref)
    }

    private fun tsukiAuthIntercept(chain: Interceptor.Chain): Response {
        if (apiToken.isNullOrEmpty()) {
            if (usernameOrEmail.isEmpty() || password.isEmpty()) {
                throw IOException(ERROR_CREDENTIALS_MISSING)
            }

            val loginRequest = loginRequest(usernameOrEmail, password)
            val loginResponse = chain.proceed(loginRequest)

            // API returns 422 when the credentials are invalid.
            if (loginResponse.code == 422) {
                loginResponse.close()
                throw IOException(ERROR_CANNOT_LOGIN)
            }

            try {
                val loginResponseBody = loginResponse.body?.string().orEmpty()
                val authResult = json.decodeFromString<TsukiAuthResultDto>(loginResponseBody)

                apiToken = authResult.token

                loginResponse.close()
            } catch (e: SerializationException) {
                loginResponse.close()
                throw IOException(ERROR_LOGIN_FAILED)
            }
        }

        val authorizedRequest = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $apiToken")
            .build()

        val response = chain.proceed(authorizedRequest)

        // API returns 403 when User-Agent permission is disabled
        // and returns 401 when the token is invalid.
        if (response.code == 403 || response.code == 401) {
            response.close()
            throw IOException(if (response.code == 403) UA_DISABLED_MESSAGE else ERROR_INVALID_TOKEN)
        }

        return response
    }

    private fun loginRequest(usernameOrEmail: String, password: String): Request {
        val authInfo = TsukiAuthRequestDto(usernameOrEmail, password)
        val payload = json.encodeToString(authInfo).toRequestBody(JSON_MEDIA_TYPE)

        return POST("$baseUrl/api/v2/login", headers, payload)
    }

    private class Genre(name: String) : Filter.CheckBox(name)

    private class DemographyFilter(demographies: List<String>) : Filter.Select<String>("Demografia", demographies.toTypedArray())

    private class FormatFilter(types: List<String>) : Filter.Select<String>("Formato", types.toTypedArray())

    private class StatusFilter(statusList: List<String>) : Filter.Select<String>("Status", statusList.toTypedArray())

    private class AdultFilter : Filter.TriState("Conteúdo adulto", STATE_EXCLUDE)

    private class SortByFilter : Filter.Sort("Ordenar por", arrayOf("Visualizações", "Nota"), Selection(0, false))

    private class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Gêneros", genres)

    override fun getFilterList(): FilterList = FilterList(
        DemographyFilter(getDemographiesList()),
        FormatFilter(getSerieFormats()),
        StatusFilter(getStatusList()),
        AdultFilter(),
        SortByFilter(),
        GenreFilter(getGenreList()),
    )

    private fun getDemographiesList(): List<String> = listOf(
        "Todas",
        "Shounen",
        "Shoujo",
        "Seinen",
        "Josei"
    )

    private fun getSerieFormats(): List<String> = listOf(
        "Todos",
        "Mangá",
        "Manhwa",
        "Manhua",
        "Novel"
    )

    private fun getStatusList(): List<String> = listOf(
        "Todos",
        "Ativo",
        "Completo",
        "Cancelado",
        "Hiato"
    )

    // [...document.querySelectorAll(".multiselect:first-of-type .multiselect__element span span")]
    //     .map(i => `Genre("${i.innerHTML}")`).join(",\n")
    private fun getGenreList(): List<Genre> = listOf(
        Genre("4-Koma"),
        Genre("Adaptação"),
        Genre("Aliens"),
        Genre("Animais"),
        Genre("Antologia"),
        Genre("Artes Marciais"),
        Genre("Aventura"),
        Genre("Ação"),
        Genre("Colorido por fã"),
        Genre("Comédia"),
        Genre("Crime"),
        Genre("Cross-dressing"),
        Genre("Deliquentes"),
        Genre("Demônios"),
        Genre("Doujinshi"),
        Genre("Drama"),
        Genre("Ecchi"),
        Genre("Esportes"),
        Genre("Fantasia"),
        Genre("Fantasmas"),
        Genre("Filosófico"),
        Genre("Gals"),
        Genre("Ganhador de Prêmio"),
        Genre("Garotas Monstro"),
        Genre("Garotas Mágicas"),
        Genre("Gastronomia"),
        Genre("Gore"),
        Genre("Harém"),
        Genre("Harém Reverso"),
        Genre("Hentai"),
        Genre("Histórico"),
        Genre("Horror"),
        Genre("Incesto"),
        Genre("Isekai"),
        Genre("Jogos Tradicionais"),
        Genre("Lolis"),
        Genre("Long Strip"),
        Genre("Mafia"),
        Genre("Magia"),
        Genre("Mecha"),
        Genre("Medicina"),
        Genre("Militar"),
        Genre("Mistério"),
        Genre("Monstros"),
        Genre("Música"),
        Genre("Ninjas"),
        Genre("Obscenidade"),
        Genre("Oficialmente Colorido"),
        Genre("One-shot"),
        Genre("Policial"),
        Genre("Psicológico"),
        Genre("Pós-apocalíptico"),
        Genre("Realidade Virtual"),
        Genre("Reencarnação"),
        Genre("Romance"),
        Genre("Samurais"),
        Genre("Sci-Fi"),
        Genre("Shotas"),
        Genre("Shoujo Ai"),
        Genre("Shounen Ai"),
        Genre("Slice of Life"),
        Genre("Sobrenatural"),
        Genre("Sobrevivência"),
        Genre("Super Herói"),
        Genre("Thriller"),
        Genre("Todo Colorido"),
        Genre("Trabalho de Escritório"),
        Genre("Tragédia"),
        Genre("Troca de Gênero"),
        Genre("Vampiros"),
        Genre("Viagem no Tempo"),
        Genre("Vida Escolar"),
        Genre("Violência Sexual"),
        Genre("Vídeo Games"),
        Genre("Webcomic"),
        Genre("Wuxia"),
        Genre("Yaoi"),
        Genre("Yuri"),
        Genre("Zumbis")
    )

    private fun String.toDate(): Long {
        return try {
            DATE_FORMATTER.parse(this)?.time ?: 0L
        } catch (e: ParseException) {
            0L
        }
    }

    private fun String.toStatus() = when {
        contains("Ativo") -> SManga.ONGOING
        contains("Completo") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    companion object {
        private const val ACCEPT = "application/json, text/plain, */*"
        private const val ACCEPT_IMAGE = "image/avif,image/webp,image/apng,image/*,*/*;q=0.8"
        private const val ACCEPT_LANGUAGE = "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7,es;q=0.6,gl;q=0.5"
        // By request of site owner. Detailed at Issue #4912 (in Portuguese).
        private val USER_AGENT = "Tachiyomi " + System.getProperty("http.agent")!!

        private val CDN_1_URL = "https://cdn1.tsukimangas.com".toHttpUrl()
        private val CDN_2_URL = "https://cdn2.tsukimangas.com".toHttpUrl()

        private const val UA_DISABLED_MESSAGE = "Permissão de acesso da extensão desativada. " +
            "Aguarde a reativação pelo site para continuar utilizando."

        private const val EMPTY_COVER = "/ext/errorcapa.jpg"

        private const val NOVEL_FORMAT_ID = 4

        private val DATE_FORMATTER by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }

        const val PREFIX_ID_SEARCH = "id:"
        private val ID_SEARCH_PATTERN = "^id:(\\d+)$".toRegex()

        private val JSON_MEDIA_TYPE = "application/json;charset=UTF-8".toMediaType()

        private const val USERNAME_OR_EMAIL_PREF_KEY = "username_or_email"
        private const val USERNAME_OR_EMAIL_PREF_TITLE = "Usuário ou E-mail"
        private const val USERNAME_OR_EMAIL_PREF_SUMMARY = "Defina aqui o seu usuário ou e-mail para o login."

        private const val PASSWORD_PREF_KEY = "password"
        private const val PASSWORD_PREF_TITLE = "Senha"
        private const val PASSWORD_PREF_SUMMARY = "Defina aqui a sua senha para o login."

        private const val ERROR_CANNOT_LOGIN = "Não foi possível realizar o login. " +
            "Revise suas informações nas configurações da extensão."
        private const val ERROR_CREDENTIALS_MISSING = "Acesso restrito a usuários cadastrados " +
            "no site. Defina suas credenciais de acesso nas configurações da extensão."
        private const val ERROR_LOGIN_FAILED = "Não foi possível realizar o login devido " +
            "a um erro inesperado. Tente novamente mais tarde."
        private const val ERROR_INVALID_TOKEN = "Token inválido. " +
            "Revise suas credenciais nas configurações da extensão."
    }
}
