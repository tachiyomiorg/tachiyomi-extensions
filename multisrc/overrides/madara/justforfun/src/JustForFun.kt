package eu.kanade.tachiyomi.extension.en.justforfun

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class JustForFun : Madara(
        "Just For Fun",
        "https://just-for-fun.ru",
        "ru",
        dateFormat = SimpleDateFormat("yy.MM.dd", Locale.US)
)