#!/usr/bin/env bash
echo "My Manga Reader CMS source generator by: nulldev"
# CMS: https://getcyberworks.com/product/manga-reader-cms/

# Print a message out to stderr
function echoErr() {
	echo "ERROR: $@" >&2
}

# Require that a command exists before continuing
function require() {
	command -v $1 >/dev/null 2>&1 || { echoErr "This script requires $1 but it's not installed."; exit 1; }
}

# Define commands that this script depends on
require xmllint
require jq
require perl
require wget
require curl
require grep
require sed

# Show help/usage info
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
# String containing processed URLs (used to detect duplicate URLs)
PROCESSED=""

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

# Variable used to store output while processing
QUEUED_SOURCES="["

# lang, name, baseUrl
function gen() {
	PROCESSED="$PROCESSED$3\n"
	if [ "$OPT_LIST" = true ] ; then
		echo "- $(echo "$1" | awk '{print toupper($0)}'): $2"
	else
		echo "Generating source: $2"
		QUEUED_SOURCES="$QUEUED_SOURCES"$'\n'"$(genSource "$1" "$2" "$3")"
		# genSource runs in a subprocess, so we check for bad exit code and exit current process if necessary
		[ $? -ne 0 ] && exit -1;
	fi
}

# Find and get the item URL from an HTML page
function getItemUrl() {
	grep -oP "(?<=showURL = \")(.*)(?=SELECTION)" "$1"
}

# Strip all scripts and Cloudflare email protection from page
# We strip Cloudflare email protection as titles like 'IDOLM@STER' can trigger it and break the parser
function stripScripts() {
	perl -0pe 's/<script.*?>[\s\S]*?< *?\/ *?script *?>//g' |\
		perl -0pe 's/<span class="__cf_email__".*?>[\s\S]*?< *?\/ *?span *?>/???@???/g'
}

# Verify that a response is valid
function verifyResponse() {
	[ "${1##*$'\n'}" -eq "200" ] && [[ "$1" != *"Whoops, looks like something went wrong"* ]]
}

# Get the available tags from the manga list page
function parseTagsFromMangaList() {
	xmllint --xpath "//div[contains(@class, 'tag-links')]//a" --html "$1" 2>/dev/null |\
		sed 's/<\/a>/"},\n/g; s/">/", "name": "/g;' |\
		perl -pe 's/<a.*?\/tag\//            {"id": "/gi;' |\
		sed '/^</d'
}

# Get the available categories from the manga list page
function parseCategoriesFromMangaList() {
	xmllint --xpath "//li//a[contains(@class, 'category')]" --html "$1" 2>/dev/null |\
		sed 's/<\/a>/"},\n/g; s/" class="category">/", "name": "/g;' |\
		perl -pe 's/<a.*?\?cat=/            {"id": "/gi;'
}

# Get the available categories from the advanced search page
function parseCategoriesFromAdvancedSearch() {
	xmllint --xpath "//select[@name='categories[]']/option" --html "$1" 2>/dev/null |\
		sed 's/<\/option>/"},\n/g; s/<option value="/            {"id": "/g; s/">/", "name": "/g;'
}

# Unescape HTML entities
function unescapeHtml() {
	echo "$1" | perl -C -MHTML::Entities -pe 'decode_entities($_);'
}

# Remove the last character from a string, often used to remove the trailing comma
function stripLastComma() {
	echo "${1::-1}"
}

