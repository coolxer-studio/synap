package com.coolxer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * 设置允许跨域，js探针需要跨域访问.
 */

@Configuration
public class CrossDomainConfigure {

  private final String[] allowedOrigins;

  public CrossDomainConfigure(@Value("${synap.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}") String allowedOrigins) {
    this.allowedOrigins = java.util.Arrays.stream(allowedOrigins.split(","))
        .map(String::trim).filter(value -> !value.isEmpty()).toArray(String[]::new);
  }

  @Bean
  FilterRegistrationBean<CorsFilter> synapSecureCommunicationCorsFilter() {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOriginPatterns(Arrays.asList(allowedOrigins));
    cors.setAllowedMethods(List.of("POST", "OPTIONS"));
    cors.setAllowedHeaders(List.of("Content-Type", "Accept", "X-Requested-With"));
    cors.setAllowCredentials(false);
    cors.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/sc/v1/**", cors);

    FilterRegistrationBean<CorsFilter> registration =
        new FilterRegistrationBean<>(new CorsFilter(source));
    registration.setName("synapSecureCommunicationCorsFilter");
    registration.addUrlPatterns("/sc/v1/*");
    // Must wrap the secure-message filter (-200), because that filter rewrites
    // the request to its decrypted logical route before Spring MVC handles it.
    registration.setOrder(-300);
    return registration;
  }

}
