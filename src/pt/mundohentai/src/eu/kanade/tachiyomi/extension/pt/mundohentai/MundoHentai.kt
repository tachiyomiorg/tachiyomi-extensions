package eu.kanade.tachiyomi.extension.pt.mundohentai

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Nsfw
class MundoHentai : ParsedHttpSource() {

    override val name = "Mundo Hentai"

    override val baseUrl = "https://mundohentaioficial.com"

    override val lang = "pt-BR"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Referer", baseUrl)

    private fun genericMangaFromElement(element: Element): SManga =
        SManga.create().apply {
            title = element.select("div.menu a.title").text()
            thumbnail_url = element.attr("style")
                .substringAfter("url(\"")
                .substringBefore("\")")
            url = element.select("a.absolute").attr("href")
        }

    // The source does not have a popular list page, so we use the Doujin list instead.
    override fun popularMangaRequest(page: Int): Request {
        val newHeaders = headersBuilder()
            .set("Referer", if (page == 1) baseUrl else "$baseUrl/tipo/doujin/${page - 1}")
            .build()

        val pageStr = if (page != 1) "/$page" else ""
        return GET("$baseUrl/tipo/doujin$pageStr", newHeaders)
    }

    override fun popularMangaSelector(): String = "ul.post-list li div.card:has(a.absolute[href^=/])"

    override fun popularMangaFromElement(element: Element): SManga = genericMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "div.buttons:not(:has(a.selected + a.material-icons))"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = HttpUrl.parse("$baseUrl/search")!!.newBuilder()
                .addQueryParameter("q", query)
                .toString()

            return GET(url, headers)
        }

        val tagFilter = filters[1] as TagFilter
        val tagSlug = tagFilter.values[tagFilter.state].slug

        val newHeaders = headersBuilder()
            .set("Referer", if (page == 1) "$baseUrl/categories" else "$baseUrl/tags/$tagSlug/${page - 1}")
            .build()

        val pageStr = if (page != 1) "/$page" else ""
        return GET("$baseUrl/tags/$tagSlug$pageStr", newHeaders)
    }

    override fun searchMangaSelector() = popularMangaSelector() + ":not(:has(div.right-tape))"

    override fun searchMangaFromElement(element: Element): SManga = genericMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val post = document.select("div.post")

        return SManga.create().apply {
            author = post.select("div.tags div.tag:contains(Artista:) a.value").text()
            genre = post.select("div.tags div.tag:contains(Tags:) a.value").joinToString { it.text() }
            description = post.select("div.tags div.tag:contains(Tipo:)").text()
                .plus("\n" + post.select("div.tags div.tag:contains(Cor:)").text())
            status = SManga.COMPLETED
            thumbnail_url = post.select("div.cover img").attr("src")
        }
    }

    override fun chapterListSelector(): String = "div.post header.data div.float-buttons a.read"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = "Capítulo"
        scanlator = element.parent().parent()
            .select("div.tags div.tag:contains(Tradutor:) a.value")
            .text()
        chapter_number = 1f
        url = element.attr("href")
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val newHeader = headersBuilder()
            .set("Referer", "$baseUrl${chapter.url}".substringBeforeLast("/"))
            .build()

        return GET(baseUrl + chapter.url, newHeader)
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.gallery > img")
            .mapIndexed { i, el ->
                Page(i, document.location(), el.attr("src"))
            }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Os filtros são ignorados na busca!"),
        TagFilter(getTags())
    )

    data class Tag(val name: String, val slug: String) {
        override fun toString(): String = name
    }

    private class TagFilter(tags: Array<Tag>) : Filter.Select<Tag>("Tag", tags)

    private fun getTags(): Array<Tag> = arrayOf(
        Tag("-- Selecione --", ""),
        Tag("Ahegao", "ahegao"),
        Tag("Anal", "anal"),
        Tag("Biquíni", "biquini"),
        Tag("Chubby", "chubby"),
        Tag("Colegial", "colegial"),
        Tag("Creampie", "creampie"),
        Tag("Dark Skin", "dark-skin"),
        Tag("Dupla Penetração", "dupla-penetracao"),
        Tag("Espanhola", "espanhola"),
        Tag("Exibicionismo", "exibicionismo"),
        Tag("Footjob", "footjob"),
        Tag("Furry", "furry"),
        Tag("Futanari", "futanari"),
        Tag("Grupal", "grupal"),
        Tag("Incesto", "incesto"),
        Tag("Lingerie", "lingerie"),
        Tag("MILF", "milf"),
        Tag("Maiô", "maio"),
        Tag("Masturbação", "masturbacao"),
        Tag("Netorare", "netorare"),
        Tag("Oral", "oral"),
        Tag("Peitinhos", "peitinhos"),
        Tag("Preservativo", "preservativo"),
        Tag("Professora", "professora"),
        Tag("Sex Toys", "sex-toys"),
        Tag("Tentáculos", "tentaculos"),
        Tag("Yaoi", "yaoi")
    )

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesNextPageSelector(): String? = throw UnsupportedOperationException("Not used")

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/84.0.4147.89 Safari/537.36"
    }
}