# lang, name, baseUrl
function genSource() {
	# Allocate temp files
	DL_TMP="$(mktemp)"
	PG_TMP="$(mktemp)"

	# Fetch categories from advanced search
	wget "$3/advanced-search" -O "$DL_TMP"
	# Find manga/comic URL
	ITEM_URL="$(getItemUrl "$DL_TMP")"
	# Remove scripts
	cat "$DL_TMP" | stripScripts > "$PG_TMP"
	# Find and transform categories
	CATEGORIES="$(parseCategoriesFromAdvancedSearch "$PG_TMP")"
	# Get item url from home page if not on advanced search page!
	if [[ -z "${ITEM_URL// }" ]]; then
		# Download home page
		wget "$3" -O "$DL_TMP"
		# Extract item url again
		ITEM_URL="$(getItemUrl "$DL_TMP")"
		# Still missing?
		if [[ -z "${ITEM_URL// }" ]]; then
			echoErr "Could not get item URL!"
			exit -1
		fi
	fi

	# Calculate location of manga list page
	LIST_URL_PREFIX="manga"
	# Get last path item in item URL and set as URL prefix
	if [[ $ITEM_URL =~ .*\/([^\\]+)\/ ]]; then
		LIST_URL_PREFIX="${BASH_REMATCH[1]}"
	fi
	# Download manga list page
	wget "$3/$LIST_URL_PREFIX-list" -O "$DL_TMP"
	# Remove scripts
	cat "$DL_TMP" | stripScripts > "$PG_TMP"

	# Get categories from manga list page if we couldn't from advanced search
	if [[ -z "${CATEGORIES// }" ]]; then
		# Parse
		CATEGORIES="$(parseCategoriesFromMangaList "$PG_TMP")"
		# Check again
		if [[ -z "${CATEGORIES// }" ]]; then
			echoErr "Could not get categories!"
			exit -1
		fi
	fi

	# Get tags from manga list page
	TAGS="$(parseTagsFromMangaList "$PG_TMP")"
	if [[ -z "${TAGS// }" ]]; then
		TAGS="null"
	else
		TAGS="$(stripLastComma "$TAGS")"
		TAGS=$'[\n'"$TAGS"$'\n        ]'
	fi

	# Unescape HTML entities
	CATEGORIES="$(unescapeHtml "$CATEGORIES")"
	# Check if latest manga is supported
	LATEST_RESP=$(curl --write-out \\n%{http_code} --silent --output - "$3/filterList?page=1&sortBy=last_release&asc=false")
	SUPPORTS_LATEST="false"
	if verifyResponse "$LATEST_RESP"; then
		SUPPORTS_LATEST="true"
	fi
	# Remove leftover html pages
	rm "$DL_TMP"
	rm "$PG_TMP"

	# Cleanup categories
	CATEGORIES="$(stripLastComma "$CATEGORIES")"

	echo "    {"
	echo "        \"language\": \"$1\","
	echo "        \"name\": \"$2\","
	echo "        \"base_url\": \"$3\","
	echo "        \"supports_latest\": $SUPPORTS_LATEST,"
	echo "        \"item_url\": \"$ITEM_URL\","
	echo "        \"categories\": ["
	echo "$CATEGORIES"
	echo "        ],"
	echo "        \"tags\": $TAGS"
	echo "    },"
}

