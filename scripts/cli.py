#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.10"
# dependencies = [
#     "typer>=0.9.0",
# ]
# ///
"""
FFShare Release CLI

A command-line tool for managing releases: tagging, building APKs, and publishing to GitHub.

Usage:
    uv run scripts/cli.py tag patch --push
    uv run scripts/cli.py build
    uv run scripts/cli.py release
    uv run scripts/cli.py publish patch
"""

import hashlib
import re
import shutil
import subprocess
from enum import Enum
from pathlib import Path
from typing import Optional

import typer
from typer import Argument, Option

# Project root directory (parent of scripts/)
PROJECT_ROOT = Path(__file__).parent.parent.resolve()

# Constants
APP_NAME = "FFShare"
GRADLE_LOCATION = PROJECT_ROOT / "app" / "build.gradle"
APK_ROOT = PROJECT_ROOT / "app" / "build" / "outputs" / "apk"
RELEASES_DIR = PROJECT_ROOT / "github_releases"
CHANGELOG_DIR = PROJECT_ROOT / "fastlane" / "metadata" / "android" / "en-US" / "changelogs"

# Main app
app = typer.Typer(
    name="ffshare",
    help="FFShare release management CLI",
    no_args_is_help=True,
)

# Tag subcommand group
tag_app = typer.Typer(
    name="tag",
    help="Create and manage git tags following semver",
    no_args_is_help=True,
)
app.add_typer(tag_app, name="tag")


class ReleaseType(str, Enum):
    major = "major"
    minor = "minor"
    patch = "patch"


def run(
    cmd: list[str], capture: bool = False, check: bool = True, cwd: Path | None = None
) -> subprocess.CompletedProcess:
    """Run a shell command."""
    if capture:
        return subprocess.run(cmd, capture_output=True, text=True, check=check, cwd=cwd)
    return subprocess.run(cmd, check=check, cwd=cwd)


def get_gradle_version() -> tuple[str, int]:
    """Get version name and code from build.gradle."""
    content = GRADLE_LOCATION.read_text()
    version_name = re.search(r'versionName "([^"]+)"', content)
    version_code = re.search(r"versionCode (\d+)", content)
    return (
        version_name.group(1) if version_name else "0.0.0",
        int(version_code.group(1)) if version_code else 1,
    )


def get_latest_stable_tag() -> Optional[str]:
    """Get the latest stable tag (no pre-release suffix)."""
    result = run(
        ["git", "tag", "-l", "v*", "--sort", "-version:refname"], capture=True, check=False
    )
    if result.returncode != 0:
        return None
    for tag in result.stdout.strip().split("\n"):
        if tag and "-" not in tag:
            return tag
    return None


def get_latest_rc_tag() -> Optional[str]:
    """Get the latest release candidate tag."""
    result = run(
        ["git", "tag", "-l", "v*-rc.*", "--sort", "-version:refname"], capture=True, check=False
    )
    if result.returncode != 0 or not result.stdout.strip():
        return None
    return result.stdout.strip().split("\n")[0]


def get_latest_tag() -> Optional[str]:
    """Get the latest tag (any type)."""
    result = run(
        ["git", "tag", "-l", "v*", "--sort", "-version:refname"], capture=True, check=False
    )
    if result.returncode != 0 or not result.stdout.strip():
        return None
    return result.stdout.strip().split("\n")[0]


def tag_exists(tag: str) -> bool:
    """Check if a git tag exists."""
    result = run(["git", "rev-parse", tag], capture=True, check=False)
    return result.returncode == 0


def parse_version(tag: str) -> tuple[int, int, int]:
    """Parse a version tag into (major, minor, patch)."""
    match = re.match(r"v?(\d+)\.(\d+)\.(\d+)", tag)
    if not match:
        return (0, 0, 0)
    return (int(match.group(1)), int(match.group(2)), int(match.group(3)))


