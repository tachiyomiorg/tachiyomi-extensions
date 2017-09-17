#!/usr/bin/env bash
echo "My Manga Reader CMS source generator by: nulldev"
# CMS: https://getcyberworks.com/product/manga-reader-cms/

# Target file
TARGET=GeneratedSources.kt

# Delete old sources
rm "$TARGET"

echo -e "package eu.kanade.tachiyomi.extension.all.mmrcms\n" >> $TARGET
echo -e "private typealias MMRSource = MyMangaReaderCMSSource\n" >> $TARGET
echo -e "// GENERATED FILE, DO NOT MODIFY!" >> $TARGET
echo -e "// Generated on $(date)\n" >> $TARGET
echo "val SOURCES = mutableListOf<MMRSource>().apply {" >> $TARGET

# lang, name, baseUrl
function gen() {
    echo "Generating source: $2"
    genSource "$1" "$2" "$3" >> $TARGET
    #echo "- $(echo "$1" | awk '{print toupper($0)}'): $2"
}

function getItemUrl() {
    grep -oP "(?<=showURL = \")(.*)(?=SELECTION)" tmp.html
}

function echoErr () {
    echo "$@" >&2
}

# lang, name, baseUrl
function genSource() {
    # Fetch categories
    wget "$3/advanced-search" -O tmp.html
    # Find and transform categories
    CATEGORIES="$(xmllint --xpath "//select[@name='categories[]']/option" --html tmp.html 2>/dev/null |\
        sed 's/<\/option>/\")\n/g; s/<option value=\"/                this += Pair(\"/g; s/\">/\", \"/g;')"
    # Find manga/comic URL
    ITEM_URL="$(getItemUrl)"
    # Get from home page if not on advanced search page!
    if [ -z "$ITEM_URL" ]
    then
        wget "$3" -O tmp.html
        ITEM_URL="$(getItemUrl)"
        # Still missing?
        if [ -z "$ITEM_URL" ]
        then
            echoErr "Could not get item URL!"
            exit -1
        fi
    fi
    # Remove leftover html page
    rm tmp.html

    echo "    // $2"
    echo "    this += MMRSource("
    echo "            \"$1\","
    echo "            \"$2\","
    echo "            \"$3\","
    echo "            \"$ITEM_URL\","
    echo "            mutableListOf<Pair<String, String>>().apply {"
    echo "$CATEGORIES"
    echo "            }"
    echo "    )"
}

# Source list
gen "ar" "مانجا اون لاين" "http://on-manga.com"
gen "en" "Read Comics Online" "http://readcomics.website"
gen "en" "Fallen Angels Scans" "http://manga.fascans.com"
gen "en" "MangaRoot" "http://mangaroot.com"
gen "en" "Mangawww Reader" "http://mangawww.com"
gen "en" "MangaForLife" "http://manga4ever.com"
gen "en" "Manga Mofo" "http://mangamofo.com"
gen "en" "H-Manga.moe" "https://h-manga.moe"
gen "en" "MangaBlue" "http://mangablue.com"
gen "en" "Manga Forest" "https://mangaforest.com"
gen "en" "DManga" "http://dmanga.website"
gen "es" "My-mangas.com" "https://my-mangas.com"
gen "fa" "TrinityReader" "http://trinityreader.pw"
gen "fr" "Manga-LEL" "https://www.manga-lel.com"
gen "fr" "Manga Etonnia" "https://www.etonnia.com"
gen "fr" "Tous Vos Scans" "http://www.tous-vos-scans.com"
gen "id" "Manga Desu" "http://mangadesu.net"
gen "id" "Komik Mangafire.ID" "http://go.mangafire.id"
gen "id" "MangaOnline" "http://mangaonline.web.id"
gen "id" "MangaNesia" "https://manganesia.com"
gen "id" "KOMIK.CO.ID" "https://komik.co.id"
gen "id" "MangaID" "http://mangaid.co"
gen "id" "Indo Manga Reader" "http://indomangareader.com"
gen "ja" "IchigoBook" "http://ichigobook.com"
gen "ja" "Mangaraw Online" "http://mangaraw.online"
gen "pl" "Candy Scans" "http://csreader.webd.pl"
gen "pt" "Comic Space" "https://www.comicspace.com.br"
gen "pt" "Mangás Yuri" "https://www.mangasyuri.net"
gen "ru" "NAKAMA" "http://nakama.ru"
gen "tr" "MangAoi" "http://mangaoi.com"
gen "tr" "MangaHanta" "http://mangahanta.com"

echo "}" >> $TARGET

echo "Done!"