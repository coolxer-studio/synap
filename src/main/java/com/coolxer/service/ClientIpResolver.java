package com.coolxer.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Resolves the address seen by a client without trusting caller-controlled
 * forwarding headers unless the immediate peer is an explicitly trusted proxy.
 */
@Component
public class ClientIpResolver {

    private final List<CidrBlock> trustedProxies;

    @Autowired
    public ClientIpResolver(@Value("${synap.network.trusted-proxies:}") String trustedProxyCidrs) {
        ArrayList<CidrBlock> blocks = new ArrayList<>();
        if (trustedProxyCidrs != null) {
            for (String value : trustedProxyCidrs.split(",")) {
                CidrBlock block = CidrBlock.parse(value.trim());
                if (block != null) blocks.add(block);
            }
        }
        this.trustedProxies = Collections.unmodifiableList(blocks);
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = normalizeIp(request == null ? null : request.getRemoteAddr());
        if (remoteAddress == null) return "unavailable";
        if (!isTrusted(remoteAddress)) return remoteAddress;

        List<String> chain = forwardedChain(request);
        String current = remoteAddress;
        for (int index = chain.size() - 1; index >= 0; index--) {
            if (!isTrusted(current)) break;
            String candidate = normalizeIp(chain.get(index));
            if (candidate != null) current = candidate;
        }
        return current;
    }

    private List<String> forwardedChain(HttpServletRequest request) {
        if (request == null) return Collections.emptyList();
        String forwarded = request.getHeader("Forwarded");
        ArrayList<String> chain = new ArrayList<>();
        if (forwarded != null && !forwarded.isBlank()) {
            for (String hop : forwarded.split(",")) {
                for (String attribute : hop.split(";")) {
                    String[] pair = attribute.trim().split("=", 2);
                    if (pair.length == 2 && "for".equalsIgnoreCase(pair[0].trim())) {
                        chain.add(pair[1].trim());
                        break;
                    }
                }
            }
            if (!chain.isEmpty()) return chain;
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            for (String hop : xForwardedFor.split(",")) chain.add(hop.trim());
            return chain;
        }
        String realIp = request.getHeader("X-Real-IP");
        return realIp == null || realIp.isBlank() ? Collections.emptyList() : List.of(realIp.trim());
    }

    private boolean isTrusted(String address) {
        byte[] bytes = addressBytes(address);
        if (bytes == null) return false;
        for (CidrBlock block : trustedProxies) {
            if (block.contains(bytes)) return true;
        }
        return false;
    }

    static String normalizeIp(String value) {
        if (value == null) return null;
        String address = value.trim();
        if (address.length() >= 2 && address.startsWith("\"") && address.endsWith("\"")) {
            address = address.substring(1, address.length() - 1).trim();
        }
        if (address.isEmpty() || "unknown".equalsIgnoreCase(address) || address.startsWith("_")) return null;
        if (address.startsWith("[")) {
            int end = address.indexOf(']');
            if (end < 1) return null;
            address = address.substring(1, end);
        } else if (address.matches("^\\d{1,3}(?:\\.\\d{1,3}){3}:\\d+$")) {
            address = address.substring(0, address.lastIndexOf(':'));
        }
        int zoneIndex = address.indexOf('%');
        if (zoneIndex >= 0) address = address.substring(0, zoneIndex);
        byte[] parsed = addressBytes(address);
        if (parsed == null) return null;
        try {
            return InetAddress.getByAddress(parsed).getHostAddress().toLowerCase(Locale.ROOT);
        } catch (UnknownHostException ignored) {
            return null;
        }
    }

    private static byte[] addressBytes(String address) {
        if (address == null || address.isBlank()) return null;
        try {
            if (address.indexOf(':') >= 0) {
                InetAddress parsed = InetAddress.getByName(address);
                return parsed instanceof Inet6Address ? parsed.getAddress() : null;
            }
            String[] parts = address.split("\\.", -1);
            if (parts.length != 4) return null;
            byte[] bytes = new byte[4];
            for (int index = 0; index < parts.length; index++) {
                if (parts[index].isEmpty() || !parts[index].matches("\\d{1,3}")) return null;
                int octet = Integer.parseInt(parts[index]);
                if (octet < 0 || octet > 255) return null;
                bytes[index] = (byte) octet;
            }
            return bytes;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final class CidrBlock {
        private final byte[] network;
        private final int prefixLength;

        private CidrBlock(byte[] network, int prefixLength) {
            this.network = network;
            this.prefixLength = prefixLength;
        }

        static CidrBlock parse(String value) {
            if (value == null || value.isBlank()) return null;
            String[] parts = value.split("/", 2);
            String normalized = normalizeIp(parts[0]);
            byte[] network = addressBytes(normalized);
            if (network == null) return null;
            int maximum = network.length * 8;
            int prefix;
            try {
                prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : maximum;
            } catch (NumberFormatException ignored) {
                return null;
            }
            if (prefix < 0 || prefix > maximum) return null;
            return new CidrBlock(network, prefix);
        }

        boolean contains(byte[] address) {
            if (address == null || address.length != network.length) return false;
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int index = 0; index < fullBytes; index++) {
                if (address[index] != network[index]) return false;
            }
            if (remainingBits == 0) return true;
            int mask = 0xff << (8 - remainingBits);
            return (address[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
