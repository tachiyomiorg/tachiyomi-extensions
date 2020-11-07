package eu.kanade.tachiyomi.extension.pt.socialcomics

import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.PreferenceScreen
import android.text.InputType
import android.widget.Toast
import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.lang.UnsupportedOperationException
import androidx.preference.EditTextPreference as AndroidXEditTextPreference
import androidx.preference.PreferenceScreen as AndroidXPreferenceScreen

class SocialComics : HttpSource(), ConfigurableSource {

    override val name = "Social Comics"

    override val baseUrl = "https://socialcomics.com.br"

    override val lang = "pt-BR"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor { authIntercept(it) }
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", "$baseUrl/home")
        .add("User-Agent", USER_AGENT)

    private fun sourceHeadersBuilder(): Headers.Builder = headersBuilder()
        .add("Accept", ACCEPT_JSON)
        .add("Origin", baseUrl)

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val email: String
        get() = preferences.getString(EMAIL_PREF_KEY, "")!!

    private val password: String
        get() = preferences.getString(PASSWORD_PREF_KEY, "")!!

    private var apiToken: String
        get() = preferences.getString(API_TOKEN_PREF_KEY, "")!!
        set(value) { preferences.edit().putString(API_TOKEN_PREF_KEY, value).apply() }

    private var userHash: String = ""

