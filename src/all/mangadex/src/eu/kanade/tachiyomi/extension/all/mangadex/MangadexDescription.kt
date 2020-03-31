package eu.kanade.tachiyomi.extension.all.mangadex

class MangadexDescription(private val internalLang: String) {

    val listOfLangs = when (internalLang) {
            "ru" -> RUSSIAN
            "de" -> GERMAN
            "it" -> ITALIAN
            in "es", "mx" -> SPANISH
            in "br", "pt" -> PORTUGESE
            "tr" -> TURKISH
            "fr" -> FRENCH
            "sa" -> ARABIC
            else -> emptyList()
        }

    fun clean(internalLang: String, description: String): String {
        val langList = ALL_LANGS.toMutableList()

        //remove any languages before the ones provided in the langTextToCheck, if no matches or empty
        // just uses the original description, also removes the potential lang from all lang list
        var newDescription = description;
        listOfLangs.forEach { it ->
            newDescription = newDescription.substringAfter(it)
            langList.remove(it)
        }

        // remove any possible languages that remain to get the new description
        langList.forEach { it -> newDescription = newDescription.substringBefore(it) }
        return newDescription
    }

    companion object {
        val ARABIC = listOf("[b][u]Arabic / العربية[/u][/b]")
        val FRENCH = listOf(
            "French - Français:",
            "[b][u]French[/u][/b]",
            "[b][u]French / Fran&ccedil;ais[/u][/b]"
        )
        val GERMAN = listOf("[b][u]German / Deutsch[/u][/b]", "German/Deutsch:")
        val ITALIAN = listOf("[b][u]Italian / Italiano[/u][/b]")
        val PORTUGESE = listOf(
            "[b][u]Portuguese (BR) / Portugu&ecirc;s (BR)[/u][/b]",
            "[b][u]Português / Portuguese[/u][/b]",
            "[b][u]Portuguese / Portugu[/u][/b]"
        )
        val RUSSIAN = listOf("[b][u]Russian / Русский[/u][/b]")
        val SPANISH = listOf("[b][u]Espa&ntilde;ol / Spanish:[/u][/b]")
        val TURKISH = listOf("[b][u]Turkish / T&uuml;rk&ccedil;e[/u][/b]")

        val ALL_LANGS =
            listOf(ARABIC, FRENCH, GERMAN, ITALIAN, PORTUGESE, RUSSIAN, SPANISH, TURKISH).flatten()
    }
}
