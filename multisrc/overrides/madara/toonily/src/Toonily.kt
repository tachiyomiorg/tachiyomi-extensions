package eu.kanade.tachiyomi.extension.en.toonily

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.annotations.Nsfw

@Nsfw
class Toonily : Madara("Toonily", "https://toonily.com", "en") {
    override fun getGenreList(): List<Genre> = listOf(
        Genre("Action", "action-webtoon"),
        Genre("Adult", "adult-webtoon"),
        Genre("Adventure", "adventure-webtoon"),
        Genre("Comedy", "comedy-webtoon"),
        Genre("Drama", "drama-webtoon"),
        Genre("Fantasy", "fantasy-webtoon"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Gossip", "gossip"),
        Genre("Harem", "harem-webtoon"),
        Genre("Historical", "webtoon-historical"),
        Genre("Horror", "horror-webtoon"),
        Genre("Josei", "josei-manga"),
        Genre("Mature", "mature-webtoon"),
        Genre("Mystery", "mystery-webtoon"),
        Genre("NTR", "ntr-webtoon"),
        Genre("Psychological", "psychological-webtoon"),
        Genre("Romance", "romance-webtoon"),
        Genre("School life", "school-life-webtoon"),
        Genre("Sci-Fi", "scifi-webtoon"),
        Genre("Seinen", "seinen-webtoon"),
        Genre("Shoujo", "shoujo"),
        Genre("Shounen", "shounen-webtoon"),
        Genre("Slice of Life", "sliceoflife-webtoon"),
        Genre("Supernatural", "supernatural-webtoon"),
        Genre("Thriller", "thriller-webtoon"),
        Genre("Tragedy", "tragedy"),
        Genre("Vanilla", "vanilla-webtoon"),
        Genre("Yaoi", "yaoi-webtoon"),
        Genre("Yuri", "yuri-webtoon")
    )
}