    override fun popularMangaRequest(page: Int): Request {
        val newHeaders = sourceHeadersBuilder()
            .add("Referer", "$baseUrl/home")
            .build()

        return GET("$SERVICE_URL/api/mobile/home/list/web", newHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.asJson().obj

        val freeTrack = result["tracks"].array
            .firstOrNull {
                it.obj["name"].string.contains("Grátis")
            }

        if (freeTrack != null) {
            val popularMangas = freeTrack.obj["comics"]["items"].array
                .map { popularMangaItemParse(it.obj) }

            return MangasPage(popularMangas, hasNextPage = false)
        }

        return MangasPage(emptyList(), hasNextPage = false)
    }

    private fun popularMangaItemParse(obj: JsonObject) = SManga.create().apply {
        title = obj["name"].string
        thumbnail_url = obj["thumb"].string
        url = "/quadrinho/${obj["hash"].string}?monetize=${obj["monetize"].int}"
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val newHeaders = sourceHeadersBuilder()
            .add("Referer", "$baseUrl/pesquisa")
            .build()

        val endpointUrl = HttpUrl.parse("$SERVICE_URL/api/mobile/search/")!!.newBuilder()
            .addEncodedPathSegment(query)
            .toString()

        return GET(endpointUrl, newHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.asJson().obj

        val searchMangas = result["comics"]["items"].array
            .filter { it.obj["monetize"].int == 0 }
            .map { searchMangaItemParse(it.obj) }

        return MangasPage(searchMangas, false)
    }

    private fun searchMangaItemParse(obj: JsonObject) = SManga.create().apply {
        title = obj["name"].string
        thumbnail_url = obj["thumb"].string
        url = "/quadrinho/${obj["hash"].string}?monetize=${obj["monetize"].int}"
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val hash = manga.url
            .substringAfterLast("/")
            .substringBefore("?")

        val newHeaders = sourceHeadersBuilder()
            .add("Referer", "$baseUrl/quadrinho/$hash")
            .build()

        return GET("$SERVICE_URL/api/mobile/comic/detail/$hash", newHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val apiResult = response.asJson().obj

        title = apiResult["name"].string
        author = apiResult["script"].string
        description = apiResult["description"].string +
            "\n\nEdição: #${apiResult["edition"].int}"
        genre = apiResult["tags"].array
            .joinToString { it.obj["name"].string }
        status = SManga.COMPLETED
        thumbnail_url = apiResult["thumb"].string

        if (apiResult["art"].string != "Não Informado") {
            artist = apiResult["art"].string
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        if (manga.url.substringAfter("?monetize=") == "1") {
            return Observable.error(Exception(ERROR_PAID_CONTENT))
        }

        return super.fetchChapterList(manga)
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val apiResult = response.asJson().obj

        val chapter = SChapter.create().apply {
            name = "Quadrinho"
            scanlator = apiResult["publisher"]["name"].string
            url = "/leitor/${apiResult["hash"].string}"
        }

        return listOf(chapter)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val newHeaders = sourceHeadersBuilder()
            .set("Referer", baseUrl + chapter.url)
            .build()

        val hash = chapter.url.substringAfterLast("/")

        return GET("$SERVICE_URL/api/mobile/comic/pages/$hash", newHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val apiResult = response.asJson().obj
        val hash = apiResult["hash"].string
        val comicUrl = "$baseUrl/leitor/$hash"

        return apiResult["pages"].array
            .mapIndexed { i, el -> Page(i, comicUrl, el.obj["page"].string) }
    }

    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl!!)

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", ACCEPT_IMAGE)
            .add("Referer", page.url)
            .removeAll("Origin")
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val emailPref = EditTextPreference(screen.context).apply {
            key = EMAIL_PREF_KEY
            title = EMAIL_PREF_TITLE
            setDefaultValue("")
            summary = EMAIL_PREF_SUMMARY
            dialogTitle = EMAIL_PREF_TITLE

            setOnPreferenceChangeListener { _, newValue ->
                val res = preferences.edit()
                    .putString(EMAIL_PREF_KEY, newValue as String)
                    .putString(API_TOKEN_PREF_KEY, "")
                    .commit()

                Toast.makeText(screen.context, TOAST_RESTART_TO_APPLY, Toast.LENGTH_LONG).show()
                res
            }
        }
        val passwordPref = EditTextPreference(screen.context).apply {
            key = PASSWORD_PREF_KEY
            title = PASSWORD_PREF_TITLE
            setDefaultValue("")
            summary = PASSWORD_PREF_SUMMARY
            dialogTitle = PASSWORD_PREF_TITLE

            setOnPreferenceChangeListener { _, newValue ->
                val res = preferences.edit()
                    .putString(PASSWORD_PREF_KEY, newValue as String)
                    .putString(API_TOKEN_PREF_KEY, "")
                    .commit()

                Toast.makeText(screen.context, TOAST_RESTART_TO_APPLY, Toast.LENGTH_LONG).show()
                res
            }
        }

        screen.addPreference(emailPref)
        screen.addPreference(passwordPref)
    }

    override fun setupPreferenceScreen(screen: AndroidXPreferenceScreen) {
        val emailPref = AndroidXEditTextPreference(screen.context).apply {
            key = EMAIL_PREF_KEY
            title = EMAIL_PREF_TITLE
            setDefaultValue("")
            summary = EMAIL_PREF_SUMMARY
            dialogTitle = EMAIL_PREF_TITLE

            setOnPreferenceChangeListener { _, newValue ->
                val res = preferences.edit()
                    .putString(EMAIL_PREF_KEY, newValue as String)
                    .putString(API_TOKEN_PREF_KEY, "")
                    .commit()

                Toast.makeText(screen.context, TOAST_RESTART_TO_APPLY, Toast.LENGTH_LONG).show()
                res
            }
        }
        val passwordPref = AndroidXEditTextPreference(screen.context).apply {
            key = PASSWORD_PREF_KEY
            title = PASSWORD_PREF_TITLE
            setDefaultValue("")
            summary = PASSWORD_PREF_SUMMARY
            dialogTitle = PASSWORD_PREF_TITLE

            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }

            setOnPreferenceChangeListener { _, newValue ->
                val res = preferences.edit()
                    .putString(PASSWORD_PREF_KEY, newValue as String)
                    .putString(API_TOKEN_PREF_KEY, "")
                    .commit()

                Toast.makeText(screen.context, TOAST_RESTART_TO_APPLY, Toast.LENGTH_LONG).show()
                res
            }
        }

        screen.addPreference(emailPref)
        screen.addPreference(passwordPref)
    }

    override fun latestUpdatesRequest(page: Int): Request =
        throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Not used")

    private fun authIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.url().toString().contains(THUMBNAIL_CDN)) {
            return chain.proceed(request)
        }

        if (email.isEmpty() || password.isEmpty()) {
            throw IOException(ERROR_CREDENTIALS_MISSING)
        }

        // Always do logout at the first time to reset the token.
        if (userHash.isEmpty() && apiToken.isNotEmpty()) {
            doLogout(chain)
        }

        if (apiToken.isEmpty()) {
            doLogin(chain)
        }

        val authRequest = request.newBuilder()
            .addHeader("Authorization", "Bearer $apiToken")
            .build()

        return chain.proceed(authRequest)
    }

