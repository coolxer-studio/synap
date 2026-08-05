package com.coolxer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 设置允许跨域，js探针需要跨域访问.
 */

@Configuration
public class CrossDomainConfigure implements WebMvcConfigurer {

  private final String[] allowedOrigins;

  public CrossDomainConfigure(@Value("${synap.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}") String allowedOrigins) {
    this.allowedOrigins = java.util.Arrays.stream(allowedOrigins.split(","))
        .map(String::trim).filter(value -> !value.isEmpty()).toArray(String[]::new);
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    if (allowedOrigins.length == 0) return;
    registry.addMapping("/**")
        .allowedOriginPatterns(allowedOrigins)
        .allowedMethods("POST", "OPTIONS")
        .allowCredentials(false)
        .maxAge(3600)
        .allowedHeaders("Content-Type", "code");
  }

}
