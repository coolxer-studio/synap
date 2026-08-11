package com.coolxer.config;

import com.coolxer.securecommunication.core.AesGcmSessionRecordProtector;
import com.coolxer.securecommunication.core.P256ServerIdentityProvider;
import com.coolxer.securecommunication.core.RedisEnrollmentTokenService;
import com.coolxer.securecommunication.core.RedisInstallationRegistry;
import com.coolxer.securecommunication.core.RedisReplayProtector;
import com.coolxer.securecommunication.core.RedisSessionRepository;
import com.coolxer.securecommunication.protocol.SecureErrorCode;
import com.coolxer.securecommunication.protocol.SecureProtocolException;
import com.coolxer.securecommunication.spi.EnrollmentTokenService;
import com.coolxer.securecommunication.spi.HandshakeAuthorizer;
import com.coolxer.securecommunication.spi.InstallationRegistry;
import com.coolxer.securecommunication.spi.LogicalRouteAuthorizer;
import com.coolxer.securecommunication.spi.ReplayProtector;
import com.coolxer.securecommunication.spi.ServerIdentityProvider;
import com.coolxer.securecommunication.spi.SessionRecordProtector;
import com.coolxer.securecommunication.spi.SessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Configuration
@ConditionalOnProperty(prefix = "synap.sc", name = "enabled", havingValue = "true")
public class SecureCommunicationConfiguration {
    @Bean
    FilterRegistrationBean<SecureRouteEnforcementFilter> synapSecureRouteEnforcementFilter() {
        FilterRegistrationBean<SecureRouteEnforcementFilter> registration =
                new FilterRegistrationBean<>(new SecureRouteEnforcementFilter());
        registration.setName("synapSecureRouteEnforcementFilter");
        registration.addUrlPatterns("/*");
        registration.setOrder(-190);
        return registration;
    }

    @Bean
    ServerIdentityProvider synapServerIdentity(
            @Value("${synap.sc.server-key-id}") String keyId,
            @Value("${synap.sc.server-private-key-pkcs8}") String privateKey,
            @Value("${synap.sc.server-public-key-spki}") String publicKey) throws Exception {
        KeyFactory factory = KeyFactory.getInstance("EC");
        PrivateKey privateValue = factory.generatePrivate(
                new PKCS8EncodedKeySpec(decode(privateKey)));
        PublicKey publicValue = factory.generatePublic(
                new X509EncodedKeySpec(decode(publicKey)));
        return new P256ServerIdentityProvider(keyId, privateValue, publicValue);
    }

    @Bean
    SessionRecordProtector synapSessionRecordProtector(
            @Value("${synap.sc.session-record-key}") String key) {
        return new AesGcmSessionRecordProtector(decode(key));
    }

    @Bean
    SessionRepository synapSessionRepository(
            StringRedisTemplate redis, SessionRecordProtector protector) {
        return new RedisSessionRepository(redis, "synap:sc1:session", protector);
    }

    @Bean
    ReplayProtector synapReplayProtector(StringRedisTemplate redis) {
        return new RedisReplayProtector(redis, "synap:sc1:replay");
    }

    @Bean
    InstallationRegistry synapInstallationRegistry(StringRedisTemplate redis) {
        return new RedisInstallationRegistry(redis, "synap:sc1:installation");
    }

    @Bean
    EnrollmentTokenService synapEnrollmentTokens(StringRedisTemplate redis) {
        return new RedisEnrollmentTokenService(redis, "synap:sc1:enrollment");
    }

    @Bean
    LogicalRouteAuthorizer synapLogicalRoutes() {
        return (method, path) -> "POST".equals(method)
                && ("/attach/start".equals(path)
                || "/attach/upload".equals(path)
                || path.matches("/cross/[A-Za-z0-9_-]{1,64}"));
    }

    @Bean
    HandshakeAuthorizer synapHandshakeAuthorizer(
            @Value("${synap.sc.allowed-app-ids}") String configuredAppIds,
            @Value("${synap.cors.allowed-origin-patterns:}") String configuredOrigins) {
        Set<String> appIds = csv(configuredAppIds);
        java.util.List<Pattern> origins = csv(configuredOrigins).stream()
                .map(SecureCommunicationConfiguration::wildcard).toList();
        return context -> {
            if (!appIds.contains(context.appId())) {
                throw new SecureProtocolException(SecureErrorCode.HANDSHAKE_FAILED);
            }
            if ("H5".equals(context.deviceType())
                    && (context.origin() == null || origins.stream().noneMatch(
                    pattern -> pattern.matcher(context.origin()).matches()))) {
                throw new SecureProtocolException(SecureErrorCode.HANDSHAKE_FAILED);
            }
        };
    }

    private static byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value.trim());
    }

    private static Set<String> csv(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim).filter(item -> !item.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Pattern wildcard(String value) {
        return Pattern.compile("^" + Pattern.quote(value).replace("*", "\\E.*\\Q") + "$");
    }
}
