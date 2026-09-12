from __future__ import annotations

import json
import time

import pytest
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

from mock_server import (
    MESSAGE_PATH,
    PROTECTED_TYPE,
    SUITE,
    State,
    aad,
    b64d,
    b64e,
    nonce,
    p1363_sign,
    public_der,
    transcript,
)


def establish() -> tuple[State, str, bytes]:
    state = State(
        ec.generate_private_key(ec.SECP256R1()),
        "test-key",
        "synap-esp32-dev",
        "single-use-test-token",
        "accepted",
    )
    installation = ec.generate_private_key(ec.SECP256R1())
    ephemeral = ec.generate_private_key(ec.SECP256R1())
    request = {
        "v": 1,
        "suite": SUITE,
        "appId": "synap-esp32-dev",
        "deviceId": "test-device",
        "deviceType": "ESP32",
        "clientEphemeralPublicKey": b64e(public_der(ephemeral.public_key())),
        "installationPublicKey": b64e(public_der(installation.public_key())),
        "enrollmentToken": "single-use-test-token",
        "timestamp": int(time.time() * 1000),
    }
    response = state.start_handshake(request)
    digest = transcript(request, response, state.identity_der)
    secret = ephemeral.exchange(
        ec.ECDH(), serialization.load_der_public_key(b64d(response["serverEphemeralPublicKey"]))
    )
    material = HKDF(
        algorithm=hashes.SHA256(),
        length=72,
        salt=digest,
        info=f"SC1/session/{SUITE}/{response['sid']}".encode(),
    ).derive(secret)
    finish = state.finish_handshake(
        {
            "kid": response["kid"],
            "sid": response["sid"],
            "proof": b64e(p1363_sign(installation, digest)),
        }
    )
    assert finish["active"]
    return state, response["sid"], material


def upload_envelope(sid: str, material: bytes, sequence: int = 1) -> dict:
    body = {
        "meta": {"batchId": "batch-1"},
        "data": [{"messageId": "message-1", "type": "HealthData"}],
    }
    protected = {
        "method": "POST",
        "path": "/attach/upload",
        "contentType": "application/json",
        "headers": {"code": "device-esp32-1-1.0.0"},
        "body": b64e(json.dumps(body, separators=(",", ":")).encode()),
    }
    envelope = {
        "v": 1,
        "suite": SUITE,
        "kid": "test-key",
        "sid": sid,
        "ts": int(time.time() * 1000),
        "seq": sequence,
        "rid": f"request-{sequence}",
        "m": "POST",
        "p": MESSAGE_PATH,
        "cty": PROTECTED_TYPE,
        "st": 0,
        "nonce": b64e(nonce(material[64:68], sequence)),
    }
    envelope["ct"] = b64e(
        AESGCM(material[:32]).encrypt(
            nonce(material[64:68], sequence),
            json.dumps(protected, separators=(",", ":")).encode(),
            aad("request", envelope),
        )
    )
    return envelope


def test_mock_accepts_upload_and_rejects_replay() -> None:
    state, sid, material = establish()
    envelope = upload_envelope(sid, material)
    status, response = state.message(envelope)
    assert status == 200
    plain = AESGCM(material[32:64]).decrypt(
        nonce(material[68:72], 1), b64d(response["ct"]), aad("response", response)
    )
    protected = json.loads(plain)
    business = json.loads(b64d(protected["body"]))
    assert business["ack"]["acceptedMessageIds"] == ["message-1"]
    with pytest.raises(LookupError, match="SC_REPLAY_DETECTED"):
        state.message(envelope)


def test_mock_enrollment_token_cannot_register_a_second_device() -> None:
    state, _, _ = establish()
    installation = ec.generate_private_key(ec.SECP256R1())
    ephemeral = ec.generate_private_key(ec.SECP256R1())
    request = {
        "v": 1,
        "suite": SUITE,
        "appId": "synap-esp32-dev",
        "deviceId": "second-device",
        "deviceType": "ESP32",
        "clientEphemeralPublicKey": b64e(public_der(ephemeral.public_key())),
        "installationPublicKey": b64e(public_der(installation.public_key())),
        "enrollmentToken": "single-use-test-token",
        "timestamp": int(time.time() * 1000),
    }
    with pytest.raises(PermissionError, match="invalid enrollment token"):
        state.start_handshake(request)


@pytest.mark.parametrize("mode", ["duplicate", "retryable", "permanent"])
def test_mock_ack_modes(mode: str) -> None:
    state, sid, material = establish()
    state.ack_mode = mode
    _, response = state.message(upload_envelope(sid, material))
    protected = json.loads(
        AESGCM(material[32:64]).decrypt(
            nonce(material[68:72], 1), b64d(response["ct"]), aad("response", response)
        )
    )
    ack = json.loads(b64d(protected["body"]))["ack"]
    if mode == "duplicate":
        assert ack["duplicateMessageIds"] == ["message-1"]
    else:
        assert ack["rejectedItems"][0]["retryable"] is (mode == "retryable")
