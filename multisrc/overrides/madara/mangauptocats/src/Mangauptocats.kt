package eu.kanade.tachiyomi.extension.th.mangauptocats

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Mangauptocats : Madara("Mangauptocats", "https://mangauptocats.online", "th", SimpleDateFormat("MMMM d, yyyy", Locale("th"))) {
    override fun getGenreList() = listOf(
        Genre("Mecha", "mecha"),
        Genre("Mystery", "mystery"),
        Genre("One shot", "one-shot"),
        Genre("Psychological", "psychological"),
        Genre("Romance", "romance"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo Ai", "shoujo-ai"),
        Genre("Shounen", "shounen"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Smut", "smut"),
        Genre("Tragedy", "tragedy"),
        Genre("Webtoon", "webtoon"),
        Genre("การ์ตูน", "cartoon"),
        Genre("กีฬา", "sports"),
        Genre("คอมเมดี้", "comedy"),
        Genre("ชีวิตในโรงเรียน", "ชีวิตในโรงเรียน"),
        Genre("ดราม่า", "drama"),
        Genre("ต่างโลก", "ต่างโลก"),
        Genre("ทะลึ่ง", "ecchi"),
        Genre("ทำอาหาร", "cooking"),
        Genre("ผจญภัย", "adventure"),
        Genre("มังงะจีน", "manga-chaina"),
        Genre("มังงะญี่ปุ่น", "manga-japan"),
        Genre("มังงะที่จบแล้ว", "มังงะที่จบแล้ว"),
        Genre("มังงะที่ยังไม่จบ", "มังงะที่ยังไม่จบ"),
        Genre("ย้อนยุค", "historical"),
        Genre("ยูริ", "yuri"),
        Genre("วาย", "yaoi"),
        Genre("ศิลปะการต่อสู้", "martial-arts"),
        Genre("สมบทบาท", "live-action"),
        Genre("สยองขวัญ", "horror"),
        Genre("ฮาเร็ม", "harem"),
        Genre("เหนือธรรมชาติ", "supernatural"),
        Genre("แฟนตาซี", "fantasy"),
        Genre("แอคชั่น", "action"),
        Genre("โดจิน", "doujinshi"),
        Genre("Completed", "complete"),
        Genre("Ongoing", "on-going"),
        Genre("Canceled", "canceled"),
        Genre("On Hold", "on-hold")
    )
}
