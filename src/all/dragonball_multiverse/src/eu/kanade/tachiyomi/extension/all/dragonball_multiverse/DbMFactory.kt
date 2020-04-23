@file:Suppress("ClassName")

package eu.kanade.tachiyomi.extension.all.dragonball_multiverse

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class DbMFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        DbMultiverseEN(),
        DbMultiverseFR(),
        DbMultiverseJP(),
        DbMultiverseCN(),
        DbMultiverseES(),
        DbMultiverseIT(),
        DbMultiversePT(),
        DbMultiverseDE(),
        DbMultiversePL(),
        DbMultiverseNL(),
        DbMultiverseFR_PA(),
        DbMultiverseTR_TR(),
        DbMultiversePT_BR(),
        DbMultiverseHU_HU(),
        DbMultiverseGA_ES(),
        DbMultiverseCT_CT(),
        DbMultiverseNO_NO(),
        DbMultiverseRU_RU(),
        DbMultiverseRO_RO(),
        DbMultiverseEU_EH(),
        DbMultiverseLT_LT(),
        DbMultiverseHR_HR(),
        DbMultiverseKR_KR(),
        DbMultiverseFI_FI(),
        DbMultiverseHE_HE(),
        DbMultiverseBG_BG(),
        DbMultiverseSV_SE(),
        DbMultiverseGR_GR(),
        DbMultiverseES_CO(),
        DbMultiverseAR_JO(),
        DbMultiverseTL_PI(),
        DbMultiverseLA_LA(),
        DbMultiverseDA_DK(),
        DbMultiverseCO_FR(),
        DbMultiverseBR_FR(),
        DbMultiverseXX_VE()
    )
}

class DbMultiverseEN : DbMultiverse("en")
class DbMultiverseFR : DbMultiverse("fr")
class DbMultiverseJP : DbMultiverse("jp")
class DbMultiverseCN : DbMultiverse("cn")
class DbMultiverseES : DbMultiverse("es")
class DbMultiverseIT : DbMultiverse("it")
class DbMultiversePT : DbMultiverse("pt")
class DbMultiverseDE : DbMultiverse("de")
class DbMultiversePL : DbMultiverse("pl")
class DbMultiverseNL : DbMultiverse("nl")
class DbMultiverseFR_PA : DbMultiverse("fr-PA")
class DbMultiverseTR_TR : DbMultiverse("tr-TR")
class DbMultiversePT_BR : DbMultiverse("pt-BR")
class DbMultiverseHU_HU : DbMultiverse("hu-HU")
class DbMultiverseGA_ES : DbMultiverse("ga-ES")
class DbMultiverseCT_CT : DbMultiverse("ct-CT")
class DbMultiverseNO_NO : DbMultiverse("no-NO")
class DbMultiverseRU_RU : DbMultiverse("ru-RU")
class DbMultiverseRO_RO : DbMultiverse("ro-RO")
class DbMultiverseEU_EH : DbMultiverse("eu-EH")
class DbMultiverseLT_LT : DbMultiverse("lt-LT")
class DbMultiverseHR_HR : DbMultiverse("hr-HR")
class DbMultiverseKR_KR : DbMultiverse("kr-KR")
class DbMultiverseFI_FI : DbMultiverse("fi-FI")
class DbMultiverseHE_HE : DbMultiverse("he-HE")
class DbMultiverseBG_BG : DbMultiverse("bg-BG")
class DbMultiverseSV_SE : DbMultiverse("sv-SE")
class DbMultiverseGR_GR : DbMultiverse("gr-GR")
class DbMultiverseES_CO : DbMultiverse("es-CO")
class DbMultiverseAR_JO : DbMultiverse("ar-JO")
class DbMultiverseTL_PI : DbMultiverse("tl-PI")
class DbMultiverseLA_LA : DbMultiverse("la-LA")
class DbMultiverseDA_DK : DbMultiverse("da-DK")
class DbMultiverseCO_FR : DbMultiverse("co-FR")
class DbMultiverseBR_FR : DbMultiverse("br-FR")
class DbMultiverseXX_VE : DbMultiverse("xx-VE")