def bump_version(
    version: tuple[int, int, int], release_type: ReleaseType
) -> tuple[int, int, int]:
    """Bump version based on release type."""
    major, minor, patch = version
    if release_type == ReleaseType.major:
        return (major + 1, 0, 0)
    elif release_type == ReleaseType.minor:
        return (major, minor + 1, 0)
    else:  # patch
        return (major, minor, patch + 1)


def format_version(version: tuple[int, int, int], rc: Optional[int] = None) -> str:
    """Format version tuple as tag string."""
    tag = f"v{version[0]}.{version[1]}.{version[2]}"
    if rc is not None:
        tag += f"-rc.{rc}"
    return tag


def sha256_file(path: Path) -> str:
    """Calculate SHA256 hash of a file."""
    sha256 = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            sha256.update(chunk)
    return sha256.hexdigest()


def human_size(size_bytes: int) -> str:
    """Convert bytes to human readable size."""
    return f"{size_bytes / (1024 * 1024):.1f}M"


# Tag commands
@tag_app.command("major")
def tag_major(
    push: bool = Option(False, "--push", "-p", help="Push tag to origin"),
    simulate: bool = Option(False, "--simulate", "-s", help="Dry run, don't execute git commands"),
):
    """Bump major version (x.0.0)."""
    _create_tag(ReleaseType.major, push=push, simulate=simulate)


@tag_app.command("minor")
def tag_minor(
    push: bool = Option(False, "--push", "-p", help="Push tag to origin"),
    simulate: bool = Option(False, "--simulate", "-s", help="Dry run, don't execute git commands"),
):
    """Bump minor version (0.x.0)."""
    _create_tag(ReleaseType.minor, push=push, simulate=simulate)


@tag_app.command("patch")
def tag_patch(
    push: bool = Option(False, "--push", "-p", help="Push tag to origin"),
    simulate: bool = Option(False, "--simulate", "-s", help="Dry run, don't execute git commands"),
):
    """Bump patch version (0.0.x)."""
    _create_tag(ReleaseType.patch, push=push, simulate=simulate)


@tag_app.command("rc")
def tag_rc(
    release_type: Optional[ReleaseType] = Argument(
        None, help="Version type to bump (major/minor/patch). Omit to increment existing RC."
    ),
    push: bool = Option(False, "--push", "-p", help="Push tag to origin"),
    simulate: bool = Option(False, "--simulate", "-s", help="Dry run, don't execute git commands"),
):
    """Create or increment a release candidate tag.

    Examples:
        uv run scripts/cli.py tag rc patch --push    # v1.0.0 -> v1.0.1-rc.1
        uv run scripts/cli.py tag rc --push          # v1.0.1-rc.1 -> v1.0.1-rc.2
    """
    _create_tag(release_type, is_rc=True, push=push, simulate=simulate)


@tag_app.command("latest")
def tag_latest():
    """Show the latest stable and RC tags."""
    stable = get_latest_stable_tag()
    rc = get_latest_rc_tag()
    typer.echo(f"Latest stable tag: {stable or 'none'}")
    typer.echo(f"Latest RC tag: {rc or 'none'}")


@tag_app.command("retag")
def tag_retag(
    push: bool = Option(False, "--push", "-p", help="Push tag to origin"),
    simulate: bool = Option(False, "--simulate", "-s", help="Dry run, don't execute git commands"),
):
    """Re-create the latest tag at current HEAD."""
    stable = get_latest_stable_tag()
    if not stable:
        typer.echo("Error: No existing tags found", err=True)
        raise typer.Exit(1)

    if simulate:
        typer.echo("[SIMULATE] Would delete and recreate tag")

    typer.echo(f"> git tag -d {stable}")
    if not simulate:
        run(["git", "tag", "-d", stable], check=False)

    typer.echo(f"> git tag {stable}")
    if not simulate:
        run(["git", "tag", stable])

    if push:
        typer.echo(f"> git push origin {stable} --force")
        if not simulate:
            run(["git", "push", "origin", stable, "--force"])


