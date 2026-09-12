from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import uuid

import pytest


SCRIPT = Path(__file__).parents[1] / "tools" / "generate_provisioning.py"
SPEC = importlib.util.spec_from_file_location("generate_provisioning", SCRIPT)
assert SPEC and SPEC.loader
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)


def valid_manifest() -> dict[str, str]:
    return {
        "wifi_ssid": "factory-network",
        "wifi_password": "random-development-password",
        "server_url": "https://192.0.2.10:18443",
        "tls_ca_pem": "-----BEGIN CERTIFICATE-----\nTEST\n-----END CERTIFICATE-----",
        "sc_app_id": "synap-esp32-dev",
        "server_key_id": "mock-key",
        "server_trust_spki": "AQIDBA",
        "enrollment_token": "one-time-random-value",
        "business_app_id": "1",
        "device_id": str(uuid.uuid4()),
        "device_name": "test-board",
    }


def test_manifest_is_validated_and_csv_uses_expected_namespace(tmp_path: Path) -> None:
    manifest_path = tmp_path / "device.json"
    manifest_path.write_text(json.dumps(valid_manifest()), encoding="utf-8")
    loaded = module.load_manifest(manifest_path)
    csv_path = tmp_path / "device.csv"
    module.write_csv(csv_path, loaded)
    encoded = csv_path.read_text(encoding="utf-8")
    assert "synap,namespace" in encoded
    assert "enroll_token,data,string" in encoded
    assert "server_url,data,string,https://" in encoded


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("server_url", "http://insecure.example"),
        ("business_app_id", "not-numeric"),
        ("device_id", "not-a-uuid"),
        ("server_trust_spki", "not+padded="),
    ],
)
def test_invalid_manifest_is_rejected(tmp_path: Path, field: str, value: str) -> None:
    manifest = valid_manifest()
    manifest[field] = value
    path = tmp_path / "invalid.json"
    path.write_text(json.dumps(manifest), encoding="utf-8")
    with pytest.raises(ValueError):
        module.load_manifest(path)


def test_checked_in_sample_cannot_be_used_as_a_real_device() -> None:
    sample = Path(__file__).parents[1] / "provisioning" / "sample-device.json"
    with pytest.raises(ValueError, match="placeholder"):
        module.load_manifest(sample)

