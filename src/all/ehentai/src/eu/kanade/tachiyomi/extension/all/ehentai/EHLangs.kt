package eu.kanade.tachiyomi.extension.all.ehentai

/**
 * E-Hentai languages
 */
class EHJapanese : EHentai("ja", "japanese")
class EHEnglish : EHentai("en", "english")
class EHChinese : EHentai("zh", "chinese")
class EHDutch : EHentai("nl", "dutch")
class EHFrench : EHentai("fr", "french")
class EHGerman : EHentai("de", "german")
class EHHungarian : EHentai("hu", "hungarian")
class EHItalian : EHentai("it", "italian")
class EHKorean : EHentai("ko", "korean")
class EHPolish : EHentai("pl", "polish")
class EHPortuguese : EHentai("pt", "portuguese")
class EHRussian : EHentai("ru", "russian")
class EHSpanish : EHentai("es", "spanish")
class EHThai : EHentai("th", "thai")
class EHVietnamese : EHentai("vi", "vietnamese")
class EHSpeechless : EHentai("none", "n/a")
class EHOther : EHentai("other", "other")

fun getAllEHentaiLanguages() = listOf(
        EHJapanese(),
        EHEnglish(),
        EHChinese(),
        EHDutch(),
        EHFrench(),
        EHGerman(),
        EHHungarian(),
        EHItalian(),
        EHKorean(),
        EHPolish(),
        EHPortuguese(),
        EHRussian(),
        EHSpanish(),
        EHThai(),
        EHVietnamese(),
        EHSpeechless(),
        EHOther()
)
