package com.coolxer.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Prevents direct plaintext access to controllers protected by protocol v1. */
public final class SecureRouteEnforcementFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (isProtectedRoute(request.getRequestURI())
                && !isAuthenticated(request.getAttribute("sc.transportTrust"))) {
            response.reset();
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        filterChain.doFilter(request, response);
    }

    static boolean isProtectedRoute(String path) {
        return path != null && (path.equals("/attach") || path.startsWith("/attach/")
                || path.equals("/cross") || path.startsWith("/cross/")
                || path.equals("/1") || path.startsWith("/1/")
                || path.equals("/risk") || path.startsWith("/risk/")
                || path.equals("/2") || path.startsWith("/2/")
                || path.equals("/device_index") || path.startsWith("/device_index/"));
    }

    private static boolean isAuthenticated(Object trust) {
        return trust instanceof String
                && ((String) trust).startsWith("sc1-authenticated/");
    }
}
