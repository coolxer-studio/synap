#!/usr/bin/env python3
"""Generate a per-device Synap provisioning NVS image without logging secrets."""

from __future__ import annotations

import argparse
import csv
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import uuid


NVS_KEYS = (
    ("wifi_ssid", "wifi_ssid", True),
    ("wifi_password", "wifi_pass", True),
    ("server_url", "server_url", True),
    ("tls_ca_pem", "tls_ca", False),
    ("sc_app_id", "sc_app_id", True),
    ("server_key_id", "server_kid", True),
    ("server_trust_spki", "trust_spki", True),
    ("enrollment_token", "enroll_token", True),
    ("business_app_id", "business_app", True),
    ("device_id", "device_id", True),
    ("device_name", "device_name", False),
)

APP_ID_PATTERN = re.compile(r"^[A-Za-z0-9._:@/-]{1,128}$")
BASE64URL_PATTERN = re.compile(r"^[A-Za-z0-9_-]+$")


def load_manifest(path: Path) -> dict[str, str]:
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"invalid manifest: {exc}") from exc
    if not isinstance(raw, dict):
        raise ValueError("manifest must be a JSON object")
    result: dict[str, str] = {}
    for source, _, required in NVS_KEYS:
        value = raw.get(source)
        if value is None and not required:
            continue
        if not isinstance(value, str) or not value:
            raise ValueError(f"{source} must be a non-empty string")
        if "<" in value or ">" in value:
            raise ValueError(f"{source} still contains a placeholder")
        result[source] = value
    try:
        uuid.UUID(result["device_id"])
    except (ValueError, KeyError) as exc:
        raise ValueError("device_id must be a UUID") from exc
    if not APP_ID_PATTERN.fullmatch(result["sc_app_id"]):
        raise ValueError("sc_app_id contains unsupported characters")
    if not result["business_app_id"].isdigit():
        raise ValueError("business_app_id must contain decimal digits only")
    if not BASE64URL_PATTERN.fullmatch(result["server_trust_spki"]):
        raise ValueError("server_trust_spki must be unpadded Base64URL")
    if not result["server_url"].startswith("https://"):
        raise ValueError("server_url must use HTTPS")
    return result


def generator_path(explicit: Path | None) -> Path:
    if explicit is not None:
        path = explicit
    else:
        idf_path = os.environ.get("IDF_PATH")
        if not idf_path:
            raise ValueError("IDF_PATH is required unless --generator is set")
        path = Path(idf_path) / "components" / "nvs_flash" / "nvs_partition_generator" / "nvs_partition_gen.py"
    if not path.is_file():
        raise ValueError(f"NVS generator not found: {path}")
    return path


def write_csv(path: Path, manifest: dict[str, str]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(("key", "type", "encoding", "value"))
        writer.writerow(("synap", "namespace", "", ""))
        for source, target, _ in NVS_KEYS:
            if source in manifest:
                writer.writerow((target, "data", "string", manifest[source]))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path, help="per-device JSON manifest")
    parser.add_argument("output", type=Path, help="output NVS partition image")
    parser.add_argument("--size", default="0x5000", help="partition size (default: 0x5000)")
    parser.add_argument("--key-file", type=Path, help="NVS encryption key file")
    parser.add_argument("--allow-plain-dev", action="store_true",
                        help="generate an unencrypted development image")
    parser.add_argument("--generator", type=Path,
                        help="path to ESP-IDF nvs_partition_gen.py")
    args = parser.parse_args()
    if args.key_file is None and not args.allow_plain_dev:
        parser.error("production generation requires --key-file; use --allow-plain-dev only locally")
    if args.key_file is not None and not args.key_file.is_file():
        parser.error("--key-file does not exist")
    try:
        manifest = load_manifest(args.manifest)
        generator = generator_path(args.generator)
    except ValueError as exc:
        parser.error(str(exc))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="synap-esp32-provision-") as temporary:
        csv_path = Path(temporary) / "provision.csv"
        write_csv(csv_path, manifest)
        if args.key_file is not None:
            command = [sys.executable, str(generator), "encrypt", str(csv_path),
                       str(args.output), args.size, "--inputkey", str(args.key_file)]
        else:
            command = [sys.executable, str(generator), "generate", str(csv_path),
                       str(args.output), args.size]
        subprocess.run(command, check=True, stdout=subprocess.DEVNULL)
    print(f"generated provisioning image: {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
