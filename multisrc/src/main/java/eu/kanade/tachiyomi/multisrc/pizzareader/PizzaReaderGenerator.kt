package eu.kanade.tachiyomi.multisrc.pizzareader

// import generator.ThemeSourceData.MultiLang
import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class PizzaReaderGenerator : ThemeSourceGenerator {

    override val themePkg = "pizzareader"

    override val themeClass = "PizzaReader"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        // Current migrating to this CMS:
        // SingleLang("GTO The Great Site", "https://reader.gtothegreatsite.net", "it", className = "GTO", overrideVersionCode = 1),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            PizzaReaderGenerator().createAll()
        }
    }
}
