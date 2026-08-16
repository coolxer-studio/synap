package com.coolxer.synap.bridge;

import java.util.ArrayList;
import java.util.List;

/** Request-local context. Raw inputs live only for the request and are never included in events. */
public record RequestSecurityContext(
        String correlationId,
        String assetId,
        String method,
        String route,
        boolean authenticated,
        String sourceIpHash,
        List<String> localInputs) {
    public RequestSecurityContext {
        localInputs = localInputs == null ? List.of() : List.copyOf(new ArrayList<>(localInputs));
    }
}
