package com.coolxer.probe;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PublicIpResolverTest {
    @Test
    public void acceptsPublicIpv4AndIpv6Addresses() {
        assertTrue(PublicIpResolver.isPublicAddress("8.8.8.8"));
        assertTrue(PublicIpResolver.isPublicAddress("2606:4700:4700::1111"));
    }

    @Test
    public void rejectsLocalCarrierNatAndMalformedAddresses() {
        assertFalse(PublicIpResolver.isPublicAddress("192.168.0.103"));
        assertFalse(PublicIpResolver.isPublicAddress("100.64.1.8"));
        assertFalse(PublicIpResolver.isPublicAddress("127.0.0.1"));
        assertFalse(PublicIpResolver.isPublicAddress("not-an-ip"));
    }
}
