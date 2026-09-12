package com.coolxer.synap.starter;

import com.coolxer.synap.bridge.AgentBridge;
import com.coolxer.synap.bridge.BridgeEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

final class MvcApiInstrumentation implements WebMvcConfigurer, SmartInitializingSingleton {
    private static final String START = MvcApiInstrumentation.class.getName() + ".start";
    private static final Pattern SENSITIVE = Pattern.compile("(?i).*(password|token|secret|authorization|cookie|card|cvv|idnumber).*");
    private final ObjectProvider<RequestMappingHandlerMapping> mappings; private final ApiObservationAggregator observations; private final int appId;
    private final Set<String> dynamicAssets = ConcurrentHashMap.newKeySet();
    MvcApiInstrumentation(ObjectProvider<RequestMappingHandlerMapping> mappings, ApiObservationAggregator observations, int appId) { this.mappings = mappings; this.observations = observations; this.appId = appId; }
    @Override public void afterSingletonsInstantiated() {
        List<Map<String, Object>> assets = new ArrayList<>();
        RequestMappingHandlerMapping mapping = mappings.orderedStream().findFirst().orElse(null);
        if (mapping == null) return;
        mapping.getHandlerMethods().forEach((info, handler) -> assets.addAll(assets(info, handler)));
        for (int index = 0; index < assets.size(); index += 100) AgentBridge.emit(BridgeEvent.of("ApiAssetData", Map.of("schemaVersion", 1, "assets", assets.subList(index, Math.min(index + 100, assets.size())))));
    }
    private List<Map<String, Object>> assets(RequestMappingInfo info, HandlerMethod handler) {
        Set<String> routes = info.getPatternValues().isEmpty() ? Set.of("/") : info.getPatternValues();
        Set<org.springframework.web.bind.annotation.RequestMethod> configured = info.getMethodsCondition().getMethods();
        List<String> methods = configured.isEmpty() ? List.of("ANY") : configured.stream().map(Enum::name).toList(); List<Map<String, Object>> values = new ArrayList<>();
        for (String route : routes) for (String method : methods) { Map<String, Object> value = new LinkedHashMap<>(); String normalized = ApiAssetSupport.normalizeRoute(route);
            value.put("assetId", ApiAssetSupport.assetId(appId, method, normalized)); value.put("method", method); value.put("route", normalized); value.put("framework", "spring-mvc");
            value.put("handler", handler.getBeanType().getName() + "#" + handler.getMethod().getName()); value.put("consumes", info.getConsumesCondition().getConsumableMediaTypes().stream().map(Object::toString).toList());
            value.put("produces", info.getProducesCondition().getProducibleMediaTypes().stream().map(Object::toString).toList()); value.put("authorization", authorization(handler));
            value.put("parameterTypes", Arrays.stream(handler.getMethodParameters()).map(parameter -> parameter.getParameterType().getName()).toList()); values.add(value); }
        return values;
    }
    private static String authorization(HandlerMethod handler) {
        PreAuthorize pre = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), PreAuthorize.class); if (pre != null) return "pre-authorize";
        Secured secured = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), Secured.class); return secured == null ? "unspecified" : "secured";
    }
    @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(new HandlerInterceptor() {
        @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            String route = String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)); if ("null".equals(route)) route = "/dynamic/unmapped";
            String assetId = ApiAssetSupport.assetId(appId, request.getMethod(), route); boolean authenticated = request.getUserPrincipal() != null;
            if ("/dynamic/unmapped".equals(route) && dynamicAssets.add(assetId)) AgentBridge.emit(BridgeEvent.of("ApiAssetData", Map.of("schemaVersion", 1,
                    "assets", List.of(Map.of("assetId", assetId, "method", request.getMethod(), "route", route, "framework", "spring-mvc", "authorization", "runtime")))));
            AgentBridge.openRequest(assetId, request.getMethod(), route, authenticated, ApiAssetSupport.dailyIpHash(request.getRemoteAddr()), localInputs(request)); request.setAttribute(START, System.nanoTime()); return true;
        }
        @Override public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
            try { var context = AgentBridge.currentRequest(); String assetId = context == null ? ApiAssetSupport.assetId(appId, request.getMethod(), "/dynamic/unmapped") : context.assetId();
                long started = request.getAttribute(START) instanceof Long value ? value : System.nanoTime();
                observations.record(assetId, response.getStatus(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                        request.getUserPrincipal() != null, request.getContentLengthLong(), parseLength(response.getHeader("Content-Length")), request.getContentType());
                if (!response.containsHeader("X-Content-Type-Options") || !response.containsHeader("X-Frame-Options")) security("api-security-header", "missing-security-header", "low", assetId);
                if ("*".equals(response.getHeader("Access-Control-Allow-Origin")) && "true".equalsIgnoreCase(response.getHeader("Access-Control-Allow-Credentials"))) security("api-cors", "wildcard-with-credentials", "high", assetId);
                if (request.getContentLengthLong() > 10L * 1024 * 1024) security("api-request", "oversized-request", "medium", assetId);
                if (response.getStatus() == 405) security("api-request", "unexpected-http-method", "medium", assetId);
            } finally { AgentBridge.closeRequest(); }
        }
    }); }
    private static void security(String category, String name, String severity, String assetId) { AgentBridge.emit(BridgeEvent.of("SecurityEventData", Map.of("schemaVersion", 1, "category", category, "name", name, "severity", severity, "assetId", assetId))); }
    private static long parseLength(String value) { try { return value == null ? 0 : Long.parseLong(value); } catch (NumberFormatException ignored) { return 0; } }
    private static List<String> localInputs(HttpServletRequest request) { List<String> values = new ArrayList<>(); request.getParameterMap().forEach((key, entries) -> { if (!SENSITIVE.matcher(key).matches()) for (String value : entries) if (value != null && values.size() < 64) values.add(value); }); return values; }
}
