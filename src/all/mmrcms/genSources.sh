#!/usr/bin/env bash
echo "My Manga Reader CMS source generator by: nulldev"
# CMS: https://getcyberworks.com/product/manga-reader-cms/

function printHelp() {
    echo "Usage: ./genSources.sh [options]"
    echo ""
    echo "Options:"
    echo "--help: Show this help page"
    echo "--dry-run: Perform a dry run (make no changes)"
    echo "--list: List currently available sources"
    echo "--out <file>: Explicitly specify output file"
}
# Target file
TARGET="src/eu/kanade/tachiyomi/extension/all/mmrcms/GeneratedSources.kt"

# Parse CLI args
while [ $# -gt 0 ]
do
    case "$1" in
        --help)
	    printHelp
	    exit 0
            ;;
        --dry-run) OPT_DRY_RUN=true
            ;;
        --list)
	    OPT_DRY_RUN=true
	    OPT_LIST=true
            ;;
        --out)
	    TARGET="$2"
	    shift
            ;;
        --*)
	    echo "Invalid option $1!"
	    printHelp
	    exit -1
            ;;
        *)
	    echo "Invalid argument $1!"
	    printHelp
	    exit -1
            ;;
    esac
    shift
done

# Change target if performing dry run
if [ "$OPT_DRY_RUN" = true ] ; then
    # Do not warn if dry running because of list
    if ! [ "$OPT_LIST" = true ] ; then
	    echo "Performing a dry run, no changes will be made!"
    fi
    TARGET="/dev/null"
else
    # Delete old sources
    rm "$TARGET"
fi

echo -e "package eu.kanade.tachiyomi.extension.all.mmrcms\n" >> "$TARGET"
echo -e "private typealias MMRSource = MyMangaReaderCMSSource\n" >> "$TARGET"
echo -e "// GENERATED FILE, DO NOT MODIFY!" >> "$TARGET"
echo -e "// Generated on $(date)\n" >> "$TARGET"
echo "val SOURCES = mutableListOf<MMRSource>().apply {" >> "$TARGET"

# lang, name, baseUrl
function gen() {
    if [ "$OPT_LIST" = true ] ; then
        echo "- $(echo "$1" | awk '{print toupper($0)}'): $2"
    else
        echo "Generating source: $2"
        genSource "$1" "$2" "$3" >> "$TARGET"
    fi
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
    # Escape HTML entities
    CATEGORIES=$(echo "$CATEGORIES" | perl -C -MHTML::Entities -pe 'decode_entities($_);')
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
    # Check if latest manga is supported
    LATEST_RESP=$(curl --write-out \\n%{http_code} --silent --output - "$3/filterList?page=1&sortBy=last_release&asc=false")
    SUPPORTS_LATEST="false"
    if [ "${LATEST_RESP##*$'\n'}" -eq "200" ] && [[ "$LATEST_RESP" != *"Whoops, looks like something went wrong"* ]]
    then
        SUPPORTS_LATEST="true"
    fi
    # Remove leftover html page
    rm tmp.html

    echo "    // $2"
    echo "    this += MMRSource("
    echo "            \"$1\","
    echo "            \"$2\","
    echo "            \"$3\","
    echo "            $SUPPORTS_LATEST,"
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
# Went offline
# gen "en" "MangaRoot" "http://mangaroot.com"
gen "en" "Mangawww Reader" "http://mangawww.com"
gen "en" "MangaForLife" "http://manga4ever.com"
gen "en" "Manga Spoil" "http://mangaspoil.com"
gen "en" "H-Manga.moe" "https://h-manga.moe"
# Protected by CloudFlare
# gen "en" "MangaBlue" "http://mangablue.com"
# Advanced search page exists but does not work (unsupported)
# gen "en" "Manga Forest" "https://mangaforest.com"
gen "en" "DManga" "http://dmanga.website"
gen "en" "Chibi Manga Reader" "http://www.cmreader.info"
gen "en" "ZXComic" "http://zxcomic.com"
gen "es" "My-mangas.com" "https://my-mangas.com"
gen "fa" "TrinityReader" "http://trinityreader.pw"
gen "fr" "Manga-LEL" "https://www.manga-lel.com"
gen "fr" "Manga Etonnia" "https://www.etonnia.com"
# Went offline
# gen "fr" "Tous Vos Scans" "http://www.tous-vos-scans.com"
gen "id" "Manga Desu" "http://mangadesu.net"
# Went offline
# gen "id" "Komik Mangafire.ID" "http://go.mangafire.id"
# Went offline
# gen "id" "MangaOnline" "http://mangaonline.web.id"
# Went offline
# gen "id" "MangaNesia" "https://manganesia.com"
# Went offline
# gen "id" "KOMIK.CO.ID" "https://komik.co.id"
gen "id" "MangaID" "http://mangaid.co"
gen "id" "Manga Seru" "http://www.mangaseru.top"
# Went offline
# gen "id" "Indo Manga Reader" "http://indomangareader.com"
# Went offline
# gen "ja" "IchigoBook" "http://ichigobook.com"
gen "ja" "Mangaraw Online" "http://mangaraw.online"
gen "ja" "Mangazuki RAWS" "https://raws.mangazuki.co"
gen "pl" "Candy Scans" "http://csreader.webd.pl"
# Advanced search screen removed (unsupported)
# gen "pt" "Comic Space" "https://www.comicspace.com.br"
gen "pt" "Mangás Yuri" "https://www.mangasyuri.net"
gen "ru" "NAKAMA" "http://nakama.ru"
gen "tr" "MangAoi" "http://mangaoi.com"
gen "tr" "MangaHanta" "http://mangahanta.com"
# NOTE: THIS SOURCE CONTAINS A CUSTOM LANGUAGE SYSTEM (which will be ignored)!
gen "other" "HentaiShark" "http://www.hentaishark.com"

echo "}" >> "$TARGET"

echo "Done!"
