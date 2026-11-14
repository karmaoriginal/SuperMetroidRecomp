#!/usr/bin/env python3
"""Upload an owned Super Metroid ROM privately for one Android Actions build.

The ROM is stored as a private GitHub release asset. Only its short-lived signed
URL is copied to the build repository as SM_ANDROID_ROM_URL. The Android
workflow verifies the known SHA-256, generates the private C program on its
ephemeral runner, deletes the ROM, and publishes only the compiled APK.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import time
import urllib.error
import urllib.request

EXPECTED_SHA256 = "12b77c4bc9c1832cee8881244659065ee1d84c70c3d29e6eaf92e6798cc2ca72"
EXPECTED_SIZE = 3145728


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("rom", type=Path)
    parser.add_argument("--storage-repo", help="Private owner/repo (default: <you>/sm-android-rom-vault)")
    parser.add_argument("--build-repo", default="karmaoriginal/SuperMetroidRecomp")
    args = parser.parse_args()

    gh = shutil.which("gh")
    if not gh:
        raise SystemExit("GitHub CLI is required: pkg install gh")

    rom = args.rom.expanduser().resolve()
    if not rom.is_file():
        raise SystemExit(f"ROM not found: {rom}")
    raw = rom.read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    if len(raw) != EXPECTED_SIZE or digest != EXPECTED_SHA256:
        raise SystemExit(
            "ROM rejected: expected the unheadered Super Metroid (Japan, USA) "
            f"image ({EXPECTED_SIZE} bytes, SHA-256 {EXPECTED_SHA256})")

    def run(*command, input_text=None):
        try:
            return subprocess.check_output(
                [gh, *command], text=True, input=input_text,
                stderr=subprocess.STDOUT).strip()
        except subprocess.CalledProcessError as exc:
            detail = (exc.stdout or "").strip()
            raise SystemExit(f"gh {' '.join(command)} failed:\n{detail}") from None

    user = json.loads(run("api", "user"))["login"]
    storage = args.storage_repo or f"{user}/sm-android-rom-vault"
    probe = subprocess.run([gh, "api", f"repos/{storage}"],
                           stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if probe.returncode:
        run("repo", "create", storage, "--private",
            "--description", "Private owned-ROM input for ephemeral Android builds")
        repo_info = json.loads(run("api", f"repos/{storage}"))
    else:
        repo_info = json.loads(probe.stdout)
        if not repo_info["private"]:
            raise SystemExit(f"Refusing public storage repository: {storage}")

    # GitHub cannot create a release in a completely empty repository. Query
    # the file itself rather than repo_info.size: that size can remain zero for
    # a while after the first commit, which made a retry attempt to overwrite
    # README.md without the API-required existing-file SHA.
    readme_probe = subprocess.run(
        [gh, "api", f"repos/{storage}/contents/README.md"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if readme_probe.returncode:
        readme = base64.b64encode(
            b"# Private Android build inputs\n\nNever make this repository public.\n"
        ).decode("ascii")
        run("api", "--method", "PUT", f"repos/{storage}/contents/README.md",
            "-f", "message=Initialize private ROM vault",
            "-f", f"content={readme}")

    tag = "super-metroid-owned-rom-" + digest[:16]
    found = subprocess.run([gh, "release", "view", tag, "--repo", storage],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if found.returncode:
        run("release", "create", tag, "--repo", storage, "--title", tag,
            "--notes", "Private user-owned ROM. Never publish or redistribute.")
    def find_rom_asset(release):
        # GitHub may normalize spaces and punctuation in an uploaded asset's
        # filename. This release tag is unique to the verified ROM digest, so
        # identify its sole 3 MiB payload by exact digest/name first and size
        # second instead of assuming the server preserves rom.name verbatim.
        for candidate in release["assets"]:
            if candidate.get("digest") == "sha256:" + digest:
                return candidate
        for candidate in release["assets"]:
            if candidate["name"] == rom.name:
                return candidate
        sized = [a for a in release["assets"] if a.get("size") == EXPECTED_SIZE]
        return sized[0] if len(sized) == 1 else None

    release = json.loads(run("api", f"repos/{storage}/releases/tags/{tag}"))
    asset = find_rom_asset(release)
    if asset is None:
        run("release", "upload", tag, str(rom), "--repo", storage)
        # Release asset listings can be briefly eventually consistent.
        for _ in range(10):
            release = json.loads(run("api", f"repos/{storage}/releases/tags/{tag}"))
            asset = find_rom_asset(release)
            if asset is not None:
                break
            time.sleep(1)
    if asset is None:
        raise SystemExit("ROM upload completed, but GitHub did not list its private release asset")
    if asset.get("digest") not in (None, "sha256:" + digest):
        raise SystemExit("Private uploaded asset digest differs from the local ROM")

    token = run("auth", "token")
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            return None
    request = urllib.request.Request(asset["url"], headers={
        "Authorization": "Bearer " + token,
        "Accept": "application/octet-stream",
        "User-Agent": "SuperMetroid-Android-private-input",
    })
    try:
        urllib.request.build_opener(NoRedirect).open(request)
        raise RuntimeError("Expected a signed private asset redirect")
    except urllib.error.HTTPError as response:
        if response.code != 302:
            raise RuntimeError(f"Private asset download API returned {response.code}") from None
        url = response.headers["Location"]
    if not url.startswith("https://release-assets.githubusercontent.com/"):
        raise SystemExit("Unexpected signed asset host")

    subprocess.run([gh, "secret", "set", "SM_ANDROID_ROM_URL",
                    "--repo", args.build_repo], input=url, text=True, check=True)
    print(json.dumps({
        "storage": storage,
        "sha256": digest,
        "secret": f"{args.build_repo}:SM_ANDROID_ROM_URL",
        "next": "Re-run Android APK immediately; the signed URL expires.",
    }, indent=2))


if __name__ == "__main__":
    main()
