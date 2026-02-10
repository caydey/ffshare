# <img src="fastlane/metadata/android/en-US/images/icon.png" height="32"> FFShare

[![GitHub release (latest by date)](https://img.shields.io/github/v/release/caydey/FFShare)](https://github.com/caydey/ffshare/releases/latest)
[![GitHub all releases](https://img.shields.io/github/downloads/caydey/ffshare/total)](https://github.com/caydey/ffshare/releases/latest)
[![GitHub license](https://img.shields.io/github/license/caydey/ffshare)](https://github.com/caydey/ffshare/blob/master/LICENSE)

**[Download APK from Releases](https://github.com/caydey/ffshare/releases/latest)**

An android app to compress image, video and audio files through ffmpeg before sharing them

## Images

<p align="left">
      <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="270">
      <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="270">
      <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="270">
      <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="270">
</p>

## Used Libraries

- [FFmpegKit](https://github.com/arthenica/ffmpeg-kit)
- [Timber](https://github.com/JakeWharton/timber)

## Development

### Release CLI

A Python CLI for managing releases. Requires [uv](https://docs.astral.sh/uv/) and [GitHub CLI](https://cli.github.com/).

```bash
uv run scripts/cli.py --help            # Show all commands
uv run scripts/cli.py <command> --help  # Show command help
```

**Full release workflow** (`publish`):
```bash
uv run scripts/cli.py publish minor          # Tag v1.1.0, build APKs, create GitHub release
uv run scripts/cli.py publish patch          # Tag v1.0.1, build APKs, create GitHub release
uv run scripts/cli.py publish rc patch       # Tag v1.0.1-rc.1, build, release as prerelease
uv run scripts/cli.py publish rc             # Increment RC (v1.0.1-rc.2), build, release
uv run scripts/cli.py publish patch --draft  # Create as draft release
uv run scripts/cli.py publish patch --no-build  # Skip build, use existing APKs
```

**Tagging only** (`tag`):
```bash
uv run scripts/cli.py tag minor --push       # v1.0.0 -> v1.1.0
uv run scripts/cli.py tag patch --push       # v1.0.0 -> v1.0.1
uv run scripts/cli.py tag rc patch --push    # v1.0.0 -> v1.0.1-rc.1
uv run scripts/cli.py tag rc --push          # v1.0.1-rc.1 -> v1.0.1-rc.2
uv run scripts/cli.py tag latest             # Show latest stable and RC tags
uv run scripts/cli.py tag patch --simulate   # Dry run
```

**Build and release separately**:
```bash
uv run scripts/cli.py build                  # Build APKs with current version
uv run scripts/cli.py build 1.0.1-rc.1       # Build with custom version
uv run scripts/cli.py release                # Create GitHub release from latest tag
uv run scripts/cli.py release v1.0.1 --draft # Release specific tag as draft
```
