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
    CATEGORIES=$(xmllint --xpath "//select[@name='categories[]']/option" --html tmp.html 2>/dev/null |\
        sed 's/<\/option>/\")\n/g; s/<option value=\"/                this += Pair(\"/g; s/\">/\", \"/g;')
    # Find manga/comic URL
    ITEM_URL=$(getItemUrl)
    # Get from home page if not on advanced search page!
    if [ -z "$ITEM_URL" ]
    then
        wget "$3" -O tmp.html
        ITEM_URL=$(getItemUrl)
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
gen "en" "Read Comics Online" "http://readcomics.website"
gen "en" "Fallen Angels Scans" "http://manga.fascans.com"
gen "en" "MangaRoot" "http://mangaroot.com"
gen "en" "Mangawww Reader" "http://mangawww.com"
gen "en" "MangaForLife" "http://manga4ever.com"
gen "es" "My-mangas.com" "https://my-mangas.com"
gen "fa" "TrinityReader" "http://trinityreader.pw"
gen "id" "Manga Desu" "http://mangadesu.net"
gen "ja" "IchigoBook" "http://ichigobook.com"
gen "tr" "MangAoi" "http://mangaoi.com"

echo "}" >> $TARGET

echo "Done!"