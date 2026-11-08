#!/usr/bin/env python3
"""
Mersin University BAP Regulation Downloader
Downloads available MEÜ BAP regulation documents into data/regulations/.
Gracefully handles 404 or unreachable URLs by copying fallback seed documents.
"""

import hashlib
import json
import os
import shutil
import sys
import urllib.error
import urllib.request
from pathlib import Path

MANIFEST_PATH = Path("contracts/seed-data/meu_regulations_manifest.json")
DATA_DIR = Path("data/regulations")
USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 BapAdvisor/1.0"


def calculate_sha256(filepath: Path) -> str:
    hasher = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    return hasher.hexdigest()


def download_file(url: str, target_path: Path, timeout: int = 15) -> bool:
    req = urllib.request.Request(
        url,
        headers={"User-Agent": USER_AGENT}
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            if response.status == 200:
                content = response.read()
                # Basic sanity check (ensure not HTML 404 page disguised as 200)
                if content.startswith(b"%PDF") or len(content) > 1024:
                    with open(target_path, "wb") as f:
                        f.write(content)
                    return True
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, Exception) as e:
        return False
    return False


def main():
    if not MANIFEST_PATH.exists():
        print(f"Error: Manifest file not found at {MANIFEST_PATH}", file=sys.stderr)
        sys.exit(1)

    DATA_DIR.mkdir(parents=True, exist_ok=True)

    with open(MANIFEST_PATH, "r", encoding="utf-8") as f:
        manifest = json.load(f)

    fallback_file = Path(manifest.get("seed_fallback_file", "contracts/seed-data/meu_uygulama_esaslari_seed.md"))
    if fallback_file.exists():
        target_seed = DATA_DIR / fallback_file.name
        shutil.copy2(fallback_file, target_seed)
        print(f"[SEED] Copied base seed: {target_seed} (SHA256: {calculate_sha256(target_seed)[:16]}...)")

    results = []
    print(f"\nFetching {len(manifest['documents'])} MEÜ regulation documents...")
    print("-" * 75)

    for doc in manifest["documents"]:
        slug = doc["slug"]
        file_name = doc["file_name"]
        target_path = DATA_DIR / file_name
        primary_url = doc.get("url")
        alt_urls = doc.get("alternative_urls", [])
        urls_to_try = [primary_url] + [u for u in alt_urls if u != primary_url]

        downloaded = False
        status_msg = "FAILED"
        sha256_hash = "N/A"

        for url in urls_to_try:
            if not url:
                continue
            print(f"  Attempting download: {slug} from {url[:60]}...")
            if download_file(url, target_path):
                downloaded = True
                status_msg = "DOWNLOADED"
                sha256_hash = calculate_sha256(target_path)
                break

        if not downloaded:
            # Check if fallback seed applies
            if "fallback_seed_path" in doc or slug == "meu-bap-uygulama-esaslari-2025":
                seed_target = DATA_DIR / fallback_file.name
                if seed_target.exists():
                    status_msg = "FALLBACK_SEED_USED"
                    sha256_hash = calculate_sha256(seed_target)
            else:
                status_msg = "404_OR_UNAVAILABLE"

        results.append({
            "slug": slug,
            "file": file_name,
            "status": status_msg,
            "sha256": sha256_hash[:16] if sha256_hash != "N/A" else "N/A"
        })

    print("-" * 75)
    print(f"{'SLUG':<35} | {'STATUS':<18} | {'SHA256 (16)':<16}")
    print("-" * 75)
    for r in results:
        print(f"{r['slug']:<35} | {r['status']:<18} | {r['sha256']:<16}")
    print("-" * 75)
    print("Fetch completed. All required seed data and accessible regulations prepared.\n")


if __name__ == "__main__":
    main()
