package com.coolxer.service;

import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.Header;
import com.coolxer.model.vo.RequestVO;
import com.coolxer.model.vo.RequestMeta;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class RequestValidationService {
    private static final Set<String> H5_FACT_TYPES = Set.of(
            "DeviceData", "SelfAppData", "RuntimeData", "LocationData", "IntegrityData",
            "BehaviorData", "SessionData", "NetworkData", "ErrorData", "EventData",
            "DebugData", "DevToolData", "UserData", "ResourceIntegrityData", "ActionData");
    private static final Set<String> WECHAT_FACT_TYPES = Set.of(
            "DeviceData", "SelfAppData", "RuntimeData", "IntegrityData", "SessionData",
            "ErrorData", "BehaviorData", "ActionData", "NetworkData", "LocationData",
            "UserData", "DebugData");
    private static final Set<String> HOST_FACT_TYPES = Set.of(
            "DeviceData", "FileData", "RuntimeData", "NetData", "EventData", "UserData", "AuditData");
    private static final Set<String> SERVER_FACT_TYPES = Set.of(
            "ApplicationData", "RuntimeData", "ApiAssetData", "ApiObservationData", "SecurityConfigData",
            "DependencyData", "DangerousCallData", "SecurityEventData", "AgentHealthData", "UserData", "ActionData");

    private final String[] globalOrigins;
    private final Map<String, String[]> appOrigins;
    private final boolean allowMissingOrigin;
    private final int maxBodyBytes;
    private final int maxFacts;
    private final int maxStringLength;
    @Value("${synap.security.wechat-appids:}")
    private String configuredWechatAppIds = "";
    @Value("${synap.security.require-wechat-appid:false}")
    private boolean requireWechatAppId;
    @Value("${synap.start-tracking.required-platforms:}")
    private String requiredStartTrackingPlatforms = "";

    public RequestValidationService(
            @Value("${synap.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}") String globalOrigins,
            @Value("${synap.security.app-origins:}") String appOrigins,
            @Value("${synap.security.allow-missing-origin:true}") boolean allowMissingOrigin,
            @Value("${synap.security.max-body-bytes:262144}") int maxBodyBytes,
            @Value("${synap.security.max-facts:100}") int maxFacts,
            @Value("${synap.security.max-string-length:8192}") int maxStringLength) {
        this.globalOrigins = split(globalOrigins, ",");
        this.appOrigins = parseAppOrigins(appOrigins);
        this.allowMissingOrigin = allowMissingOrigin;
        this.maxBodyBytes = maxBodyBytes;
        this.maxFacts = maxFacts;
        this.maxStringLength = maxStringLength;
    }

    public String validateEnvelope(String code, RequestVO request, HttpServletRequest servletRequest, boolean upload) {
        if (request == null || request.getCommon() == null || request.getCommon().size() != 24) return "common must contain 24 fields";
        long contentLength = servletRequest == null ? -1 : servletRequest.getContentLengthLong();
        if (contentLength > maxBodyBytes) return "request body is too large";
        if (upload && request.getData() != null && request.getData().size() > maxFacts) return "too many facts";
        Common common;
        try {
            Header header = new Header(code);
            common = com.coolxer.model.fact.FactBuilder.buildCommon(request.getCommon());
            if (!header.isConsistentWith(common)) return "header and common are inconsistent";
            if ("jvm".equalsIgnoreCase(common.getPlatform())) return "unsupported platform";
            if (!upload) {
                RequestMeta meta = request.getMeta();
                boolean hasTracking = meta != null && meta.hasStartTracking();
                if (requiresStartTracking(common.getPlatform()) && !hasTracking) {
                    return "start tracking metadata is required";
                }
                if (hasTracking && !meta.isValidStartTracking(common.getStartId())) {
                    return "invalid start tracking metadata";
                }
            }
            if (isProtocolPlatform(common.getPlatform())) {
                String label = protocolLabel(common.getPlatform());
                if (request.getMeta() == null) return label + " metadata is required";
                if (upload ? !request.getMeta().isValidFor(common.getStartId()) : !request.getMeta().isValidStart()) {
                    return upload ? "invalid " + label + " batch metadata" : "invalid " + label + " start metadata";
                }
                if ("h5".equalsIgnoreCase(common.getPlatform())) {
                    String browserError = validateBrowserContext(common, request.getMeta().getAppKeyId(), servletRequest);
                    if (browserError != null) return browserError;
                } else if ("wechat".equalsIgnoreCase(common.getPlatform())) {
                    String appError = validateWechatApp(common, request.getMeta().getAppKeyId());
                    if (appError != null) return appError;
                }
            }
        } catch (RuntimeException error) {
            return "invalid header or common";
        }
        if (upload && isProtocolPlatform(common.getPlatform()) && request.getData() != null) {
            Set<String> allowedTypes = allowedFactTypes(common.getPlatform());
            for (Map<String, Object> fact : request.getData()) {
                if (fact == null) return "fact is null";
                Object type = fact.get("type");
                if (!(type instanceof String) || !allowedTypes.contains(type)) return "unsupported fact type";
                if (!validValue(fact, 0)) return "fact exceeds structural limits";
                if ("wechat".equalsIgnoreCase(common.getPlatform()) && "SelfAppData".equals(type)) {
                    Object data = fact.get("data");
                    Object appId = data instanceof Map<?, ?> values ? values.get("appId") : null;
                    if (appId != null && !String.valueOf(appId).equals(common.getAppPackage())) {
                        return "WeChat fact appId and common appPackage are inconsistent";
                    }
                }
            }
        }
        return null;
    }

    private String validateWechatApp(Common common, String appKeyId) {
        if (common.getAppPackage() == null || common.getAppPackage().isBlank()) return "WeChat appPackage is required";
        Map<String, String[]> configured = parseAppOrigins(configuredWechatAppIds);
        String[] allowed = configured.get(appKeyId);
        if (allowed == null || allowed.length == 0) return requireWechatAppId ? "WeChat appId is not configured" : null;
        return java.util.Arrays.stream(allowed).anyMatch(value -> value.equals(common.getAppPackage()))
                ? null : "WeChat appId is not allowed";
    }

    private static boolean isProtocolPlatform(String platform) {
        return "h5".equalsIgnoreCase(platform) || "wechat".equalsIgnoreCase(platform)
                || "host".equalsIgnoreCase(platform) || "server".equalsIgnoreCase(platform);
    }

    private static Set<String> allowedFactTypes(String platform) {
        if ("h5".equalsIgnoreCase(platform)) return H5_FACT_TYPES;
        if ("wechat".equalsIgnoreCase(platform)) return WECHAT_FACT_TYPES;
        if ("server".equalsIgnoreCase(platform)) return SERVER_FACT_TYPES;
        return HOST_FACT_TYPES;
    }

    private static String protocolLabel(String platform) {
        if ("h5".equalsIgnoreCase(platform)) return "H5";
        if ("wechat".equalsIgnoreCase(platform)) return "WeChat";
        if ("server".equalsIgnoreCase(platform)) return "Server";
        return "Host";
    }

    private boolean requiresStartTracking(String platform) {
        if (platform == null || requiredStartTrackingPlatforms == null) return false;
        for (String configured : requiredStartTrackingPlatforms.split(",")) {
            if (platform.equalsIgnoreCase(configured.trim())) return true;
        }
        return false;
    }

    public String normalizedOrigin(HttpServletRequest request) {
        String origin = request == null ? null : request.getHeader("Origin");
        if (origin == null || origin.isBlank() || "null".equals(origin)) return "";
        try {
            URI uri = URI.create(origin);
            if (uri.getScheme() == null || uri.getHost() == null || uri.getUserInfo() != null || uri.getPath() != null && !uri.getPath().isEmpty()) return "";
            int port = uri.getPort();
            return uri.getScheme().toLowerCase() + "://" + uri.getHost().toLowerCase() + (port < 0 ? "" : ":" + port);
        } catch (RuntimeException ignored) { return ""; }
    }

    private String validateBrowserContext(Common common, String appKeyId, HttpServletRequest request) {
        String rawOrigin = request == null ? null : request.getHeader("Origin");
        String origin = normalizedOrigin(request);
        if (origin.isEmpty()) {
            if (rawOrigin != null || !allowMissingOrigin) return "origin is missing or invalid";
            return null;
        }
        String[] allowed = appOrigins.getOrDefault(appKeyId, globalOrigins);
        boolean permitted = java.util.Arrays.stream(allowed).anyMatch(pattern -> originMatches(origin, pattern));
        if (!permitted) return "origin is not allowed";
        try {
            String expectedHost = common.getAppPackage();
            if (expectedHost != null && !expectedHost.isBlank() && !URI.create(origin).getHost().equalsIgnoreCase(expectedHost)) {
                return "origin and common host are inconsistent";
            }
        } catch (RuntimeException error) { return "origin host is invalid"; }
        String mode = header(request, "Sec-Fetch-Mode");
        if (!mode.isEmpty() && !"cors".equals(mode) && !"same-origin".equals(mode)) return "unexpected fetch mode";
        String destination = header(request, "Sec-Fetch-Dest");
        if (!destination.isEmpty() && !"empty".equals(destination)) return "unexpected fetch destination";
        String site = header(request, "Sec-Fetch-Site");
        if (!site.isEmpty() && !Set.of("same-origin", "same-site", "cross-site").contains(site)) return "unexpected fetch site";
        String referer = header(request, "Referer");
        if (!referer.isEmpty()) {
            try {
                URI value = URI.create(referer);
                String refererOrigin = value.getScheme() + "://" + value.getHost() + (value.getPort() < 0 ? "" : ":" + value.getPort());
                if (!origin.equalsIgnoreCase(refererOrigin)) return "origin and referer are inconsistent";
            } catch (RuntimeException error) { return "referer is invalid"; }
        }
        return null;
    }

    private boolean validValue(Object value, int depth) {
        if (depth > 6) return false;
        if (value == null || value instanceof Number || value instanceof Boolean) return true;
        if (value instanceof String text) return text.length() <= maxStringLength;
        if (value instanceof Map<?, ?> map) {
            if (map.size() > 128) return false;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || key.length() > 64 || !validValue(entry.getValue(), depth + 1)) return false;
            }
            return true;
        }
        if (value instanceof Collection<?> collection) {
            if (collection.size() > 256) return false;
            for (Object item : collection) if (!validValue(item, depth + 1)) return false;
            return true;
        }
        return false;
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request == null ? null : request.getHeader(name);
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static boolean originMatches(String origin, String configured) {
        if (configured == null || configured.isBlank() || "*".equals(configured)) return false;
        String[] parts = configured.trim().split("\\*", -1);
        StringBuilder expression = new StringBuilder("^");
        for (int index = 0; index < parts.length; index++) {
            if (index > 0) expression.append(".*");
            expression.append(Pattern.quote(parts[index]));
        }
        return origin.matches(expression.append('$').toString());
    }

    private static String[] split(String value, String separator) {
        return java.util.Arrays.stream((value == null ? "" : value).split(separator))
                .map(String::trim).filter(part -> !part.isEmpty()).toArray(String[]::new);
    }

    private static Map<String, String[]> parseAppOrigins(String configured) {
        Map<String, String[]> values = new LinkedHashMap<>();
        for (String entry : split(configured, ";")) {
            String[] pair = entry.split("=", 2);
            if (pair.length == 2 && pair[0].trim().matches("[a-zA-Z0-9_-]{1,64}")) {
                values.put(pair[0].trim(), split(pair[1], "\\|"));
            }
        }
        return values;
    }
}
