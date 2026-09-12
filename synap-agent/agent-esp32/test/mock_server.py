#!/usr/bin/env python3
"""Small protocol-v1 HTTPS server for ESP32 hardware-in-loop tests only."""

from __future__ import annotations

import argparse
import base64
from dataclasses import dataclass, field
import hashlib
import json
import os
from pathlib import Path
import secrets
import ssl
import struct
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import (
    decode_dss_signature,
    encode_dss_signature,
)
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF


SUITE = "P256_HKDF_SHA256_AES256_GCM"
MESSAGE_PATH = "/sc/v1/message"
ENVELOPE_TYPE = "application/sc-envelope+json"
PROTECTED_TYPE = "application/sc-protected+json"


def b64e(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).decode().rstrip("=")


def b64d(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def public_der(key: ec.EllipticCurvePublicKey) -> bytes:
    return key.public_bytes(
        serialization.Encoding.DER,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    )


def p1363_sign(key: ec.EllipticCurvePrivateKey, transcript_hash: bytes) -> bytes:
    der = key.sign(transcript_hash, ec.ECDSA(hashes.SHA256()))
    r, s = decode_dss_signature(der)
    return r.to_bytes(32, "big") + s.to_bytes(32, "big")


def p1363_verify(
    key: ec.EllipticCurvePublicKey, transcript_hash: bytes, signature: bytes
) -> None:
    if len(signature) != 64:
        raise ValueError("invalid P1363 signature length")
    der = encode_dss_signature(
        int.from_bytes(signature[:32], "big"), int.from_bytes(signature[32:], "big")
    )
    key.verify(der, transcript_hash, ec.ECDSA(hashes.SHA256()))


def transcript(
    request: dict[str, Any], response: dict[str, Any], server_identity: bytes
) -> bytes:
    value = "\n".join(
        [
            "SC1-HANDSHAKE",
            "1",
            SUITE,
            request["appId"],
            request["deviceId"],
            request["deviceType"],
            request["clientEphemeralPublicKey"],
            request["installationPublicKey"],
            b64e(server_identity),
            response["serverEphemeralPublicKey"],
            response["kid"],
            response["sid"],
            str(response["createdAt"]),
            str(response["expiresAt"]),
        ]
    )
    return hashlib.sha256(value.encode()).digest()


def nonce(prefix: bytes, sequence: int) -> bytes:
    return prefix + struct.pack(">Q", sequence)


def aad(direction: str, envelope: dict[str, Any]) -> bytes:
    parts = [
        "SC1",
        direction,
        envelope["suite"],
        envelope["kid"],
        envelope["sid"],
        str(envelope["ts"]),
        str(envelope["seq"]),
        envelope["rid"],
        envelope["m"],
        envelope["p"],
        envelope["cty"],
    ]
    if direction == "response":
        parts.append(str(envelope["st"]))
    return "\n".join(parts).encode()


@dataclass
class Pending:
    app_id: str
    device_id: str
    installation: ec.EllipticCurvePublicKey
    transcript_hash: bytes
    material: bytes
    expires_at: int
    new_registration: bool


@dataclass
class Session:
    app_id: str
    device_id: str
    request_key: bytes
    response_key: bytes
    request_prefix: bytes
    response_prefix: bytes
    expires_at: int
    used_sequences: set[int] = field(default_factory=set)


@dataclass
class State:
    identity: ec.EllipticCurvePrivateKey
    key_id: str
    app_id: str
    enrollment_token: str
    ack_mode: str
    pending: dict[str, Pending] = field(default_factory=dict)
    sessions: dict[str, Session] = field(default_factory=dict)
    installations: dict[str, bytes] = field(default_factory=dict)
    accepted_messages: set[str] = field(default_factory=set)
    token_consumed: bool = False
    lock: threading.Lock = field(default_factory=threading.Lock)

    @property
    def identity_der(self) -> bytes:
        return public_der(self.identity.public_key())

    def start_handshake(self, request: dict[str, Any]) -> dict[str, Any]:
        required = {
            "v",
            "suite",
            "appId",
            "deviceId",
            "deviceType",
            "clientEphemeralPublicKey",
            "installationPublicKey",
            "timestamp",
        }
        if not required.issubset(request):
            raise ValueError("missing handshake fields")
        if (
            request["v"] != 1
            or request["suite"] != SUITE
            or request["appId"] != self.app_id
            or request["deviceType"] != "ESP32"
            or abs(int(time.time() * 1000) - int(request["timestamp"])) > 300_000
        ):
            raise PermissionError("handshake is not allowed")
        installation_der = b64d(request["installationPublicKey"])
        known = self.installations.get(request["deviceId"])
        if known is None:
            if self.token_consumed or request.get("enrollmentToken") != self.enrollment_token:
                raise PermissionError("invalid enrollment token")
            self.token_consumed = True
        elif known != installation_der:
            raise PermissionError("installation identity changed")
        installation = serialization.load_der_public_key(installation_der)
        client_ephemeral = serialization.load_der_public_key(
            b64d(request["clientEphemeralPublicKey"])
        )
        if not isinstance(installation, ec.EllipticCurvePublicKey) or not isinstance(
            client_ephemeral, ec.EllipticCurvePublicKey
        ):
            raise ValueError("keys must be EC public keys")
        ephemeral = ec.generate_private_key(ec.SECP256R1())
        now = int(time.time() * 1000)
        sid = b64e(secrets.token_bytes(18))
        response: dict[str, Any] = {
            "v": 1,
            "suite": SUITE,
            "kid": self.key_id,
            "sid": sid,
            "serverIdentityPublicKey": b64e(self.identity_der),
            "serverEphemeralPublicKey": b64e(public_der(ephemeral.public_key())),
            "createdAt": now,
            "expiresAt": now + 3_600_000,
        }
        digest = transcript(request, response, self.identity_der)
        response["signature"] = b64e(p1363_sign(self.identity, digest))
        secret = ephemeral.exchange(ec.ECDH(), client_ephemeral)
        material = HKDF(
            algorithm=hashes.SHA256(),
            length=72,
            salt=digest,
            info=f"SC1/session/{SUITE}/{sid}".encode(),
        ).derive(secret)
        self.pending[sid] = Pending(
            request["appId"],
            request["deviceId"],
            installation,
            digest,
            material,
            response["expiresAt"],
            known is None,
        )
        return response

    def finish_handshake(self, request: dict[str, Any]) -> dict[str, Any]:
        if request.get("kid") != self.key_id or request.get("sid") not in self.pending:
            raise PermissionError("unknown pending session")
        sid = request["sid"]
        pending = self.pending.pop(sid)
        p1363_verify(pending.installation, pending.transcript_hash, b64d(request["proof"]))
        self.installations[pending.device_id] = public_der(pending.installation)
        material = pending.material
        self.sessions[sid] = Session(
            pending.app_id,
            pending.device_id,
            material[:32],
            material[32:64],
            material[64:68],
            material[68:72],
            pending.expires_at,
        )
        return {"active": True, "expiresAt": pending.expires_at}

    def message(self, envelope: dict[str, Any]) -> tuple[int, dict[str, Any]]:
        sid = envelope.get("sid")
        session = self.sessions.get(sid)
        if session is None or session.expires_at <= int(time.time() * 1000):
            raise LookupError("SC_UNKNOWN_SESSION")
        sequence = envelope.get("seq")
        if (
            envelope.get("v") != 1
            or envelope.get("suite") != SUITE
            or envelope.get("kid") != self.key_id
            or envelope.get("m") != "POST"
            or envelope.get("p") != MESSAGE_PATH
            or envelope.get("cty") != PROTECTED_TYPE
            or envelope.get("st") != 0
            or not isinstance(sequence, int)
            or sequence < 1
        ):
            raise ValueError("invalid envelope")
        if sequence in session.used_sequences:
            raise LookupError("SC_REPLAY_DETECTED")
        expected_nonce = nonce(session.request_prefix, sequence)
        if b64d(envelope["nonce"]) != expected_nonce:
            raise ValueError("invalid nonce")
        plaintext = AESGCM(session.request_key).decrypt(
            expected_nonce, b64d(envelope["ct"]), aad("request", envelope)
        )
        protected = json.loads(plaintext)
        session.used_sequences.add(sequence)
        if protected.get("method") != "POST" or protected.get("path") not in {
            "/attach/start",
            "/attach/upload",
        }:
            raise PermissionError("logical route is not allowed")
        body = json.loads(b64d(protected["body"]))
        if protected["path"] == "/attach/start":
            business = {
                "status": 1,
                "config": {"startConfig": [1, 5, 300, 1, 300]},
                "agendas": [],
                "punishes": [],
            }
        else:
            meta = body.get("meta", {})
            facts = body.get("data", [])
            message_id = facts[0].get("messageId") if len(facts) == 1 else None
            if not message_id or not meta.get("batchId"):
                raise ValueError("invalid tracked upload")
            accepted: list[str] = []
            duplicate: list[str] = []
            rejected: list[dict[str, Any]] = []
            if message_id in self.accepted_messages:
                duplicate.append(message_id)
            elif self.ack_mode == "accepted":
                accepted.append(message_id)
                self.accepted_messages.add(message_id)
            elif self.ack_mode == "duplicate":
                duplicate.append(message_id)
            else:
                rejected.append(
                    {
                        "messageId": message_id,
                        "retryable": self.ack_mode == "retryable",
                        "reason": f"mock_{self.ack_mode}",
                    }
                )
            business = {
                "status": 1,
                "ack": {
                    "batchId": meta["batchId"],
                    "acceptedMessageIds": accepted,
                    "duplicateMessageIds": duplicate,
                    "rejectedItems": rejected,
                },
                "agendas": [],
                "punishes": [],
            }
        return 200, self.encrypt_response(session, envelope, 200, business)

    @staticmethod
    def encrypt_response(
        session: Session,
        request: dict[str, Any],
        status: int,
        business: dict[str, Any],
    ) -> dict[str, Any]:
        timestamp = int(time.time() * 1000)
        result = {
            "v": 1,
            "suite": SUITE,
            "kid": request["kid"],
            "sid": request["sid"],
            "ts": timestamp,
            "seq": request["seq"],
            "rid": request["rid"],
            "m": "POST",
            "p": MESSAGE_PATH,
            "cty": PROTECTED_TYPE,
            "st": status,
        }
        response_nonce = nonce(session.response_prefix, request["seq"])
        protected = json.dumps(
            {
                "contentType": "application/json",
                "body": b64e(json.dumps(business, separators=(",", ":")).encode()),
            },
            separators=(",", ":"),
        ).encode()
        result["nonce"] = b64e(response_nonce)
        result["ct"] = b64e(
            AESGCM(session.response_key).encrypt(
                response_nonce, protected, aad("response", result)
            )
        )
        return result


class Handler(BaseHTTPRequestHandler):
    server: "MockHTTPServer"

    def log_message(self, format: str, *args: Any) -> None:
        print(f"mock-server: {format % args}")

    def send_json(self, status: int, value: dict[str, Any], content_type: str) -> None:
        body = json.dumps(value, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self) -> None:  # noqa: N802
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > 262_144:
                raise ValueError("invalid body size")
            body = json.loads(self.rfile.read(length))
            with self.server.state.lock:
                if self.path == "/sc/v1/handshake":
                    result = self.server.state.start_handshake(body)
                    self.send_json(200, result, "application/json")
                elif self.path == "/sc/v1/handshake/finish":
                    result = self.server.state.finish_handshake(body)
                    self.send_json(200, result, "application/json")
                elif self.path == MESSAGE_PATH:
                    status, result = self.server.state.message(body)
                    self.send_json(status, result, ENVELOPE_TYPE)
                else:
                    self.send_json(404, {"code": "SC_ROUTE_NOT_ALLOWED"}, "application/json")
        except LookupError as exc:
            self.send_json(409, {"code": str(exc)}, "application/json")
        except PermissionError:
            self.send_json(403, {"code": "SC_HANDSHAKE_FAILED"}, "application/json")
        except Exception as exc:
            print(f"mock-server: rejected request: {type(exc).__name__}")
            self.send_json(400, {"code": "SC_INVALID_ENVELOPE"}, "application/json")


class MockHTTPServer(ThreadingHTTPServer):
    def __init__(self, address: tuple[str, int], state: State):
        super().__init__(address, Handler)
        self.state = state


def load_or_create_identity(path: Path) -> ec.EllipticCurvePrivateKey:
    if path.exists():
        key = serialization.load_pem_private_key(path.read_bytes(), password=None)
        if not isinstance(key, ec.EllipticCurvePrivateKey):
            raise ValueError("identity key is not an EC private key")
        return key
    path.parent.mkdir(parents=True, exist_ok=True)
    key = ec.generate_private_key(ec.SECP256R1())
    path.write_bytes(
        key.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption(),
        )
    )
    os.chmod(path, 0o600)
    return key


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=18443)
    parser.add_argument("--cert", type=Path, required=True)
    parser.add_argument("--tls-key", type=Path, required=True)
    parser.add_argument("--identity-key", type=Path, required=True)
    parser.add_argument("--key-id", default="synap-mock-key")
    parser.add_argument("--app-id", default="synap-esp32-dev")
    parser.add_argument(
        "--ack-mode",
        choices=("accepted", "duplicate", "retryable", "permanent"),
        default="accepted",
    )
    parser.add_argument("--print-trust-anchor", action="store_true")
    args = parser.parse_args()
    identity = load_or_create_identity(args.identity_key)
    if args.print_trust_anchor:
        print(b64e(public_der(identity.public_key())))
        return 0
    token = os.environ.get("SYNAP_MOCK_ENROLLMENT_TOKEN")
    if not token:
        parser.error("SYNAP_MOCK_ENROLLMENT_TOKEN is required")
    state = State(identity, args.key_id, args.app_id, token, args.ack_mode)
    server = MockHTTPServer((args.host, args.port), state)
    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    tls.minimum_version = ssl.TLSVersion.TLSv1_2
    tls.load_cert_chain(args.cert, args.tls_key)
    server.socket = tls.wrap_socket(server.socket, server_side=True)
    print(f"mock server listening on https://{args.host}:{args.port}")
    server.serve_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
