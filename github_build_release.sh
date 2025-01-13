#!/bin/bash

GRADLE_LOCATION=./app/build.gradle
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk

APP_NAME="FFShare"
APP_VERSION=$(grep -Po '(?<=versionName \").*(?=\")' "$GRADLE_LOCATION")
APP_VERSION_CODE=$(grep -Po '(?<=versionCode ).*' "$GRADLE_LOCATION")

# no pre-release version argument
if [ -z "$1" ]; then
    ./gradlew assembleRelease
else
    PRE_VERSION_NAME="$1"
    # create backup of gradle to revert back to after bumping version and assembling release
    cp "$GRADLE_LOCATION" "$GRADLE_LOCATION.original"

    # change app version to pre version and bumb version code
    APP_VERSION="$PRE_VERSION_NAME"
    APP_VERSION_CODE="$((APP_VERSION_CODE + 1))"

    sed -i -e "s/versionName \".*\"/versionName \"${APP_VERSION}\"/g" "$GRADLE_LOCATION"
    sed -i -e "s/versionCode .*/versionCode ${APP_VERSION_CODE}/g" "$GRADLE_LOCATION"

    ./gradlew assembleRelease

    # revert to original gradle version after build finished
    mv "$GRADLE_LOCATION.original" "$GRADLE_LOCATION"
fi


OUTPUT_FOLDER="./github_releases/$APP_VERSION"


mkdir -p "$OUTPUT_FOLDER" 2>/dev/null
rm -rf ./"${OUTPUT_FOLDER:?}"/* # clean if rebuild

APK_ROOT=./app/build/outputs/apk

for VARIANT_DIR in "$APK_ROOT"/*; do
	variant=$(basename "$VARIANT_DIR")
	for APK_FILE in "$VARIANT_DIR"/release/*.apk; do
		abi=$(basename "$APK_FILE" | cut -d'-' -f3)
		abi_bit="_$abi"
		[[ "$abi" == 'universal' ]] && abi_bit=""
		newApk="${APP_NAME}_${APP_VERSION}_${variant}${abi_bit}.apk"
		cp "$APK_FILE" "$OUTPUT_FOLDER/$newApk"
	done
done

changelog=$(cat "./fastlane/metadata/android/en-US/changelogs/$APP_VERSION_CODE.txt")

# title
echo "$APP_NAME $APP_VERSION" > "$OUTPUT_FOLDER/release"

# changelog
echo "=== Changelog ===" >> "$OUTPUT_FOLDER/release"
echo "$changelog" >> "$OUTPUT_FOLDER/release"

# apk info
echo """
=== APK Info ===
arm64 & armeabi - your phones CPU architecture, the only benefit of downloading these over the default one is a download size reduction
full - FFShare will compress videos, images and audio files (mp3/ogg/etc...)
video - FFShare will only compress videos and images
""" >> "$OUTPUT_FOLDER/release"

# sha256
echo "=== SHA256 ===" >> "$OUTPUT_FOLDER/release"
for apk in "$OUTPUT_FOLDER"/*.apk; do
    sha=$(sha256sum "$apk" | awk '{ print $1 }')
    size=$(du -hk "$apk" | awk '{ printf "%.1fM", $1/1024 }')
    base=$(basename "$apk")
    echo "$sha  $base ($size)" >> "$OUTPUT_FOLDER/release"
done