# Source list
gen "ar" "مانجا اون لاين" "http://www.on-manga.com"
gen "ar" "Manga FYI" "http://mangafyi.com/manga/arabic"
gen "en" "Read Comics Online" "http://readcomicsonline.ru"
gen "en" "Fallen Angels Scans" "http://manga.fascans.com"
# Went offline
# gen "en" "MangaRoot" "http://mangaroot.com"
gen "en" "Mangawww Reader" "http://mangawww.com"
# Went offline
# gen "en" "MangaForLife" "http://manga4ever.com"
gen "en" "Manga Spoil" "http://mangaspoil.com"
# Protected by CloudFlare
# gen "en" "MangaBlue" "http://mangablue.com"
gen "en" "Manga Forest" "https://mangaforest.com"
# Went offline
# gen "en" "DManga" "http://dmanga.website"
gen "en" "Chibi Manga Reader" "http://www.cmreader.info"
gen "en" "ZXComic" "http://zxcomic.com"
# Went offline
# gen "en" "DB Manga" "http://dbmanga.com"
gen "en" "Mangacox" "http://mangacox.com"
# Protected by CloudFlare
# gen "en" "GO Manhwa" "http://gomanhwa.xyz"
# Went offline
# gen "en" "KoManga" "https://komanga.net"
# Went offline
# gen "en" "Manganimecan" "http://manganimecan.com"
gen "en" "Hentai2Manga" "http://hentai2manga.com"
gen "en" "White Cloud Pavilion" "http://www.whitecloudpavilion.com/manga/free"
gen "en" "4 Manga" "http://4-manga.com"
gen "en" "XYXX.INFO" "http://xyxx.info"
gen "es" "My-mangas.com" "https://my-mangas.com"
gen "es" "SOS Scanlation" "https://sosscanlation.com"
gen "es" "Doujin Hentai" "http://doujinhentai.net"
# Went offline
# gen "fa" "TrinityReader" "http://trinityreader.pw"
gen "fr" "Manga-LEL" "https://www.manga-lel.com"
gen "fr" "Manga Etonnia" "https://www.etonnia.com"
gen "fr" "Scan FR" "http://www.scan-fr.net" # Also available here: http://www.scan-fr.io
# Went offline
# gen "fr" "ScanFR.com" "http://scanfr.com"
gen "fr" "Manga FYI" "http://mangafyi.com/manga/french"
gen "fr" "Mugiwara" "http://mugiwara.be"
gen "fr" "scans-manga" "http://scans-manga.com"
gen "fr" "Henka no Kaze" "http://henkanokazelel.esy.es/upload"
# Went offline
# gen "fr" "Tous Vos Scans" "http://www.tous-vos-scans.com"
# Went offline
# gen "id" "Manga Desu" "http://mangadesu.net"
# Went offline
# gen "id" "Komik Mangafire.ID" "http://go.mangafire.id"
gen "id" "MangaOnline" "http://mangaonline.web.id"
# Went offline
# gen "id" "MangaNesia" "https://manganesia.com"
gen "id" "Komikid" "http://www.komikid.com"
gen "id" "MangaID" "http://mangaid.co"
gen "id" "Manga Seru" "http://www.mangaseru.top"
gen "id" "Manga FYI" "http://mangafyi.com/manga/indonesian"
gen "id" "Bacamangaku" "http://www.bacamangaku.com"
# Went offline
# gen "id" "Indo Manga Reader" "http://indomangareader.com"
# Protected by Cloudflare
# gen "it" "Kingdom Italia Reader" "http://kireader.altervista.org"
# Went offline
# gen "ja" "IchigoBook" "http://ichigobook.com"
# Went offline
# gen "ja" "Mangaraw Online" "http://mangaraw.online"
gen "ja" "Mangazuki RAWS" "https://raws.mangazuki.co"
# Went offline
# gen "ja" "MangaRAW" "https://www.mgraw.com"
gen "ja" "マンガ/漫画 マガジン/雑誌 raw" "http://netabare-manga-raw.com"
gen "pl" "ToraScans" "http://torascans.pl"
gen "pt" "Comic Space" "https://www.comicspace.com.br"
gen "pt" "Mangás Yuri" "https://mangasyuri.net"
gen "ru" "NAKAMA" "http://nakama.ru"
# Went offline
# gen "tr" "MangAoi" "http://mangaoi.com"
gen "tr" "MangaHanta" "http://mangahanta.com"
gen "tr" "ManhuaTR" "http://www.manhua-tr.com"
gen "vi" "Fallen Angels Scans" "http://truyen.fascans.com"
# Blocks bots (like this one)
# gen "tr" "Epikmanga" "http://www.epikmanga.com"
# NOTE: THIS SOURCE CONTAINS A CUSTOM LANGUAGE SYSTEM (which will be ignored)!
gen "other" "HentaiShark" "https://www.hentaishark.com"

if ! [ "$OPT_LIST" = true ] ; then
	# Remove last comma from output
	QUEUED_SOURCES="$(stripLastComma "$QUEUED_SOURCES")"
	# Format, minify and split JSON output into chunks of 5000 chars
	OUTPUT="$(echo -e "$QUEUED_SOURCES\n]" | jq -c . | fold -s -w5000)"
	# Write file header
	echo -e "package eu.kanade.tachiyomi.extension.all.mmrcms\n" >> "$TARGET"
	echo -e "// GENERATED FILE, DO NOT MODIFY!" >> "$TARGET"
	echo -e "// Generated on $(date)\n" >> "$TARGET"
	# Convert split lines into variables
	COUNTER=0
	CONCAT="val SOURCES: String get() = "
	TOTAL_LINES="$(echo "$OUTPUT" | wc -l)"
	while read -r line; do
		COUNTER=$[$COUNTER +1]
		VARNAME="MMRSOURCE_$COUNTER"
		echo "private val $VARNAME = \"\"\"$line\"\"\"" >> "$TARGET"
		CONCAT="$CONCAT$VARNAME"
		if [ "$COUNTER" -ne "$TOTAL_LINES" ]; then
			CONCAT="$CONCAT + "
		fi
	done <<< "$OUTPUT"
	echo "$CONCAT" >> "$TARGET"
fi

# Detect and warn about duplicate sources
DUPES="$(echo -e "$PROCESSED" | sort | uniq -d)"
if [[ ! -z "$DUPES" ]]; then
	echo
	echo "----> WARNING, DUPLICATE SOURCES DETECTED! <----"
	echo "Listing duplicates:"
	echo "$DUPES"
	echo
fi

echo "Done!"
