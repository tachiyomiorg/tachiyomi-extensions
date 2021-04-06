package eu.kanade.tachiyomi.extension.all.webtoonstranslate

import eu.kanade.tachiyomi.multisrc.webtoons.WebtoonsTranslate
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class WebtoonsTranslateFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        WebtoonsTranslateEN(),
        WebtoonsTranslateZH_CMN(),
        WebtoonsTranslateZH_CMY(),
        WebtoonsTranslateTH(),
        WebtoonsTranslateID(),
        WebtoonsTranslateFR(),
        WebtoonsTranslateVI(),
        WebtoonsTranslateRU(),
        WebtoonsTranslateAR(),
        WebtoonsTranslateFIL(),
        WebtoonsTranslateDE(),
        WebtoonsTranslateHI(),
        WebtoonsTranslateIT(),
        WebtoonsTranslateJA(),
        WebtoonsTranslatePT_POR(),
        WebtoonsTranslateTR(),
        WebtoonsTranslateMS(),
        WebtoonsTranslatePL(),
        WebtoonsTranslatePT_POT(),
        WebtoonsTranslateBG(),
        WebtoonsTranslateDA(),
        WebtoonsTranslateNL(),
        WebtoonsTranslateRO(),
        WebtoonsTranslateMN(),
        WebtoonsTranslateEL(),
        WebtoonsTranslateLT(),
        WebtoonsTranslateCS(),
        WebtoonsTranslateSV(),
        WebtoonsTranslateBN(),
        WebtoonsTranslateFA(),
        WebtoonsTranslateUK(),
        WebtoonsTranslateES(),
    )
}
class WebtoonsTranslateEN : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "en", "ENG")
class WebtoonsTranslateZH_CMN : WebtoonsTranslate("Webtoons Translate (Simplified)", "https://translate.webtoons.com", "zh", "CMN")
class WebtoonsTranslateZH_CMY : WebtoonsTranslate("Webtoons Translate (Traditional)", "https://translate.webtoons.com", "zh-hant", "CMT")
class WebtoonsTranslateTH : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "th", "THA")
class WebtoonsTranslateID : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "id", "IND")
class WebtoonsTranslateFR : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "fr", "FRA")
class WebtoonsTranslateVI : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "vi", "VIE")
class WebtoonsTranslateRU : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "ru", "RUS")
class WebtoonsTranslateAR : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "ar", "ARA")
class WebtoonsTranslateFIL : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "fil", "FIL")
class WebtoonsTranslateDE : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "de", "DEU")
class WebtoonsTranslateHI : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "hi", "HIN")
class WebtoonsTranslateIT : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "it", "ITA")
class WebtoonsTranslateJA : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "ja", "JPN")
class WebtoonsTranslatePT_POR : WebtoonsTranslate("Webtoons Translate (Brazilian)", "https://translate.webtoons.com", "pt-br", "POR")
class WebtoonsTranslateTR : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "tr", "TUR")
class WebtoonsTranslateMS : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "ms", "MAY")
class WebtoonsTranslatePL : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "pl", "POL")
class WebtoonsTranslatePT_POT : WebtoonsTranslate("Webtoons Translate (European)", "https://translate.webtoons.com", "pt", "POT")
class WebtoonsTranslateBG : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "bg", "BUL")
class WebtoonsTranslateDA : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "da", "DAN")
class WebtoonsTranslateNL : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "nl", "NLD")
class WebtoonsTranslateRO : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "ro", "RON")
class WebtoonsTranslateMN : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "mn", "MON")
class WebtoonsTranslateEL : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "el", "GRE")
class WebtoonsTranslateLT : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "lt", "LIT")
class WebtoonsTranslateCS : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "cs", "CES")
class WebtoonsTranslateSV : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "sv", "SWE")
class WebtoonsTranslateBN : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "bn", "BEN")
class WebtoonsTranslateFA : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "fa", "PER")
class WebtoonsTranslateUK : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "uk", "UKR")
class WebtoonsTranslateES : WebtoonsTranslate("Webtoons Translate", "https://translate.webtoons.com", "es", "SPA")


