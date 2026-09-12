package com.coolxer.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientIpResolverTest {

    @Test
    void ignoresForwardingHeadersFromAnUntrustedPeer() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        HttpServletRequest request = request("198.51.100.7", "203.0.113.9, 10.1.1.1", null);

        assertEquals("198.51.100.7", resolver.resolve(request));
    }

    @Test
    void walksAForwardedChainFromTheTrustedEdge() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8,192.168.0.0/16");
        HttpServletRequest request = request("10.0.0.4", "203.0.113.9, 192.168.2.3", null);

        assertEquals("203.0.113.9", resolver.resolve(request));
    }

    @Test
    void supportsRfcForwardedAndIpv6Peers() {
        ClientIpResolver resolver = new ClientIpResolver("2001:db8:1::/48");
        HttpServletRequest request = request("2001:db8:1::5", null,
                "for=\"[2001:db8:2::8]:443\";proto=https");

        assertEquals("2001:db8:2:0:0:0:0:8", resolver.resolve(request));
    }

    @Test
    void rejectsMalformedForwardedAddresses() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1/32");
        HttpServletRequest request = request("127.0.0.1", "attacker.example, 999.1.1.1", null);

        assertEquals("127.0.0.1", resolver.resolve(request));
    }

    @Test
    void preservesOnlyValidClaimedPublicAddresses() {
        ClientIpResolver resolver = new ClientIpResolver("");

        assertEquals("8.8.8.8", resolver.resolveWanIp("8.8.8.8", "127.0.0.1"));
        assertEquals("127.0.0.1", resolver.resolveWanIp("192.168.1.8", "127.0.0.1"));
        assertEquals("127.0.0.1", resolver.resolveWanIp("100.64.1.8", "127.0.0.1"));
        assertEquals("127.0.0.1", resolver.resolveWanIp("invalid", "127.0.0.1"));
    }

    @Test
    void preservesPublicClaimsOnlyForIndependentAgentPlatforms() {
        ClientIpResolver resolver = new ClientIpResolver("");

        assertEquals("8.8.8.8", resolver.resolveWanIp("android", "8.8.8.8", "192.168.0.103"));
        assertEquals("8.8.8.8", resolver.resolveWanIp("host", "8.8.8.8", "192.168.0.103"));
        assertEquals("192.168.0.103", resolver.resolveWanIp("h5", "8.8.8.8", "192.168.0.103"));
        assertEquals("192.168.0.103",
                resolver.resolveWanIp("android", "192.168.0.8", "192.168.0.103"));
    }

    private HttpServletRequest request(String remote, String xForwardedFor, String forwarded) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(remote);
        when(request.getHeader("X-Forwarded-For")).thenReturn(xForwardedFor);
        when(request.getHeader("Forwarded")).thenReturn(forwarded);
        return request;
    }
}
