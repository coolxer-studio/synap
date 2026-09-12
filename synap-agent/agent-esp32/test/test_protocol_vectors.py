from __future__ import annotations

import base64
import json
from pathlib import Path

import pytest
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

from mock_server import p1363_verify, transcript


VECTORS = Path(__file__).parent / "vectors"


def b64decode(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


@pytest.mark.parametrize("name", ["aes-256-gcm-request.json", "aes-256-gcm-response.json"])
def test_aes_gcm_vector(name: str) -> None:
    vector = json.loads((VECTORS / name).read_text(encoding="utf-8"))
    key = bytes.fromhex(vector["keyHex"])
    expected_nonce = bytes.fromhex(vector["noncePrefixHex"]) + int(
        vector["sequence"]
    ).to_bytes(8, "big")
    assert b64decode(vector["nonceBase64Url"]) == expected_nonce
    encrypted = AESGCM(key).encrypt(
        expected_nonce,
        vector["plaintextUtf8"].encode(),
        vector["aadUtf8"].encode(),
    )
    assert encrypted == b64decode(vector["combinedCiphertextBase64Url"])
    assert AESGCM(key).decrypt(
        expected_nonce,
        encrypted,
        vector["aadUtf8"].encode(),
    ).decode() == vector["plaintextUtf8"]


def test_tampered_aad_is_rejected() -> None:
    vector = json.loads((VECTORS / "aes-256-gcm-request.json").read_text())
    key = bytes.fromhex(vector["keyHex"])
    nonce = b64decode(vector["nonceBase64Url"])
    cipher = b64decode(vector["combinedCiphertextBase64Url"])
    with pytest.raises(Exception):
        AESGCM(key).decrypt(nonce, cipher, b"SC1\nrequest\ntampered")


def test_handshake_transcript_hkdf_and_p1363_vector() -> None:
    request = {
        "appId": "app",
        "deviceId": "device",
        "deviceType": "ESP32",
        "clientEphemeralPublicKey": "client-key",
        "installationPublicKey": "install-key",
    }
    response = {
        "serverEphemeralPublicKey": "server-ephemeral",
        "kid": "kid",
        "sid": "sid",
        "createdAt": 1700000000000,
        "expiresAt": 1700000300000,
    }
    digest = transcript(request, response, b"server-key")
    assert digest.hex() == (
        "5172456c436f81c192f842f289d1a91c7f9ce7cf59cece0b6d69a2532ac41051"
    )
    material = HKDF(
        algorithm=hashes.SHA256(),
        length=72,
        salt=digest,
        info=b"SC1/session/P256_HKDF_SHA256_AES256_GCM/sid",
    ).derive(bytes(range(32)))
    assert material.hex() == (
        "22735b9e62e24a9f9a9f263e84a1d9b877cf38ade75b2be55a4eeba7f41eff80"
        "fb3e5c63836cdadbc951cde8c3b620089c52dbfc095f8090f9eebc223b59c438"
        "200f75443b990a18"
    )
    public_key = serialization.load_der_public_key(
        b64decode(
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaxfR8uEsQkf4vOblY6RA8ncDfYEt"
            "6zOg9KE5RdiYwpZP40Li_hp_m47n60p8D54WK84zV2sxXs7LtkBoN79R9Q"
        )
    )
    p1363_verify(
        public_key,
        digest,
        b64decode(
            "H2dEaaiBAClbcCFGQu49sT8YlGYFH1G8AaK8Ec7cXDt5Tgxc3HttK0Oa_AeCgkOo"
            "et46_iFKz4di4xl3INydiA"
        ),
    )
