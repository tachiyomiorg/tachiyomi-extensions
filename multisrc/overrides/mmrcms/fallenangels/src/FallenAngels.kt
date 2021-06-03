package eu.kanade.tachiyomi.extension.pt.gekkouscan

import eu.kanade.tachiyomi.multisrc.mmrcms.MMRCMS

class FallenAngels : MMRCMS("Fallen Angels", "https://manga.fascans.com", "en") {

    /**
     * Parses the response from the site and returns a list of chapters.
     *
     * Overriden to allow for null chapters
     *
     * @param response the response from the site.
     */
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).mapNotNull { nullableChapterFromElement(it) }
    }

    /**
     * Returns a chapter from the given element.
     *
     * @param element an element obtained from [chapterListSelector].
     */
    override fun nullableChapterFromElement(element: Element): SChapter? {
        val chapter = SChapter.create()

        try {
            val titleWrapper = element.select("[class^=chapter-title-rtl]").first()
            val chapterElement = titleWrapper.getElementsByTag("a")
                .first { it.attr("href").contains(urlRegex) }
            val url = chapterElement.attr("href")

            chapter.url = getUrlWithoutBaseUrl(url)

            // Construct chapter names
            // before -> <mangaName> <chapterNumber> : <chapterTitle>
            // after  -> Chapter     <chapterNumber> : <chapterTitle>
            val chapterText = chapterElement.text()
            val numberRegex = Regex("""\d+""")
            val chapterNumber = numberRegex.find(chapterText)
            val chapterTitle = chapterText.substringAfter(":")
            chapter.name = "Chapter $chapterNumber : $chapterTitle"// titleWrapper.text()

            // Parse date
            val dateText = element.getElementsByClass("date-chapter-title-rtl").text().trim()
            chapter.date_upload = parseDate(dateText)

            return chapter
        } catch (e: NullPointerException) {
            // For chapter list in a table
            if (element.select("td").hasText()) {
                element.select("td a").let {
                    chapter.setUrlWithoutDomain(it.attr("href"))
                    chapter.name = it.text()
                }
                val tableDateText = element.select("td + td").text()
                chapter.date_upload = parseDate(tableDateText)

                return chapter
            }
        }

        return null
    }

}