    private fun doLogin(chain: Interceptor.Chain) {
        val loginPayload = jsonObject(
            "device" to jsonObject(
                "device" to USER_AGENT,
                "device_id" to "window.navigator.userAgent.replace(/\\d+/g, '')",
                "platform" to "web"
            ),
            "email" to email,
            "facebook_id" to null,
            "password" to password
        )

        val loginBody = RequestBody.create(JSON_MEDIA_TYPE, loginPayload.toString())

        val loginHeaders = sourceHeadersBuilder()
            .add("Content-Type", loginBody.contentType().toString())
            .add("Content-Length", loginBody.contentLength().toString())
            .add("Referer", "$baseUrl/login")
            .build()

        val loginRequest = POST("$SERVICE_URL/api/mobile/login", loginHeaders, loginBody)
        val response = chain.proceed(loginRequest)

        if (response.code() != 200) {
            throw IOException(ERROR_CANNOT_LOGIN)
        }

        val apiResult = response.asJson().obj

        apiToken = apiResult["api_token"].string
        userHash = apiResult["hash"].string

        response.close()
    }

    private fun doLogout(chain: Interceptor.Chain) {
        val logoutPayload = jsonObject(
            "device" to jsonObject(
                "device_id" to "window.navigator.userAgent.replace(/\\d+/g, '')"
            ),
            "hash_master" to userHash
        )

        val logoutBody = RequestBody.create(JSON_MEDIA_TYPE, logoutPayload.toString())

        val logoutHeaders = sourceHeadersBuilder()
            .add("Content-Type", logoutBody.contentType().toString())
            .add("Content-Length", logoutBody.contentLength().toString())
            .build()

        val logoutRequest = POST("$SERVICE_URL/api/mobile/logout", logoutHeaders, logoutBody)
        val response = chain.proceed(logoutRequest)

        if (response.code() != 200) {
            throw IOException(ERROR_CANNOT_RENEW_TOKEN)
        }

        apiToken = ""
        userHash = ""

        response.close()
    }

    private fun Response.asJson(): JsonElement = JSON_PARSER.parse(body()!!.string())

    companion object {
        private const val SERVICE_URL = "https://service.socialcomics.com.br"
        private const val THUMBNAIL_CDN = "amazonaws.com"

        private const val ACCEPT_JSON = "application/json, text/plain, */*"
        private const val ACCEPT_IMAGE = "image/avif,image/webp,image/apng,image/*,*/*;q=0.8"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/85.0.4183.102 Safari/537.36"

        private val JSON_PARSER by lazy { JsonParser() }

        private const val EMAIL_PREF_KEY = "email"
        private const val EMAIL_PREF_TITLE = "E-mail"
        private const val EMAIL_PREF_SUMMARY = "Defina aqui o seu e-mail para o login."

        private const val PASSWORD_PREF_KEY = "password"
        private const val PASSWORD_PREF_TITLE = "Senha"
        private const val PASSWORD_PREF_SUMMARY = "Defina aqui a sua senha para o login."

        private const val API_TOKEN_PREF_KEY = "api_token"

        private const val ERROR_CANNOT_LOGIN = "Não foi possível realizar o login. Verifique suas informações."
        private const val ERROR_CANNOT_RENEW_TOKEN = "Não foi possível renovar o token de autenticação."
        private const val ERROR_CREDENTIALS_MISSING = "Informações de login incompletas. Revise as configurações."
        private const val ERROR_PAID_CONTENT = "O quadrinho não está disponível no pacote gratuito."

        private const val TOAST_RESTART_TO_APPLY = "Reinicie o Tachiyomi para aplicar as novas configurações."

        private val JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8")
    }
}
