#!/usr/bin/env bash
set -euo pipefail

SYNAP_DEV_KEY_DIR=".local/sc-dev"
mkdir -p "$SYNAP_DEV_KEY_DIR"
chmod 700 "$SYNAP_DEV_KEY_DIR"

if [ ! -f "$SYNAP_DEV_KEY_DIR/server-private.pem" ]; then
  openssl genpkey \
    -algorithm EC \
    -pkeyopt ec_paramgen_curve:P-256 \
    -out "$SYNAP_DEV_KEY_DIR/server-private.pem"

  openssl pkey \
    -in "$SYNAP_DEV_KEY_DIR/server-private.pem" \
    -pubout \
    -out "$SYNAP_DEV_KEY_DIR/server-public.pem"

  chmod 600 "$SYNAP_DEV_KEY_DIR/server-private.pem"
fi

export SYNAP_SC_ENABLED=true
export SYNAP_SC_REQUIRE_TLS=false
export SYNAP_SC_SERVER_KEY_ID=server-key-dev

export SYNAP_SC_SERVER_PRIVATE_KEY="$(
  openssl pkcs8 \
    -topk8 \
    -nocrypt \
    -in "$SYNAP_DEV_KEY_DIR/server-private.pem" \
    -outform DER |
  openssl base64 -A |
  tr '+/' '-_' |
  tr -d '='
)"

export SYNAP_SC_SERVER_PUBLIC_KEY="$(
  openssl pkey \
    -pubin \
    -in "$SYNAP_DEV_KEY_DIR/server-public.pem" \
    -outform DER |
  openssl base64 -A |
  tr '+/' '-_' |
  tr -d '='
)"

export SYNAP_SC_SESSION_RECORD_KEY="$(
  openssl rand 32 |
  openssl base64 -A |
  tr '+/' '-_' |
  tr -d '='
)"

export SYNAP_SC_ALLOWED_APP_IDS="${SYNAP_SC_ALLOWED_APP_IDS:-synap-h5-dev,synap-host-dev}"
export SYNAP_SC_ENROLLMENT_ADMIN_TOKEN="${SYNAP_SC_ENROLLMENT_ADMIN_TOKEN:-$(openssl rand -hex 32)}"
export SYNAP_CORS_ALLOWED_ORIGINS='http://localhost:8888'

mvn spring-boot:run
