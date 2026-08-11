package com.coolxer.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class CrossDomainConfigureTest {
    @Test
    void preflightIsHandledBeforeTheEncryptedMessageFilter() throws Exception {
        FilterRegistrationBean<CorsFilter> registration =
                new CrossDomainConfigure("http://localhost:*")
                        .synapSecureCommunicationCorsFilter();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "OPTIONS", "/sc/v1/message");
        request.addHeader("Origin", "http://localhost:8888");
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "content-type");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();

        registration.getFilter().doFilter(request, response,
                (ignoredRequest, ignoredResponse) -> continued.set(true));

        assertThat(registration.getOrder()).isEqualTo(-300);
        assertThat(continued).isFalse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Access-Control-Allow-Origin"))
                .isEqualTo("http://localhost:8888");
    }

    @Test
    void actualEncryptedPostKeepsCorsHeadersAroundTheDownstreamResponse()
            throws Exception {
        CorsFilter filter = new CrossDomainConfigure("http://localhost:*")
                .synapSecureCommunicationCorsFilter().getFilter();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/sc/v1/message");
        request.addHeader("Origin", "http://localhost:8888");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(request, response,
                (ignoredRequest, ignoredResponse) -> continued.set(true));

        assertThat(continued).isTrue();
        assertThat(response.getHeader("Access-Control-Allow-Origin"))
                .isEqualTo("http://localhost:8888");
    }
}
