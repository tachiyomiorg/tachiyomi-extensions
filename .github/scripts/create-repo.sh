#!/bin/bash
set -e

TOOLS="$(ls -d ${ANDROID_HOME}/build-tools/* | tail -1)"

mkdir -p repo/apk
mkdir -p repo/icon

cp -f apk/* repo/apk

cd repo

APKS=( ../apk/*".apk" )

for APK in ${APKS[@]}; do
    FILENAME=$(basename ${APK})
    BADGING="$(${TOOLS}/aapt dump --include-meta-data badging $APK)"

    PACKAGE=$(echo "$BADGING" | grep package:)
    PKGNAME=$(echo $PACKAGE | grep -Po "package: name='\K[^']+")
    VCODE=$(echo $PACKAGE | grep -Po "versionCode='\K[^']+")
    VNAME=$(echo $PACKAGE | grep -Po "versionName='\K[^']+")
    NSFW=$(echo $BADGING | grep -Po "tachiyomi.extension.nsfw' value='\K[^']+")

    APPLICATION=$(echo "$BADGING" | grep application:)
    LABEL=$(echo $APPLICATION | grep -Po "label='\K[^']+")

    LANG=$(echo $APK | grep -Po "tachiyomi-\K[^\.]+")

    ICON=$(echo "$BADGING" | grep -Po "application-icon-320.*'\K[^']+")
    unzip -p $APK $ICON > icon/${FILENAME%.*}.png

    jq -n \
        --arg name "$LABEL" \
        --arg pkg "$PKGNAME" \
        --arg apk "$FILENAME" \
        --arg lang "$LANG" \
        --argjson code $VCODE \
        --arg version "$VNAME" \
        --argjson nsfw $NSFW \
        '{name:$name, pkg:$pkg, apk:$apk, lang:$lang, code:$code, version:$version, nsfw:$nsfw}'

done | jq -sr '[.[]]' > index.json

# Alternate minified copy
jq -c '.' < index.json > index.min.json

cat index.json

echo
echo "executing inspector"
echo

curl "https://raw.githubusercontent.com/AriaMoradi/tachiyomi-extensions-inspector/repo/Inspector-latest.jar" -o "Inspector-latest.jar"

java -jar Inspector-latest.jar ../apk inspector.txt

cat inspector.txt

rm Inspector-latest.jar
