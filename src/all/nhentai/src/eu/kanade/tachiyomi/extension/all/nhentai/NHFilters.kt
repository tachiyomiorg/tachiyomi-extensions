package eu.kanade.tachiyomi.extension.all.nhentai

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter : Filter.Select<String>("Sort", arrayOf("Popular", "Date"))