package com.coolxer.synap.core;

import com.coolxer.securecommunication.FileIdentityStore;
import com.coolxer.securecommunication.SecureClient;
import com.coolxer.securecommunication.SecureClientConfig;
import com.coolxer.securecommunication.SecureClients;
import com.coolxer.securecommunication.SecureError;
import com.coolxer.securecommunication.SecureRequest;
import com.coolxer.securecommunication.SecureResponse;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/** Adapts Synap agent requests to the standard secure-communication Java SDK. */
final class SecureCommunicationClient implements AutoCloseable {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(2);
    private final SecureClient delegate;

    record Response(int status, String contentType, byte[] body) {}

    SecureCommunicationClient(AgentConfiguration config) throws SecureError {
        this(SecureClients.create(sdkConfig(config)));
        if (!config.enrollmentToken().isBlank()) {
            delegate.enroll(config.enrollmentToken());
        }
    }

    SecureCommunicationClient(SecureClient delegate) {
        if (delegate == null) throw new IllegalArgumentException("secure client is required");
        this.delegate = delegate;
    }

    Response request(String logicalPath, String code, byte[] body) throws TransportException {
        try {
            SecureResponse response = delegate.request(SecureRequest.builder()
                    .method("POST")
                    .logicalPath(logicalPath)
                    .contentType("application/json")
                    .protectedHeaders(Map.of("code", code))
                    .body(body)
                    .build());
            return new Response(
                    response.getStatus(), response.getContentType(), response.getBody());
        } catch (SecureError error) {
            throw TransportException.from(error);
        }
    }

    void closeSession() {
        delegate.closeSession();
    }

    @Override
    public void close() {
        delegate.close();
    }

    static SecureClientConfig sdkConfig(AgentConfiguration config) {
        if (config == null) throw new IllegalArgumentException("agent configuration is required");
        return SecureClientConfig.builder()
                .baseUrl(config.serverUrl())
                .appId(config.appKeyId())
                .deviceType("SERVER")
                .serverTrustAnchors(Map.of(config.serverKeyId(), config.trustAnchor()))
                .identityStore(new FileIdentityStore(identityPath(config)))
                .requestTimeout(REQUEST_TIMEOUT)
                .allowedClockSkew(CLOCK_SKEW)
                .build();
    }

    static Path identityPath(AgentConfiguration config) {
        return config.dataDir().resolve("secure-communication/java-sdk-identity.json");
    }

    static final class TransportException extends Exception {
        private final boolean retryable;
        private final int status;
        private final String code;
        private final String traceId;

        private TransportException(
                boolean retryable,
                int status,
                String code,
                String traceId,
                Throwable cause) {
            super("secure transport failed: " + code, cause);
            this.retryable = retryable;
            this.status = status;
            this.code = code;
            this.traceId = traceId;
        }

        static TransportException from(SecureError error) {
            int status = error.getHttpStatus();
            String code = error.getCode();
            boolean retryable = status == 429 || status >= 500
                    || "SC_NETWORK_FAILED".equals(code)
                    || "SC_REQUEST_TIMEOUT".equals(code)
                    || "SC_UNKNOWN_SESSION".equals(code);
            return new TransportException(
                    retryable, status, code, error.getTraceId(), error);
        }

        boolean retryable() { return retryable; }
        int status() { return status; }
        String code() { return code; }
        String traceId() { return traceId; }
        boolean unknownSession() { return "SC_UNKNOWN_SESSION".equals(code); }
    }
}