def _create_tag(
    release_type: Optional[ReleaseType],
    is_rc: bool = False,
    push: bool = False,
    simulate: bool = False,
) -> str:
    """Internal function to create a tag."""
    if simulate:
        typer.echo("[SIMULATE] No git operations will be performed")

    # Get current version
    stable_tag = get_latest_stable_tag()
    if stable_tag:
        current_version = parse_version(stable_tag)
    else:
        typer.echo("No existing stable tags found, starting from v0.0.0")
        current_version = (0, 0, 0)

    if is_rc:
        if release_type:
            # New RC: bump version and start at rc.1
            new_version = bump_version(current_version, release_type)
            rc_num = 1
            new_tag = format_version(new_version, rc=rc_num)

            # If tag already exists, increment RC number until we find an unused one
            while tag_exists(new_tag):
                rc_num += 1
                new_tag = format_version(new_version, rc=rc_num)
        else:
            # Increment existing RC
            rc_tag = get_latest_rc_tag()
            if not rc_tag:
                typer.echo(
                    "Error: No existing RC tag found. Specify a release type: uv run scripts/cli.py tag rc patch",
                    err=True,
                )
                raise typer.Exit(1)

            # Parse RC tag
            match = re.match(r"v?(\d+)\.(\d+)\.(\d+)-rc\.(\d+)", rc_tag)
            if not match:
                typer.echo(f"Error: Could not parse RC tag: {rc_tag}", err=True)
                raise typer.Exit(1)

            rc_version = (int(match.group(1)), int(match.group(2)), int(match.group(3)))
            rc_num = int(match.group(4)) + 1
            new_tag = format_version(rc_version, rc=rc_num)

            # If tag already exists, increment RC number until we find an unused one
            while tag_exists(new_tag):
                rc_num += 1
                new_tag = format_version(rc_version, rc=rc_num)
    else:
        if not release_type:
            typer.echo("Error: Release type required", err=True)
            raise typer.Exit(1)
        new_version = bump_version(current_version, release_type)
        new_tag = format_version(new_version)

    # Create tag
    typer.echo(f"> git tag {new_tag}")
    if not simulate:
        run(["git", "tag", new_tag])

    # Push if requested
    if push:
        typer.echo(f"> git push origin {new_tag}")
        if not simulate:
            run(["git", "push", "origin", new_tag])

    return new_tag


# Build command
@app.command()
def build(
    version: Optional[str] = Argument(
        None, help="Version name for the build. If omitted, uses version from build.gradle"
    ),
):
    """Build APKs for release.

    Runs the Gradle build and copies APKs to github_releases/<version>/.

    Examples:
        uv run scripts/cli.py build                  # Build with current version
        uv run scripts/cli.py build 1.0.1-rc.1       # Build with custom version name
    """
    _build_apks(version)


