package com.coolxer.synap.starter;

import com.coolxer.synap.bridge.AgentBridge;
import com.coolxer.synap.bridge.BridgeEvent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.security.web.SecurityFilterChain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SpringSecurityInstrumentation implements SmartInitializingSingleton {
    private final ObjectProvider<SecurityFilterChain> chains;
    SpringSecurityInstrumentation(ObjectProvider<SecurityFilterChain> chains) { this.chains = chains; }

    @Override public void afterSingletonsInstantiated() {
        List<SecurityFilterChain> configured = chains.orderedStream().toList(); Set<String> filters = new LinkedHashSet<>();
        configured.forEach(chain -> chain.getFilters().forEach(filter -> filters.add(filter.getClass().getName())));
        AgentBridge.emit(BridgeEvent.of("SecurityConfigData", Map.ofEntries(Map.entry("schemaVersion", 1),
                Map.entry("activeProfiles", List.of()), Map.entry("managementEndpointsExposed", ""),
                Map.entry("csrfConfigured", has(filters, "CsrfFilter")), Map.entry("corsConfigured", has(filters, "CorsFilter")),
                Map.entry("sessionConfigured", has(filters, "SessionManagementFilter")), Map.entry("anonymousEnabled", has(filters, "AnonymousAuthenticationFilter")),
                Map.entry("authenticationModes", modes(filters)), Map.entry("filterChains", configured.size()), Map.entry("capabilityMode", "spring-security"))));
    }
    private static boolean has(Set<String> filters, String suffix) { return filters.stream().anyMatch(value -> value.endsWith(suffix)); }
    private static List<String> modes(Set<String> filters) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (has(filters, "BasicAuthenticationFilter")) result.add("basic"); if (has(filters, "BearerTokenAuthenticationFilter")) result.add("bearer");
        if (has(filters, "UsernamePasswordAuthenticationFilter")) result.add("form"); if (has(filters, "OAuth2LoginAuthenticationFilter")) result.add("oauth2-login");
        return List.copyOf(result);
    }
}
