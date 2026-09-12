package com.coolxer.controller;

import com.coolxer.securecommunication.protocol.SecureProtocolException;
import com.coolxer.securecommunication.spi.EnrollmentTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

@RestController
@ConditionalOnProperty(prefix = "synap.sc", name = "enabled", havingValue = "true")
public class SecureEnrollmentController {
    private static final Set<String> DEVICE_TYPES = Set.of("HOST", "ANDROID", "IOS", "EMULATOR", "SERVER");
    private final EnrollmentTokenService tokens;
    private final byte[] adminToken;

    public SecureEnrollmentController(
            EnrollmentTokenService tokens,
            @Value("${synap.sc.enrollment-admin-token}") String adminToken) {
        if (adminToken == null || adminToken.isBlank()) {
            throw new IllegalArgumentException(
                    "synap.sc.enrollment-admin-token is required when secure communication is enabled");
        }
        this.tokens = tokens;
        this.adminToken = adminToken.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/admin/sc/v1/enrollment-tokens")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> issue(
            @RequestHeader("X-Synap-Admin-Token") String supplied,
            @RequestBody EnrollmentRequest request) {
        if (!MessageDigest.isEqual(adminToken, supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (request.appId() == null || request.appId().isBlank()
                || !DEVICE_TYPES.contains(request.deviceType())
                || request.expiresInSeconds() < 30 || request.expiresInSeconds() > 3600) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid enrollment request");
        }
        try {
            String token = tokens.issue(request.appId(), request.deviceType(),
                    Duration.ofSeconds(request.expiresInSeconds()));
            return Map.of("token", token, "expiresInSeconds", request.expiresInSeconds());
        } catch (SecureProtocolException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "enrollment service unavailable");
        }
    }

    public record EnrollmentRequest(String appId, String deviceType, long expiresInSeconds) {}
}
