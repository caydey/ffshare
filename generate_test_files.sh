#!/bin/sh

OUTPUT="$(dirname "$0")/test_files"
[ -d "$OUTPUT" ] || mkdir "$OUTPUT"
rm "$OUTPUT"/*
cd "$OUTPUT"

MASTER_VIDEO="master_video.mp4"
MASTER_IMAGE="master_image.png"

wget "https://download.blender.org/demo/movies/BBB/bbb_sunflower_1080p_60fps_normal.mp4.zip"
unzip bbb_sunflower_1080p_60fps_normal.mp4.zip
rm bbb_sunflower_1080p_60fps_normal.mp4.zip
mv bbb_sunflower_1080p_60fps_normal.mp4 "$MASTER_VIDEO"

ffmpeg -i $MASTER_VIDEO -ss 00:55 -vframes 1 "$MASTER_IMAGE"


function awkward_vid_resolutions() {
  ffmpeg -y -i "$MASTER_VIDEO" -ac 2 -vf "crop=409:941:600:0:exact=1" -ss 00:22 -to 00:24 "${1}_409x941.webm"
  ffmpeg -y -i "$MASTER_VIDEO" -ac 2 -vf "crop=1:1:0:0:exact=1" -ss 00:22 -to 00:24 "${1}_001x001.webm"
  ffmpeg -y -i "$MASTER_VIDEO" -ac 2 -vf "crop=4:4:0:0:exact=1" -ss 00:22 -to 00:24 "${1}_004x004.mkv"
  ffmpeg -y -i "$MASTER_VIDEO" -ac 2 -vf "crop=2:512:0:0:exact=1" -ss 00:22 -to 00:24 "${1}_002x512.mp4"
}
function zero_duration_video() {
  ffmpeg -y -i "$MASTER_IMAGE" "$1"
}
function image_formats() {
  magick "$MASTER_IMAGE" "$1.jpg"
  magick "$MASTER_IMAGE" "$1.webp"
  magick "$MASTER_IMAGE" "$1.jxl"
  magick "$MASTER_IMAGE" "$1.tiff"
  magick "$MASTER_IMAGE" "$1.gif"
  magick "$MASTER_IMAGE" "$1.png"
}
function video_formats() {
  ffmpeg -y -i "$MASTER_VIDEO" -ac 2 -ss 00:22 -to 00:30 "$1.avi"
  ffmpeg -y -i "$MASTER_VIDEO" -ac 2 -ss 00:22 -to 00:30 "$1.mkv"
  ffmpeg -y -i "$MASTER_VIDEO" -ac 2 -ss 00:22 -to 00:30 "$1.mp4"
  ffmpeg -y -i "$MASTER_VIDEO" -ac 2 -ss 00:22 -to 00:30 "$1.mov"
  ffmpeg -y -i "$MASTER_VIDEO" -ac 2 -ss 00:22 -to 00:30 "$1.webm"
}


awkward_vid_resolutions "awkward_vid_resolutions" 2>/dev/null
image_formats "image_formats" 2>/dev/null
video_formats "video_formats" 2>/dev/null
zero_duration_video "zero_duration_video.mp4" 2>/dev/null


rm "$MASTER_IMAGE" "$MASTER_VIDEO"
