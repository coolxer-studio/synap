from __future__ import annotations

from pathlib import Path
import re


ROOT = Path(__file__).parents[1]


def source_text() -> str:
    paths = list((ROOT / "components").rglob("*.c")) + list(
        (ROOT / "components").rglob("*.h")
    )
    return "\n".join(path.read_text(encoding="utf-8") for path in paths)


def test_only_protocol_v1_outer_routes_are_literal() -> None:
    text = source_text()
    routes = set(re.findall(r'"(/sc/v1/[A-Za-z0-9_/-]+)"', text))
    assert routes == {
        "/sc/v1/handshake",
        "/sc/v1/handshake/finish",
        "/sc/v1/message",
    }


def test_no_insecure_tls_switches() -> None:
    text = source_text()
    assert "skip_cert_common_name_check" not in text
    assert "cert_pem = NULL" not in text
    assert "disable_tls" not in text.lower()


def test_no_embedded_enrollment_token() -> None:
    text = source_text()
    assert not re.search(r'enroll(?:ment)?[_-]?token\s*=\s*"[^\"]+"', text, re.I)


def test_enrollment_token_is_loaded_only_inside_handshake() -> None:
    config = (ROOT / "components/synap_agent/src/synap_config.c").read_text(
        encoding="utf-8"
    )
    transport = (ROOT / "components/synap_agent/src/synap_sc.c").read_text(
        encoding="utf-8"
    )
    load_body = config.split("esp_err_t synap_config_load", 1)[1].split(
        "esp_err_t synap_config_read_enrollment_token", 1
    )[0]
    assert 'READ_FIELD("enroll_token"' not in load_body
    assert "synap_config_read_enrollment_token(" in transport
    assert transport.index("synap_config_read_enrollment_token(") > transport.index(
        "esp_err_t synap_sc_initialize("
    )


def test_generated_provisioning_is_ignored() -> None:
    ignore = (ROOT / ".gitignore").read_text(encoding="utf-8")
    assert "provisioning/generated/" in ignore
    assert "test/generated/" in ignore
    assert "*.pem" in ignore and "*.key" in ignore and "*.nvs" in ignore
