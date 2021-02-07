package eu.kanade.tachiyomi.extension.tr.araznovel

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class ArazNovel : Madara("ArazNovel", "https://www.araznovel.com", "tr", SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())) {
    override fun formBuilder(page: Int, popular: Boolean): FormBody.Builder = super.formBuilder(page, popular)
        .add("vars[meta_query][0][0][value]", "manga")

    override fun getGenreList() = listOf(
        Genre("Aksiyon", "action"),
        Genre("Macera", "adventure"),
        Genre("Cartoon", "cartoon"),
        Genre("Comic", "comic"),
        Genre("Komedi", "comedy"),
        Genre("Yemek", "cooking"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Dram", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Fantastik", "fantasy"),
        Genre("Harem", "harem"),
        Genre("Tarihi", "historical"),
        Genre("Korku", "horror"),
        Genre("Manga", "manga"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Olgun", "mature"),
        Genre("Mecha", "mecha"),
        Genre("Yetişkin", "adult"),
        Genre("Gizem", "mystery"),
        Genre("One Shot", "one-shot"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Dedektif", "detective"),
        Genre("Karanlık", "smut"),
        Genre("Romantizm", "romance"),
        Genre("Okul Yaşamı", "school-life"),
        Genre("Yaşamdan Kesit", "slice-of-life"),
        Genre("Spor", "sports"),
        Genre("Doğa Üstü", "supernatural"),
        Genre("Trajedi", "tragedy"),
        Genre("Webtoon ", "webtoon"),
        Genre("Dövüş Sanatları ", "martial-arts"),
        Genre("Bilim Kurgu", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo Ai", "shoujo-ai"),
        Genre("Shounen", "shounen"),
        Genre("Shounen Ai", "shounen-ai"),
        Genre("Soft Yaoi", "soft-yaoi"),
        Genre("Soft Yuri", "soft-yuri"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri")
    )

    override fun chapterListParse(response: Response): List<SChapter> {
        return getXhrChapters(response.asJsoup().select("div#manga-chapters-holder").attr("data-id")).let { document ->
            document.select("li.parent").let { elements ->
                if (!elements.isNullOrEmpty()) {
                    elements.reversed()
                        .map { volumeElement -> volumeElement.select(chapterListSelector()).map { chapterFromElement(it) } }
                        .flatten()
                } else {
                    document.select(chapterListSelector()).map { chapterFromElement(it) }
                }
            }
        }
    }
}
