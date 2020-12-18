#!/bin/bash
set -e

TOOLS="$(ls -d ${ANDROID_HOME}/build-tools/* | tail -1)"

shopt -s globstar nullglob extglob
APKS=( **/*".apk" )

# Fail if too little extensions seem to have been built
if [ "${#APKS[@]}" -le "1" ]; then
    echo "Insufficient amount of APKs found. Please check the project configuration."
    exit 1;
fi;

# Take base64 encoded key input and put it into a file
STORE_PATH=$PWD/signingkey.jks
rm -f $STORE_PATH && touch $STORE_PATH
echo $1 | base64 -d > $STORE_PATH

STORE_ALIAS=$2
export KEY_STORE_PASSWORD=$3
export KEY_PASSWORD=$4

DEST=$PWD/apk
rm -rf $DEST && mkdir -p $DEST

# Sign all of the APKs
for APK in ${APKS[@]}; do
    BASENAME=$(basename $APK)
    APKNAME="${BASENAME%%+(-release*)}.apk"
    APKDEST="$DEST/$APKNAME"

    ${TOOLS}/zipalign -c -v -p 4 $APK

    cp $APK $APKDEST
    ${TOOLS}/apksigner sign --ks $STORE_PATH --ks-key-alias $STORE_ALIAS --ks-pass env:KEY_STORE_PASSWORD --key-pass env:KEY_PASSWORD $APKDEST
done

rm $STORE_PATH
unset KEY_STORE_PASSWORD
unset KEY_PASSWORD