def _build_apks(version: Optional[str] = None) -> Path:
    """Internal function to build APKs. Returns the output folder path."""
    gradle_version, gradle_code = get_gradle_version()

    if version:
        # Backup gradle file
        backup_path = GRADLE_LOCATION.with_suffix(".gradle.original")
        shutil.copy(GRADLE_LOCATION, backup_path)

        try:
            # Update version in gradle
            content = GRADLE_LOCATION.read_text()
            new_code = gradle_code + 1
            content = re.sub(r'versionName "[^"]+"', f'versionName "{version}"', content)
            content = re.sub(r"versionCode \d+", f"versionCode {new_code}", content)
            GRADLE_LOCATION.write_text(content)

            app_version = version
            app_version_code = new_code

            # Run gradle build
            typer.echo(f"Building APKs for version {version}...")
            run(["./gradlew", "assembleRelease"], cwd=PROJECT_ROOT)
        finally:
            # Restore original gradle file
            shutil.move(backup_path, GRADLE_LOCATION)
    else:
        app_version = gradle_version
        app_version_code = gradle_code
        typer.echo(f"Building APKs for version {app_version}...")
        run(["./gradlew", "assembleRelease"], cwd=PROJECT_ROOT)

    # Create output folder
    output_folder = RELEASES_DIR / app_version
    output_folder.mkdir(parents=True, exist_ok=True)

    # Clean existing files
    for f in output_folder.glob("*"):
        f.unlink()

    # Copy APKs with renamed format
    for variant_dir in APK_ROOT.iterdir():
        if not variant_dir.is_dir():
            continue
        variant = variant_dir.name
        release_dir = variant_dir / "release"
        if not release_dir.exists():
            continue

        for apk_file in release_dir.glob("*.apk"):
            # Extract ABI from filename (e.g., app-full-arm64-v8a-release.apk)
            parts = apk_file.stem.split("-")
            abi = parts[2] if len(parts) > 2 else "universal"
            abi_suffix = f"_{abi}" if abi != "universal" else ""
            new_name = f"{APP_NAME}_{app_version}_{variant}{abi_suffix}.apk"
            shutil.copy(apk_file, output_folder / new_name)

    # Read changelog
    changelog_file = CHANGELOG_DIR / f"{app_version_code}.txt"
    changelog = changelog_file.read_text() if changelog_file.exists() else "No changelog available"

    # Generate release notes
    release_file = output_folder / "release"
    with open(release_file, "w") as f:
        # Title
        f.write(f"{APP_NAME} {app_version}\n")

        # Changelog
        f.write("=== Changelog ===\n")
        f.write(f"{changelog}\n")

        # APK info
        f.write("""
=== APK Info ===
arm64 & armeabi - your phones CPU architecture, the only benefit of downloading these over the default one is a download size reduction
full - FFShare will compress videos, images and audio files (mp3/ogg/etc...)
video - FFShare will only compress videos and images
""")

        # SHA256
        f.write("=== SHA256 ===\n")
        for apk in sorted(output_folder.glob("*.apk")):
            sha = sha256_file(apk)
            size = human_size(apk.stat().st_size)
            f.write(f"{sha}  {apk.name} ({size})\n")

    typer.echo(f"Build complete! Output: {output_folder}")
    return output_folder


# Release command
@app.command()
def release(
    tag: Optional[str] = Argument(None, help="Git tag to release. If omitted, uses latest tag."),
    draft: bool = Option(False, "--draft", "-d", help="Create release as draft"),
):
    """Create a GitHub release with built APKs.

    Uploads APKs from github_releases/<version>/ to GitHub.
    RC tags are automatically marked as prereleases.

    Examples:
        uv run scripts/cli.py release                # Release latest tag
        uv run scripts/cli.py release v1.0.1         # Release specific tag
        uv run scripts/cli.py release --draft        # Create as draft
    """
    _create_release(tag, draft)


def _create_release(tag: Optional[str] = None, draft: bool = False):
    """Internal function to create a GitHub release."""
    # Get tag
    if not tag:
        tag = get_latest_tag()
        if not tag:
            typer.echo("Error: No tags found. Create a tag first.", err=True)
            raise typer.Exit(1)
        typer.echo(f"Using latest tag: {tag}")

    # Verify tag exists
    result = run(["git", "rev-parse", tag], capture=True, check=False)
    if result.returncode != 0:
        typer.echo(f"Error: Tag '{tag}' does not exist", err=True)
        raise typer.Exit(1)

    # Extract version from tag
    version = tag.lstrip("v")

    # Find release folder
    release_folder = RELEASES_DIR / version
    if not release_folder.exists():
        typer.echo(f"Error: Release folder not found: {release_folder}", err=True)
        typer.echo("Run 'uv run scripts/cli.py build' first to build the APKs")
        raise typer.Exit(1)

    # Find APK files
    apk_files = list(release_folder.glob("*.apk"))
    if not apk_files:
        typer.echo(f"Error: No APK files found in {release_folder}", err=True)
        raise typer.Exit(1)

    # Read release notes
    release_file = release_folder / "release"
    if release_file.exists():
        release_notes = release_file.read_text()
    else:
        typer.echo(f"Warning: No release notes found at {release_file}")
        release_notes = f"Release {tag}"

    # Get title (first line of release notes)
    release_title = release_notes.split("\n")[0] or f"{APP_NAME} {version}"

    # Auto-detect prerelease
    is_prerelease = "-" in tag

    typer.echo("")
    typer.echo("Creating GitHub release:")
    typer.echo(f"  Tag: {tag}")
    typer.echo(f"  Title: {release_title}")
    typer.echo(f"  APKs: {len(apk_files)} files")
    typer.echo(f"  Prerelease: {is_prerelease}")
    typer.echo(f"  Draft: {draft}")
    typer.echo("")

    # Build gh command
    cmd = [
        "gh",
        "release",
        "create",
        tag,
        "--title",
        release_title,
        "--notes",
        release_notes,
    ]

    if draft:
        cmd.append("--draft")
    if is_prerelease:
        cmd.append("--prerelease")

    # Add APK files
    cmd.extend(str(f) for f in apk_files)

    run(cmd)

    # Get release URL
    result = run(["gh", "release", "view", tag, "--json", "url", "-q", ".url"], capture=True)
    typer.echo("")
    typer.echo("Release created successfully!")
    typer.echo(f"View at: {result.stdout.strip()}")


