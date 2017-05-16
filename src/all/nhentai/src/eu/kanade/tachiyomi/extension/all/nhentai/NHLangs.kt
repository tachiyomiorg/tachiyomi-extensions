package eu.kanade.tachiyomi.extension.all.nhentai

/**
 * NHentai languages
 */

class NHJapanese : NHentai("ja", "japanese")

class NHEnglish : NHentai("en", "english")
class NHChinese : NHentai("zh", "chinese")
class NHSpeechless : NHentai("none", "speechless")
class NHCzech : NHentai("cs", "czech")
class NHEsperanto : NHentai("eo", "esperanto")
class NHMongolian : NHentai("mn", "mongolian")
class NHSlovak : NHentai("sk", "slovak")
class NHArabic : NHentai("ar", "arabic")
class NHUkrainian : NHentai("uk", "ukrainian")

fun getAllNHentaiLanguages() = listOf(
        NHJapanese(),
        NHEnglish(),
        NHChinese(),
        NHSpeechless(),
        NHCzech(),
        NHEsperanto(),
        NHMongolian(),
        NHSlovak(),
        NHArabic(),
        NHUkrainian()
)
