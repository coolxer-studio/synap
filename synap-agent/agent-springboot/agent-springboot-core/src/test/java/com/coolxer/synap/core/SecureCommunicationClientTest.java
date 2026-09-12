package com.coolxer.synap.core;

import com.coolxer.securecommunication.ExecutionOptions;
import com.coolxer.securecommunication.SecureClient;
import com.coolxer.securecommunication.SecureClientConfig;
import com.coolxer.securecommunication.SecureError;
import com.coolxer.securecommunication.SecureRequest;
import com.coolxer.securecommunication.SecureResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureCommunicationClientTest {
    @TempDir Path dataDir;

    @Test
    void mapsAgentConfigurationToServerSdkConfiguration() {
        AgentConfiguration agent = configuration("https://synap.example.test");

        SecureClientConfig sdk = SecureCommunicationClient.sdkConfig(agent);

        assertEquals(agent.serverUrl(), sdk.getBaseUrl());
        assertEquals("synap-agent-test", sdk.getAppId());
        assertEquals("SERVER", sdk.getDeviceType());
        assertEquals(Map.of("server-key", "AA"), sdk.getServerTrustAnchors());
        assertEquals(dataDir.resolve("secure-communication/java-sdk-identity.json"),
                SecureCommunicationClient.identityPath(agent));
    }

    @Test
    void mapsRemotePlainHttpConfiguration() {
        SecureClientConfig sdk = SecureCommunicationClient.sdkConfig(
                configuration("http://synap.example.test:11099"));

        assertEquals("http://synap.example.test:11099", sdk.getBaseUrl().toString());
    }

    @Test
    void mapsLogicalRequestAndResponseWithoutChangingBusinessBody() throws Exception {
        RecordingSecureClient sdk = new RecordingSecureClient();
        sdk.response = new SecureResponse(
                202, "application/json", "{\"status\":1}".getBytes(StandardCharsets.UTF_8));
        SecureCommunicationClient client = new SecureCommunicationClient(sdk);
        byte[] body = "{\"data\":[]}".getBytes(StandardCharsets.UTF_8);

        SecureCommunicationClient.Response response =
                client.request("/attach/upload", "agent-code", body);

        assertEquals("POST", sdk.request.getMethod());
        assertEquals("/attach/upload", sdk.request.getLogicalPath());
        assertEquals("application/json", sdk.request.getContentType());
        assertEquals(Map.of("code", "agent-code"), sdk.request.getProtectedHeaders());
        assertArrayEquals(body, sdk.request.getBody());
        assertEquals(202, response.status());
        assertArrayEquals(sdk.response.getBody(), response.body());
    }

    @Test
    void returnsBusinessAndBackpressureStatusesForRuntimePolicy() throws Exception {
        RecordingSecureClient sdk = new RecordingSecureClient();
        SecureCommunicationClient client = new SecureCommunicationClient(sdk);
        for (int status : new int[]{400, 429, 503}) {
            sdk.response = new SecureResponse(status, "application/json", new byte[0]);
            assertEquals(status,
                    client.request("/attach/upload", "code", new byte[0]).status());
        }
    }

    @Test
    void exposesUnknownSessionForRuntimeRecovery() {
        RecordingSecureClient sdk = new RecordingSecureClient();
        sdk.error = new SecureError(
                "SC_UNKNOWN_SESSION", "expired", 401, "trace-1", null);
        SecureCommunicationClient client = new SecureCommunicationClient(sdk);

        SecureCommunicationClient.TransportException error = assertThrows(
                SecureCommunicationClient.TransportException.class,
                () -> client.request("/attach/start", "code", new byte[0]));

        assertTrue(error.unknownSession());
        assertTrue(error.retryable());
        assertEquals(401, error.status());
        assertEquals("trace-1", error.traceId());
    }

    @Test
    void classifiesNetworkTimeoutAndServerFailuresAsRetryable() {
        for (SecureError failure : new SecureError[]{
                new SecureError("SC_NETWORK_FAILED", "network"),
                new SecureError("SC_REQUEST_TIMEOUT", "timeout"),
                new SecureError("SC_TRANSPORT_FAILED", "server", 503, null, null),
                new SecureError("SC_TRANSPORT_FAILED", "backpressure", 429, null, null)}) {
            assertTrue(SecureCommunicationClient.TransportException.from(failure).retryable());
        }
        assertFalse(SecureCommunicationClient.TransportException.from(
                new SecureError("SC_TRANSPORT_FAILED", "business", 400, null, null))
                .retryable());
    }

    private AgentConfiguration configuration(String serverUrl) {
        return AgentConfiguration.from(Map.ofEntries(
                Map.entry("server-url", serverUrl),
                Map.entry("app-id", "1"),
                Map.entry("app-name", "sdk-test"),
                Map.entry("app-key-id", "synap-agent-test"),
                Map.entry("server-key-id", "server-key"),
                Map.entry("trust-anchor", "AA"),
                Map.entry("data-dir", dataDir.toString())));
    }

    private static final class RecordingSecureClient implements SecureClient {
        private SecureRequest request;
        private SecureResponse response;
        private SecureError error;

        @Override public void enroll(String token) {}
        @Override public void initialize() {}
        @Override public void initialize(ExecutionOptions options) {}
        @Override public CompletableFuture<Void> initializeAsync() {
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletableFuture<Void> initializeAsync(ExecutionOptions options) {
            return CompletableFuture.completedFuture(null);
        }
        @Override public SecureResponse request(SecureRequest request) throws SecureError {
            this.request = request;
            if (error != null) throw error;
            return response;
        }
        @Override public SecureResponse request(
                SecureRequest request, ExecutionOptions options) throws SecureError {
            return request(request);
        }
        @Override public CompletableFuture<SecureResponse> requestAsync(SecureRequest request) {
            try { return CompletableFuture.completedFuture(request(request)); }
            catch (SecureError failure) { return CompletableFuture.failedFuture(failure); }
        }
        @Override public CompletableFuture<SecureResponse> requestAsync(
                SecureRequest request, ExecutionOptions options) {
            return requestAsync(request);
        }
        @Override public void closeSession() {}
    }
}