# Publish command (full workflow)
publish_app = typer.Typer(
    name="publish",
    help="Full release workflow: tag, build, and publish to GitHub",
    no_args_is_help=True,
)
app.add_typer(publish_app, name="publish")


def _publish(
    release_type: Optional[ReleaseType],
    is_rc: bool = False,
    draft: bool = False,
    no_build: bool = False,
):
    """Internal function for full publish workflow."""
    typer.echo("=== Step 1: Creating tag ===")
    new_tag = _create_tag(release_type, is_rc=is_rc, push=True, simulate=False)
    version = new_tag.lstrip("v")

    if not no_build:
        typer.echo("\n=== Step 2: Building APKs ===")
        _build_apks(version)
    else:
        typer.echo("\n=== Step 2: Skipping build (--no-build) ===")

    typer.echo("\n=== Step 3: Creating GitHub release ===")
    _create_release(new_tag, draft)

    typer.echo("\n=== Release complete! ===")


@publish_app.command("major")
def publish_major(
    draft: bool = Option(False, "--draft", "-d", help="Create release as draft"),
    no_build: bool = Option(False, "--no-build", help="Skip build step, use existing APKs"),
):
    """Tag major version, build, and publish."""
    _publish(ReleaseType.major, draft=draft, no_build=no_build)


@publish_app.command("minor")
def publish_minor(
    draft: bool = Option(False, "--draft", "-d", help="Create release as draft"),
    no_build: bool = Option(False, "--no-build", help="Skip build step, use existing APKs"),
):
    """Tag minor version, build, and publish."""
    _publish(ReleaseType.minor, draft=draft, no_build=no_build)


@publish_app.command("patch")
def publish_patch(
    draft: bool = Option(False, "--draft", "-d", help="Create release as draft"),
    no_build: bool = Option(False, "--no-build", help="Skip build step, use existing APKs"),
):
    """Tag patch version, build, and publish."""
    _publish(ReleaseType.patch, draft=draft, no_build=no_build)


@publish_app.command("rc")
def publish_rc(
    release_type: Optional[ReleaseType] = Argument(
        None, help="Version type to bump (major/minor/patch). Omit to increment existing RC."
    ),
    draft: bool = Option(False, "--draft", "-d", help="Create release as draft"),
    no_build: bool = Option(False, "--no-build", help="Skip build step, use existing APKs"),
):
    """Tag release candidate, build, and publish.

    Examples:
        uv run scripts/cli.py publish rc patch       # v1.0.0 -> v1.0.1-rc.1
        uv run scripts/cli.py publish rc             # v1.0.1-rc.1 -> v1.0.1-rc.2
        uv run scripts/cli.py publish rc minor --draft
    """
    _publish(release_type, is_rc=True, draft=draft, no_build=no_build)


if __name__ == "__main__":
    app()
