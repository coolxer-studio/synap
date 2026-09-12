package com.coolxer.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class SecureRouteEnforcementFilterTest {
    private final SecureRouteEnforcementFilter filter = new SecureRouteEnforcementFilter();

    @Test
    void rejectsPlaintextCanonicalAndLegacyRoutes() throws Exception {
        for (String path : new String[]{
                "/attach/start", "/attach/upload", "/cross/device",
                "/1/start", "/risk/start", "/2/device", "/device_index/device"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean invoked = new AtomicBoolean();

            filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                    invoked.set(true));

            assertThat(response.getStatus()).as(path).isEqualTo(404);
            assertThat(invoked).as(path).isFalse();
        }
    }

    @Test
    void allowsOnlyAuthenticatedInternalDispatchAndUnprotectedAdminRoute() throws Exception {
        MockHttpServletRequest protectedRequest =
                new MockHttpServletRequest("POST", "/attach/start");
        protectedRequest.setAttribute(
                "sc.transportTrust", "sc1-authenticated/P256_HKDF_SHA256_AES256_GCM");
        AtomicBoolean protectedInvoked = new AtomicBoolean();
        filter.doFilter(protectedRequest, new MockHttpServletResponse(),
                (request, response) -> protectedInvoked.set(true));

        MockHttpServletRequest adminRequest = new MockHttpServletRequest(
                "POST", "/admin/sc/v1/enrollment-tokens");
        AtomicBoolean adminInvoked = new AtomicBoolean();
        filter.doFilter(adminRequest, new MockHttpServletResponse(),
                (request, response) -> adminInvoked.set(true));

        assertThat(protectedInvoked).isTrue();
        assertThat(adminInvoked).isTrue();
    }
}
