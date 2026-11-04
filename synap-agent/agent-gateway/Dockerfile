# syntax=docker/dockerfile:1

# ----------------------------------------------------------------------------
# Build stage: compile a static, pure-Go binary.
# ----------------------------------------------------------------------------
FROM golang:1.22-alpine AS builder

WORKDIR /src

# Cache module downloads independently of the source.
COPY go.mod go.sum ./
RUN go mod download

# Build the application.
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build \
        -trimpath \
        -buildvcs=false \
        -ldflags="-s -w" \
        -o /out/agent-gateway .

# ----------------------------------------------------------------------------
# Runtime stage: minimal image with CA roots and timezone data.
# ----------------------------------------------------------------------------
FROM alpine:3.20

# ca-certificates: TLS to Kafka/upstream; tzdata: correct log timestamps.
RUN apk add --no-cache ca-certificates tzdata \
    && adduser -D -u 10001 -h /home/app app

COPY --from=builder /out/agent-gateway /usr/local/bin/agent-gateway

# Runtime data (fallback spool, local archive) lives in /data, owned by the
# unprivileged user. The default spool path "agent-gateway-spool.ndjson" resolves
# against WORKDIR, so it lands here.
RUN mkdir -p /data && chown app:app /data
WORKDIR /data
USER app

EXPOSE 8080
VOLUME ["/data"]

# Probes /__agent-gateway/health. If you override --listen, also set LISTEN_PORT.
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -qO- "http://127.0.0.1:${LISTEN_PORT:-8080}/__agent-gateway/health" >/dev/null 2>&1 || exit 1

LABEL org.opencontainers.image.title="agent-gateway" \
      org.opencontainers.image.description="Agent gateway: forward requests and ship formatted transaction logs to Kafka" \
      org.opencontainers.image.source="https://example.invalid/agent-gateway"

# Args after the image name are appended to the probe.
ENTRYPOINT ["/usr/local/bin/agent-gateway"]
