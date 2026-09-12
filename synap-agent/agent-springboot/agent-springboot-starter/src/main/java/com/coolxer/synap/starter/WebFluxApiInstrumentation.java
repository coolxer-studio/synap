package com.coolxer.synap.starter;

import com.coolxer.synap.bridge.AgentBridge;
import com.coolxer.synap.bridge.BridgeEvent;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.result.method.RequestMappingInfo;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

final class WebFluxApiInstrumentation implements WebFilter, SmartInitializingSingleton {
    private static final Pattern SENSITIVE = Pattern.compile("(?i).*(password|token|secret|authorization|cookie|card|cvv|idnumber).*" );
    private final ObjectProvider<RequestMappingHandlerMapping> mappings; private final ApiObservationAggregator observations; private final int appId;
    private final Set<String> dynamicAssets = ConcurrentHashMap.newKeySet();
    WebFluxApiInstrumentation(ObjectProvider<RequestMappingHandlerMapping> mappings, ApiObservationAggregator observations, int appId) { this.mappings = mappings; this.observations = observations; this.appId = appId; }
    @Override public void afterSingletonsInstantiated() {
        RequestMappingHandlerMapping mapping = mappings.orderedStream().findFirst().orElse(null);
        if (mapping == null) return;
        List<Map<String, Object>> assets = new ArrayList<>(); mapping.getHandlerMethods().forEach((info, handler) -> {
            Set<String> routes = info.getPatternsCondition() == null ? Set.of("/") : info.getPatternsCondition().getPatterns().stream().map(Object::toString).collect(java.util.stream.Collectors.toSet());
            List<String> methods = info.getMethodsCondition().getMethods().isEmpty() ? List.of("ANY") : info.getMethodsCondition().getMethods().stream().map(Enum::name).toList();
            for (String route : routes) for (String method : methods) { Map<String, Object> value = new LinkedHashMap<>(); value.put("assetId", ApiAssetSupport.assetId(appId, method, route)); value.put("method", method); value.put("route", ApiAssetSupport.normalizeRoute(route)); value.put("framework", "spring-webflux"); value.put("handler", handler.getBeanType().getName() + "#" + handler.getMethod().getName()); value.put("authorization", "runtime"); assets.add(value); }
        });
        for (int index = 0; index < assets.size(); index += 100) AgentBridge.emit(BridgeEvent.of("ApiAssetData", Map.of("schemaVersion", 1, "assets", assets.subList(index, Math.min(index + 100, assets.size())))));
    }
    @Override public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String method = exchange.getRequest().getMethod().name(); String initial = "/dynamic/unmapped";
        return exchange.getPrincipal().map(ignored -> true).defaultIfEmpty(false).flatMap(authenticated -> Mono.defer(() -> {
            long start = System.nanoTime();
            var context = AgentBridge.openRequest(ApiAssetSupport.assetId(appId, method, initial), method, initial, authenticated,
                    ApiAssetSupport.dailyIpHash(exchange.getRequest().getRemoteAddress() == null ? "" : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()),
                    exchange.getRequest().getQueryParams().entrySet().stream().filter(entry -> !SENSITIVE.matcher(entry.getKey()).matches())
                            .flatMap(entry -> entry.getValue().stream()).limit(64).toList());
            return chain.filter(exchange).doFinally(signal -> { try {
                Object pattern = exchange.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE); String route = pattern == null ? initial : pattern.toString(); String assetId = ApiAssetSupport.assetId(appId, method, route);
                if (pattern == null && dynamicAssets.add(assetId)) AgentBridge.emit(BridgeEvent.of("ApiAssetData", Map.of("schemaVersion", 1,
                        "assets", List.of(Map.of("assetId", assetId, "method", method, "route", initial, "framework", "spring-webflux", "authorization", "runtime")))));
                observations.record(assetId, exchange.getResponse().getStatusCode() == null ? 200 : exchange.getResponse().getStatusCode().value(),
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), authenticated,
                        exchange.getRequest().getHeaders().getContentLength(), exchange.getResponse().getHeaders().getContentLength(),
                        String.valueOf(exchange.getRequest().getHeaders().getContentType()));
                if ("*".equals(exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"))
                        && "true".equalsIgnoreCase(exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Credentials"))) security("api-cors", "wildcard-with-credentials", "high", assetId);
                if (!exchange.getResponse().getHeaders().containsKey("X-Content-Type-Options") || !exchange.getResponse().getHeaders().containsKey("X-Frame-Options")) security("api-security-header", "missing-security-header", "low", assetId);
                if (exchange.getRequest().getHeaders().getContentLength() > 10L * 1024 * 1024) security("api-request", "oversized-request", "medium", assetId);
                if (exchange.getResponse().getStatusCode() != null && exchange.getResponse().getStatusCode().value() == 405) security("api-request", "unexpected-http-method", "medium", assetId);
            } finally { AgentBridge.closeRequest(); } }).contextWrite(reactorContext -> reactorContext.put(com.coolxer.synap.bridge.RequestSecurityContext.class, context));
        }));
    }
    private static void security(String category, String name, String severity, String assetId) { AgentBridge.emit(BridgeEvent.of("SecurityEventData", Map.of("schemaVersion", 1, "category", category, "name", name, "severity", severity, "assetId", assetId))); }
}
