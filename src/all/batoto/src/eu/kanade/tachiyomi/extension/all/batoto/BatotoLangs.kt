package eu.kanade.tachiyomi.extension.all.batoto

/**
 * Batoto languages
 */
class BEnglish: Batoto("en", "English")
class BSpanish: Batoto("es", "Spanish")
class BFrench: Batoto("fr", "French")
class BGerman: Batoto("de", "German")
class BPortuguese: Batoto("pt", "Portuguese")
class BTurkish: Batoto("tr", "Turkish")
class BIndonesian: Batoto("id", "Indonesian")
class BGreek: Batoto("el", "Greek")
class BFilipino: Batoto("fil", "Filipino")
class BPolish: Batoto("pl", "Polish")
class BThai: Batoto("th", "Thai")
class BMalay: Batoto("ms", "Malay")
class BHungarian: Batoto("hu", "Hungarian")
class BRomanian: Batoto("rm", "Romanian")
class BArabic: Batoto("ar", "Arabic")
class BHebrew: Batoto("he", "Hebrew")
class BRussian: Batoto("ru", "Russian")
class BVietnamese: Batoto("vi", "Vietnamese")
class BDutch: Batoto("nl", "Dutch")
class BBengali: Batoto("bn", "Bengali")
class BPersian: Batoto("fa", "Persian")
class BCzech: Batoto("cs", "Czech")
class BBrazilian: Batoto("pt", "Brazilian")
class BBulgarian: Batoto("bg", "Bulgarian")
class BDanish: Batoto("da", "Danish")
class BEsperanto: Batoto("eo", "Esperanto")
class BSwedish: Batoto("sv", "Swedish")
class BLithuanian: Batoto("lt", "Lithuanian")
class BOther: Batoto("other", "Other")


fun getAllBatotoLanguages() = listOf(
        BEnglish(),
        BSpanish(),
        BFrench(),
        BGerman(),
        BPortuguese(),
        BTurkish(),
        BIndonesian(),
        BGreek(),
        BFilipino(),
        BPolish(),
        BThai(),
        BMalay(),
        BHungarian(),
        BRomanian(),
        BArabic(),
        BHebrew(),
        BRussian(),
        BVietnamese(),
        BDutch(),
        BBengali(),
        BPersian(),
        BCzech(),
        BBrazilian(),
        BBulgarian(),
        BDanish(),
        BEsperanto(),
        BSwedish(),
        BLithuanian(),
        BOther()

)