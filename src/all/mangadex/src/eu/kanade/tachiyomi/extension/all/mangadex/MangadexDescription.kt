package eu.kanade.tachiyomi.extension.all.mangadex

enum class MangadexDescription(val headers: List<String>) {
    ARABIC(listOf("[b][u]Arabic / العربية[/u][/b]")),
    FRENCH(
        listOf(
            "French - Français:",
            "[b][u]French[/u][/b]",
            "[b][u]French / Fran&ccedil;ais[/u][/b]"
        )
    ),
    GERMAN(listOf("[b][u]German / Deutsch[/u][/b]", "German/Deutsch:")),
    ITALIAN(listOf("[b][u]Italian / Italiano[/u][/b]")),
    PORTUGESE(
        listOf(
            "[b][u]Portuguese (BR) / Portugu&ecirc;s (BR)[/u][/b]",
            "[b][u]Português / Portuguese[/u][/b]",
            "[b][u]Portuguese / Portugu[/u][/b]"
        )
    ),
    RUSSIAN(listOf("[b][u]Russian / Русский[/u][/b]")),
    SPANISH(listOf("[b][u]Espa&ntilde;ol / Spanish:[/u][/b]")),
    TURKISH(listOf("[b][u]Turkish / T&uuml;rk&ccedil;e[/u][/b]"))
}
